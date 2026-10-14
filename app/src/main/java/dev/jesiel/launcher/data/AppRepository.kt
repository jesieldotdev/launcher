package dev.jesiel.launcher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator

/** Carrega os apps instalados (incluindo perfis de trabalho) e acompanha instalações e remoções. */
class AppRepository(context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconSizePx = (context.resources.displayMetrics.density * 64).toInt()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private var loadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    private val appContext = context.applicationContext
    private var iconPack: IconPackLoader? = null
    private var iconPackName: String? = null
    private var styleUnthemed = true
    private var configured = false

    init {
        launcherApps.registerCallback(callback)
    }

    /**
     * Escolhe o pacote de ícones (nulo = do sistema) e recarrega os apps. A primeira chamada faz
     * o primeiro carregamento, para não montar os ícones duas vezes ao abrir o launcher.
     */
    fun setIconPack(packageName: String?, styleUnthemed: Boolean) {
        if (configured && packageName == iconPackName && styleUnthemed == this.styleUnthemed) return
        configured = true
        iconPackName = packageName
        this.styleUnthemed = styleUnthemed
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            iconPack = packageName?.let { IconPackLoader.load(appContext, it) }
            _apps.value = loadApps()
        }
    }

    /** Pacote em uso e quantos apps ele cobre (0 se não carregou). */
    fun iconPackSize(): Int = iconPack?.size ?: 0

    fun reload() {
        if (!configured) return
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) { _apps.value = loadApps() }
    }

    private fun loadApps(): List<AppInfo> {
        val collator = Collator.getInstance()
        return userManager.userProfiles
            .flatMap { user -> launcherApps.getActivityList(null, user).map { it.toAppInfo(user) } }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** Ícone do pacote escolhido (com o selo de trabalho, se for o caso) ou o do sistema. */
    private fun LauncherActivityInfo.iconBitmap(user: UserHandle): android.graphics.Bitmap {
        val packed = iconPack?.iconFor(componentName, { getIcon(0) }, iconSizePx, styleUnthemed)
        if (packed != null) {
            if (user == android.os.Process.myUserHandle()) return packed
            val pm = appContext.packageManager
            return pm.getUserBadgedIcon(android.graphics.drawable.BitmapDrawable(appContext.resources, packed), user).toBitmap(iconSizePx, iconSizePx)
        }
        return getBadgedIcon(0).toBitmap(iconSizePx, iconSizePx)
    }

    fun keyFor(info: LauncherActivityInfo, user: UserHandle): String =
        "${info.componentName.flattenToShortString()}#${userManager.getSerialNumberForUser(user)}"

    private fun LauncherActivityInfo.toAppInfo(user: UserHandle) = AppInfo(
        key = keyFor(this, user),
        label = label.toString(),
        component = componentName,
        user = user,
        icon = iconBitmap(user).asImageBitmap(),
        category = categoryOf(applicationInfo, componentName.packageName),
    )

    private fun categoryOf(info: ApplicationInfo, pkg: String): AppCategory {
        when (info.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.COMMUNICATION
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.MEDIA
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
        }
        val p = pkg.lowercase()
        return when {
            COMMUNICATION_HINTS.any { it in p } -> AppCategory.COMMUNICATION
            MEDIA_HINTS.any { it in p } -> AppCategory.MEDIA
            PRODUCTIVITY_HINTS.any { it in p } -> AppCategory.PRODUCTIVITY
            else -> AppCategory.TOOLS
        }
    }

    private companion object {
        // Muitos apps não declaram categoria; estes trechos de pacote cobrem os casos comuns.
        val COMMUNICATION_HINTS = listOf("whatsapp", "telegram", "messag", "mms", "dialer", "phone", "contacts", "mail", "gm", "instagram", "facebook", "discord", "signal", "skype", "teams", "slack", "twitter")
        val MEDIA_HINTS = listOf("music", "spotify", "video", "youtube", "camera", "gallery", "photo", "player", "netflix", "deezer", "podcast", "recorder", "soundrecorder")
        val PRODUCTIVITY_HINTS = listOf("docs", "sheets", "slides", "office", "calendar", "notes", "keep", "drive", "calculator", "word", "excel", "notion", "todo", "tasks")
    }

    fun close() {
        launcherApps.unregisterCallback(callback)
    }
}
