package dev.jesiel.launcher

import androidx.compose.ui.graphics.asAndroidBitmap
import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jesiel.launcher.data.MediaWatcher
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.SwipeAction
import dev.jesiel.launcher.widgets.WidgetHost

/**
 * Ponte entre as telas dos temas e o Android: abrir apps, pedir permissões de widget,
 * mudar papel de parede etc. Também guarda o estado das sobreposições comuns a todos os temas.
 */
class LauncherController(
    private val activity: Activity,
    val viewModel: LauncherViewModel,
    val widgets: WidgetHost,
) {
    var settingsOpen by mutableStateOf(false)
    var widgetPickerOpen by mutableStateOf(false)
    private var controlCenterState by mutableStateOf(false)

    /** Com o painel desativado, abrir o painel abre as notificações do sistema. */
    var controlCenterOpen: Boolean
        get() = controlCenterState
        set(value) {
            if (value && !viewModel.state.value.controlCenter) {
                controlCenterState = false
                expandNotifications()
            } else {
                controlCenterState = value
            }
        }
    /** Modo de organização da área de trabalho (arrastar, redimensionar, remover). */
    var editMode by mutableStateOf(false)

    /** Incrementado quando um item começa a ser arrastado na área de trabalho (fecha menus abertos). */
    var desktopDragTick by mutableIntStateOf(0)

    /** Incrementado quando o botão Home é pressionado com o launcher já visível. */
    var homePresses by mutableIntStateOf(0)
        private set

    private val launcherApps = activity.getSystemService(LauncherApps::class.java)
    private var pendingWidgetId = -1

    fun onHomePressed() {
        homePresses++
        settingsOpen = false
        widgetPickerOpen = false
        controlCenterOpen = false
        editMode = false
        articleUrl = null
        htmlLibraryOpen = false
        htmlDrawerOpen = false
    }

    /** Executa o gesto de deslizar para baixo configurado; [openSearch] é a busca do tema. */
    fun onSwipeDown(action: SwipeAction, openSearch: () -> Unit) {
        when (action) {
            SwipeAction.CONTROL_CENTER -> controlCenterOpen = true
            SwipeAction.NOTIFICATIONS -> expandNotifications()
            SwipeAction.SEARCH -> openSearch()
            SwipeAction.NONE -> Unit
        }
    }

    /** Abre a cortina de notificações do sistema (API oculta, mas estável há muitas versões). */
    @SuppressLint("WrongConstant")
    fun expandNotifications() {
        val ok = runCatching {
            val service = activity.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }.isSuccess
        if (!ok) toast("Não foi possível abrir as notificações")
    }

    fun launch(app: AppInfo, bounds: Rect? = null) {
        try {
            val options = if (viewModel.state.value.windowMode && freeformSupported()) {
                windowOptions()
            } else {
                // Animação de abertura que cresce a partir do ícone tocado.
                bounds?.let {
                    android.app.ActivityOptions.makeScaleUpAnimation(activity.window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
                }
            }
            launcherApps.startMainActivity(app.component, app.user, bounds, options)
            viewModel.onAppLaunched(app)
        } catch (e: Exception) {
            toast("Não foi possível abrir ${app.label}")
        }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppInfo) {
        start(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
    }

    fun webSearch(query: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (!start(search, silent = true)) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
        }
    }

    /** Matéria aberta no leitor interno do launcher (sem sair para outro app). */
    var articleUrl by mutableStateOf<String?>(null)

    fun openArticle(url: String) {
        articleUrl = url
    }

    /** Procura na Play Store (como o HyperOS faz quando a busca não acha o app). */
    fun searchPlayStore(query: String) {
        val q = Uri.encode(query.trim())
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps"))
        if (!start(market, silent = true)) start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps")))
    }

    fun openUrl(url: String) {
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Abre o app do Google (onde fica o Discover) ou, sem ele, o Google Notícias no navegador. */
    fun openGoogleApp(): Boolean {
        val intent = activity.packageManager.getLaunchIntentForPackage(GOOGLE_APP)
        return if (intent != null) start(intent) else start(Intent(Intent.ACTION_VIEW, Uri.parse("https://news.google.com")))
    }

    fun openWallpaperPicker() {
        start(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Escolher papel de parede"))
    }

    fun openSystemSettings() {
        start(Intent(Settings.ACTION_SETTINGS))
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = activity.packageManager.resolveActivity(intent, 0)
        return resolved?.activityInfo?.packageName == activity.packageName
    }

    fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = activity.getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_ROLE)
                return
            }
        }
        start(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    // --- Papel de parede --------------------------------------------------------------------

    private val wallpaperManager = WallpaperManager.getInstance(activity)

    /** Nome do papel de parede animado do sistema, se houver. */
    fun liveWallpaperName(): String? = runCatching {
        wallpaperManager.wallpaperInfo?.loadLabel(activity.packageManager)?.toString()
    }.getOrNull()

    /**
     * Repassa um toque no fundo da área de trabalho ao papel de parede animado, como fazem os
     * launchers do sistema. É o que permite interagir com papéis de parede que reagem ao toque.
     */
    fun sendWallpaperTap(x: Float, y: Float) {
        val token = activity.window.decorView.windowToken ?: return
        runCatching { wallpaperManager.sendWallpaperCommand(token, WallpaperManager.COMMAND_TAP, x.toInt(), y.toInt(), 0, null) }
    }

    /** Informa ao papel de parede a posição entre as telas, para o efeito de rolagem. */
    fun setWallpaperScroll(position: Float, pages: Int) {
        val token = activity.window.decorView.windowToken ?: return
        runCatching {
            val step = if (pages > 1) 1f / (pages - 1) else 0f
            wallpaperManager.setWallpaperOffsetSteps(step, 0f)
            wallpaperManager.setWallpaperOffsets(token, if (pages > 1) (position / (pages - 1)).coerceIn(0f, 1f) else 0.5f, 0.5f)
        }
    }

    /** Desfoca o papel de parede do sistema atrás do launcher (Android 12+), para painéis translúcidos. */
    fun setWindowBlur(radius: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val window = activity.window
            if (radius > 0) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radius }
        }
    }

    // --- Modo janela -----------------------------------------------------------------------

    /** Área ocupada pela dock / barra de tarefas na tela (atualizada pelos temas). */
    var dockBounds: Rect? = null

    /** O sistema tem o modo de janelas livres ("freeform") disponível? */
    fun freeformSupported(): Boolean =
        activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            runCatching { Settings.Global.getInt(activity.contentResolver, FREEFORM_SUPPORT) == 1 }.getOrDefault(false)

    /** Liga as opções de desenvolvedor do modo janela (precisa de WRITE_SECURE_SETTINGS; reiniciar depois). */
    fun enableFreeform(): Boolean = runCatching {
        Settings.Global.putInt(activity.contentResolver, FREEFORM_SUPPORT, 1)
        Settings.Global.putInt(activity.contentResolver, FORCE_RESIZABLE, 1)
    }.isSuccess

    /** Tela inteira menos a barra de status e a dock: onde a janela do app vai ficar. */
    fun windowArea(): Rect {
        val screen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(activity.windowManager.currentWindowMetrics.bounds)
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
        val bars = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        val area = Rect(screen.left, screen.top + (bars?.top ?: 0), screen.right, screen.bottom - (bars?.bottom ?: 0))
        val gap = (4 * activity.resources.displayMetrics.density).toInt()
        dockBounds?.let { dock ->
            when {
                dock.top > screen.height() / 2 -> area.bottom = minOf(area.bottom, dock.top - gap)
                dock.right < screen.width() / 2 -> area.left = maxOf(area.left, dock.right + gap)
                dock.left > screen.width() / 2 -> area.right = minOf(area.right, dock.left - gap)
                dock.bottom < screen.height() / 2 -> area.top = maxOf(area.top, dock.bottom + gap)
            }
        }
        return area
    }

    /**
     * Opções para abrir o app numa janela livre do tamanho de [windowArea]. A API pública só
     * define o tamanho; o modo "janela" vem de uma API interna, tentada se estiver acessível.
     */
    private fun windowOptions(): android.os.Bundle {
        val options = android.app.ActivityOptions.makeBasic().setLaunchBounds(windowArea())
        runCatching {
            android.app.ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        }
        return options.toBundle()
    }

    // --- Gestos do HyperOS ----------------------------------------------------------------

    /** O usuário concedeu WRITE_SECURE_SETTINGS via ADB (pm grant)? */
    fun canWriteSecureSettings(): Boolean =
        activity.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hyperOsGesturesForced(): Boolean =
        runCatching { Settings.Global.getInt(activity.contentResolver, FORCE_FSG) == 1 }.getOrDefault(false)

    /** Libera os gestos de tela cheia do HyperOS/MIUI com este launcher como padrão. */
    fun setHyperOsGestures(on: Boolean): Boolean = runCatching {
        Settings.Global.putInt(activity.contentResolver, FORCE_FSG, if (on) 1 else 0)
    }.getOrDefault(false)

    fun openUsageAccessSettings() = start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    // --- Widgets HTML -----------------------------------------------------------------------

    /** Paleta do tema em uso (para telas fora do tema, como o editor de widgets). */
    var palette: dev.jesiel.launcher.ui.common.LauncherPalette? = null

    /** Widget HTML aberto no editor (novo ou existente); nulo quando o editor está fechado. */
    var htmlEditing by mutableStateOf<dev.jesiel.launcher.data.HtmlWidget?>(null)

    /** Estúdio HTML: todos os widgets, modelos e onde cada um é usado. */
    var htmlLibraryOpen by mutableStateOf(false)

    /** Onde o widget em edição vai ser usado ao salvar (área de trabalho, bloqueio, lista de apps). */
    var htmlEditingRole by mutableStateOf(dev.jesiel.launcher.data.HtmlRole.DESKTOP)

    /** Widget aberto no testador (rascunho do editor ou salvo) e em que formato testar. */
    var htmlTesting by mutableStateOf<dev.jesiel.launcher.data.HtmlWidget?>(null)
    var htmlTestingRole by mutableStateOf(dev.jesiel.launcher.data.HtmlRole.DESKTOP)

    fun testHtml(widget: dev.jesiel.launcher.data.HtmlWidget, role: dev.jesiel.launcher.data.HtmlRole = widget.kind) {
        htmlTestingRole = role
        htmlTesting = widget
    }

    // --- Arrastar da lista de apps para a tela inicial ---------------------------------------

    var homeDrag by mutableStateOf<dev.jesiel.launcher.ui.common.HomeDrag?>(null)
        private set

    /** Definido pela área de trabalho: coloca o app na célula sob o ponto (na janela). */
    var desktopDrop: ((dev.jesiel.launcher.data.AppInfo, androidx.compose.ui.geometry.Offset) -> Unit)? = null

    fun startHomeDrag(app: dev.jesiel.launcher.data.AppInfo, position: androidx.compose.ui.geometry.Offset) {
        desktopDragTick++ // fecha o menu que o toque longo abriu
        homeDrag = dev.jesiel.launcher.ui.common.HomeDrag(app, position)
        // Fecha listas e painéis (os temas fecham a gaveta com o mesmo sinal do botão Home).
        homePresses++
        htmlDrawerOpen = false
        controlCenterOpen = false
        editMode = true
    }

    fun moveHomeDrag(position: androidx.compose.ui.geometry.Offset) {
        homeDrag = homeDrag?.copy(position = position)
    }

    fun dropHomeDrag(position: androidx.compose.ui.geometry.Offset) {
        val drag = homeDrag ?: return
        homeDrag = null
        val drop = desktopDrop
        if (drop != null) drop(drag.app, position) else viewModel.addToHome(drag.app)
    }

    /** Lista de apps em HTML aberta (no lugar da gaveta do tema). */
    var htmlDrawerOpen by mutableStateOf(false)
    var htmlDrawerSearch by mutableStateOf(false)

    /**
     * Chamado pelos temas antes de abrir a gaveta: se o usuário escolheu uma lista de apps em
     * HTML, abre ela e devolve verdadeiro (o tema não abre a sua).
     */
    fun openHtmlDrawer(search: Boolean): Boolean {
        if (viewModel.state.value.appsHtml == null) return false
        controlCenterOpen = false
        htmlDrawerSearch = search
        htmlDrawerOpen = true
        return true
    }

    /** Ícones em PNG para os widgets (launcher-icon://pacote), guardados depois do primeiro pedido. */
    private val iconCache = object : android.util.LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }
    fun appIconPng(packageName: String): ByteArray? {
        val app = viewModel.state.value.apps.firstOrNull { it.packageName == packageName } ?: return null
        // A chave muda quando o ícone muda (ex.: troca do pacote de ícones).
        val key = packageName + "@" + System.identityHashCode(app.icon)
        iconCache.get(key)?.let { return it }
        val bytes = runCatching {
            val out = java.io.ByteArrayOutputStream()
            app.icon.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }.getOrNull() ?: return null
        iconCache.put(key, bytes)
        return bytes
    }

    /** Definidos pela área de trabalho do tema, para os links launcher://apps e launcher://busca. */
    var openAppsMenu: (() -> Unit)? = null
    var openSearch: (() -> Unit)? = null

    /** Definido pela Activity: escolher um arquivo .html para o editor. */
    var importHtmlFile: (() -> Unit)? = null

    /** Abre o editor com um widget novo, a partir de [template] (ou do primeiro modelo). */
    fun newHtmlWidget(template: dev.jesiel.launcher.data.HtmlTemplate? = null, role: dev.jesiel.launcher.data.HtmlRole? = null) {
        val t = template ?: dev.jesiel.launcher.data.HtmlTemplates.all.first { it.role == dev.jesiel.launcher.data.HtmlRole.DESKTOP }
        val r = role ?: t.role
        htmlEditingRole = r
        htmlEditing = dev.jesiel.launcher.data.HtmlWidget(
            id = java.util.UUID.randomUUID().toString().take(8),
            name = if (template != null) t.name else "Widget HTML",
            html = t.html,
            interactive = t.html.contains("<script"),
            network = t.html.contains("fetch(") || t.html.contains("https://fonts."),
            onDesktop = r == dev.jesiel.launcher.data.HtmlRole.DESKTOP,
            role = r,
        )
    }

    /** Abre um widget salvo no editor. */
    fun editHtmlWidget(widget: dev.jesiel.launcher.data.HtmlWidget) {
        htmlEditingRole = widget.kind
        htmlEditing = widget
    }

    /** Onde o widget é usado hoje (para a prévia do editor ter o formato certo). */
    fun roleOf(widget: dev.jesiel.launcher.data.HtmlWidget): dev.jesiel.launcher.data.HtmlRole = when {
        viewModel.state.value.appsHtml?.id == widget.id -> dev.jesiel.launcher.data.HtmlRole.APPS
        viewModel.state.value.dockHtml?.id == widget.id -> dev.jesiel.launcher.data.HtmlRole.DOCK
        dev.jesiel.launcher.data.LockScreen.settings(activity).value?.let { it.style == dev.jesiel.launcher.data.LockStyle.HTML && it.htmlWidgetId == widget.id } == true ->
            dev.jesiel.launcher.data.HtmlRole.LOCK
        else -> dev.jesiel.launcher.data.HtmlRole.DESKTOP
    }

    /** Usa o widget na tela de bloqueio (e liga a tela de bloqueio do Launcher). */
    fun useOnLockScreen(widget: dev.jesiel.launcher.data.HtmlWidget) {
        if (widget.kind != dev.jesiel.launcher.data.HtmlRole.LOCK) return
        dev.jesiel.launcher.data.LockScreen.update(activity) { it.copy(enabled = true, style = dev.jesiel.launcher.data.LockStyle.HTML, htmlWidgetId = widget.id) }
        toast("\"${widget.name}\" na tela de bloqueio")
    }

    fun useAsDock(widget: dev.jesiel.launcher.data.HtmlWidget) {
        if (widget.kind != dev.jesiel.launcher.data.HtmlRole.DOCK) return
        viewModel.setDockHtml(widget.id)
        toast("\"${widget.name}\" como dock")
    }

    /** Aplica o widget no uso escolhido (ao salvar no editor ou pelo Estúdio). */
    fun useHtml(widget: dev.jesiel.launcher.data.HtmlWidget, role: dev.jesiel.launcher.data.HtmlRole) {
        // Cada widget só no lugar do próprio tipo.
        if (widget.kind != role) {
            toast("\"${widget.name}\" é um widget de ${widget.kind.label.lowercase()}")
            return
        }
        when (role) {
        dev.jesiel.launcher.data.HtmlRole.LOCK -> useOnLockScreen(widget)
        dev.jesiel.launcher.data.HtmlRole.APPS -> useAsAppList(widget)
        dev.jesiel.launcher.data.HtmlRole.DOCK -> useAsDock(widget)
        dev.jesiel.launcher.data.HtmlRole.DESKTOP -> viewModel.setHtmlWidgetOnDesktop(widget.id, true)
        }
    }

    fun useAsAppList(widget: dev.jesiel.launcher.data.HtmlWidget) {
        if (widget.kind != dev.jesiel.launcher.data.HtmlRole.APPS) return
        viewModel.setAppsHtml(widget.id)
        toast("\"${widget.name}\" como lista de apps")
    }

    fun launchPackage(packageName: String) {
        htmlDrawerOpen = false
        val app = viewModel.state.value.apps.firstOrNull { it.packageName == packageName }
        if (app != null) launch(app)
        else activity.packageManager.getLaunchIntentForPackage(packageName)?.let { start(it) } ?: toast("App não encontrado: $packageName")
    }

    /** Trata os links dos widgets HTML. Devolve verdadeiro se o link foi consumido. */
    /** Na tela de bloqueio: decide antes (ex.: desbloquear antes de abrir um app). Verdadeiro = tratado. */
    var linkInterceptor: ((String) -> Boolean)? = null

    fun handleWidgetLink(url: String): Boolean {
        if (linkInterceptor?.invoke(url) == true) return true
        val uri = Uri.parse(url)
        when (uri.scheme) {
            "launcher" -> when (uri.host) {
                // Nomes em português e em inglês.
                "app" -> uri.lastPathSegment?.let(::launchPackage)
                // Na lista de apps em HTML: fechar, informações e desinstalar.
                "fechar", "close" -> htmlDrawerOpen = false
                "estudio", "studio" -> htmlLibraryOpen = true
                "info" -> uri.lastPathSegment?.let { pkg -> viewModel.state.value.apps.firstOrNull { it.packageName == pkg } }?.let(::openAppInfo)
                "desinstalar", "uninstall" -> uri.lastPathSegment?.let { pkg -> viewModel.state.value.apps.firstOrNull { it.packageName == pkg } }?.let(::uninstall)
                "painel", "panel" -> controlCenterOpen = true
                "apps" -> openAppsMenu?.invoke()
                "busca", "search" -> openSearch?.invoke()
                "ajustes", "settings" -> settingsOpen = true
                "notificacoes", "notifications" -> expandNotifications()
                // launcher://musica/alternar | tocar | pausar | proxima | anterior; sem ação abre o app que toca.
                "musica", "music" -> {
                    val action = uri.lastPathSegment
                    when {
                        !MediaWatcher.supported(activity) -> toast("Controle de música indisponível na versão leve")
                        !MediaWatcher.hasAccess(activity) -> {
                            toast("Permita o acesso a notificações para controlar a música")
                            start(MediaWatcher.accessIntent(), silent = true)
                        }
                        action == null -> MediaWatcher.playingPackage?.let(::launchPackage)
                        !MediaWatcher.command(action) -> toast("Nada tocando agora")
                    }
                }
                "url" -> uri.getQueryParameter("u")?.let(::openUrl)
            }
            "http", "https" -> openUrl(url)
            else -> start(Intent(Intent.ACTION_VIEW, uri), silent = true)
        }
        return true
    }

    // --- Backup ---------------------------------------------------------------------------

    /** Definidos pela Activity: escolher onde salvar / qual arquivo importar. */
    var exportBackupFile: (() -> Unit)? = null
    var importBackupFile: (() -> Unit)? = null

    fun afterRestore() {
        viewModel.pruneWidgets { widgets.info(it) != null }
        toast("Organização restaurada")
    }

    /** Definido pela Activity: abre o seletor de imagens para o ícone do menu. */
    var pickImage: (() -> Unit)? = null

    // --- Widgets -------------------------------------------------------------------------------

    fun addWidget(provider: AppWidgetProviderInfo) {
        widgetPickerOpen = false
        val id = widgets.allocateId()
        if (widgets.bindIfAllowed(id, provider)) {
            configureOrAdd(id)
        } else {
            pendingWidgetId = id
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            activity.startActivityForResult(bind, REQUEST_BIND_WIDGET)
        }
    }

    fun removeWidget(id: Int) {
        widgets.delete(id)
        viewModel.removeWidget(id)
    }

    private fun configureOrAdd(id: Int) {
        val info = widgets.info(id)
        if (info?.configure != null) {
            pendingWidgetId = id
            try {
                widgets.host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE_WIDGET, null)
                return
            } catch (e: Exception) {
                // Alguns provedores não exportam a tela de configuração; adiciona mesmo assim.
            }
        }
        viewModel.addWidget(id)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        val id = pendingWidgetId
        if (id == -1) return
        when (requestCode) {
            REQUEST_BIND_WIDGET -> {
                pendingWidgetId = -1
                if (resultCode == Activity.RESULT_OK) configureOrAdd(id) else widgets.delete(id)
            }
            REQUEST_CONFIGURE_WIDGET -> {
                pendingWidgetId = -1
                if (resultCode == Activity.RESULT_OK) viewModel.addWidget(id) else widgets.delete(id)
            }
        }
    }

    // -------------------------------------------------------------------------------------------

    fun start(intent: Intent, silent: Boolean = false): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        if (!silent) toast("Nenhum app disponível para esta ação")
        false
    } catch (e: SecurityException) {
        if (!silent) toast("Ação não permitida")
        false
    }

    fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    /** Abre a tela de configuração de um widget já adicionado, quando ele oferece uma. */
    fun reconfigureWidget(id: Int) {
        runCatching { widgets.host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_RECONFIGURE_WIDGET, null) }
            .onFailure { toast("Este widget não pode ser configurado") }
    }

    private companion object {
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        const val FORCE_FSG = "force_fsg_nav_bar"
        const val FREEFORM_SUPPORT = "enable_freeform_support"
        const val FORCE_RESIZABLE = "force_resizable_activities"
        const val WINDOWING_MODE_FREEFORM = 5
        const val REQUEST_RECONFIGURE_WIDGET = 14
        const val REQUEST_BIND_WIDGET = 11
        const val REQUEST_CONFIGURE_WIDGET = 12
        const val REQUEST_ROLE = 13
    }
}
