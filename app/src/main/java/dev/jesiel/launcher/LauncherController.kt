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
    var controlCenterOpen by mutableStateOf(false)
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
            // Animação de abertura que cresce a partir do ícone tocado.
            val options = bounds?.let {
                android.app.ActivityOptions.makeScaleUpAnimation(activity.window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
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

    private companion object {
        const val REQUEST_BIND_WIDGET = 11
        const val REQUEST_CONFIGURE_WIDGET = 12
        const val REQUEST_ROLE = 13
    }
}
