package dev.jesiel.launcher.data

import android.content.Context
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

    init {
        launcherApps.registerCallback(callback)
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            val collator = Collator.getInstance()
            val list = userManager.userProfiles
                .flatMap { user -> launcherApps.getActivityList(null, user).map { it.toAppInfo(user) } }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
            _apps.value = list
        }
    }

    fun keyFor(info: LauncherActivityInfo, user: UserHandle): String =
        "${info.componentName.flattenToShortString()}#${userManager.getSerialNumberForUser(user)}"

    private fun LauncherActivityInfo.toAppInfo(user: UserHandle) = AppInfo(
        key = keyFor(this, user),
        label = label.toString(),
        component = componentName,
        user = user,
        icon = getBadgedIcon(0).toBitmap(iconSizePx, iconSizePx).asImageBitmap(),
    )

    fun close() {
        launcherApps.unregisterCallback(callback)
    }
}
