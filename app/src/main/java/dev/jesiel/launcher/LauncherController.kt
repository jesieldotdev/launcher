package dev.jesiel.launcher

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

    /** Biblioteca de widgets HTML salvos (adicionar, editar, duplicar, excluir). */
    var htmlLibraryOpen by mutableStateOf(false)

    /** Definidos pela área de trabalho do tema, para os links launcher://apps e launcher://busca. */
    var openAppsMenu: (() -> Unit)? = null
    var openSearch: (() -> Unit)? = null

    /** Definido pela Activity: escolher um arquivo .html para o editor. */
    var importHtmlFile: (() -> Unit)? = null

    fun newHtmlWidget() {
        htmlEditing = dev.jesiel.launcher.data.HtmlWidget(
            id = java.util.UUID.randomUUID().toString().take(8),
            name = "Widget HTML",
            html = dev.jesiel.launcher.data.HtmlTemplates.all.first().second,
        )
    }

    fun launchPackage(packageName: String) {
        val app = viewModel.state.value.apps.firstOrNull { it.packageName == packageName }
        if (app != null) launch(app)
        else activity.packageManager.getLaunchIntentForPackage(packageName)?.let { start(it) } ?: toast("App não encontrado: $packageName")
    }

    /** Trata os links dos widgets HTML. Devolve verdadeiro se o link foi consumido. */
    fun handleWidgetLink(url: String): Boolean {
        val uri = Uri.parse(url)
        when (uri.scheme) {
            "launcher" -> when (uri.host) {
                "app" -> uri.lastPathSegment?.let(::launchPackage)
                "painel" -> controlCenterOpen = true
                "apps" -> openAppsMenu?.invoke()
                "busca" -> openSearch?.invoke()
                "ajustes" -> settingsOpen = true
                "notificacoes" -> expandNotifications()
                // launcher://musica/alternar | tocar | pausar | proxima | anterior; sem ação abre o app que toca.
                "musica" -> {
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
