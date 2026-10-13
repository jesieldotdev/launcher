package dev.jesiel.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.AppRepository
import dev.jesiel.launcher.data.FolderData
import dev.jesiel.launcher.data.HomeItem
import dev.jesiel.launcher.data.LauncherData
import dev.jesiel.launcher.data.LauncherPrefs
import dev.jesiel.launcher.data.RecentApp
import dev.jesiel.launcher.data.ThemeLayout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

data class LauncherUiState(
    val loaded: Boolean = false,
    val themeId: String = "windows",
    val layout: ThemeLayout = ThemeLayout(),
    val apps: List<AppInfo> = emptyList(),
    val home: List<HomeItem> = emptyList(),
    val dock: List<AppInfo> = emptyList(),
    val recents: List<RecentApp> = emptyList(),
    val widgetIds: List<Int> = emptyList(),
    val folders: List<FolderData> = emptyList(),
    /** Apps presentes na tela inicial, soltos ou dentro de pastas. */
    val favoriteKeys: Set<String> = emptySet(),
    val dockKeys: Set<String> = emptySet(),
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = LauncherPrefs(application)
    private val repository = AppRepository(application, viewModelScope)

    val state: StateFlow<LauncherUiState> = combine(repository.apps, prefs.data) { apps, data ->
        if (apps.isNotEmpty() && !data.initialized) seedDefaults(apps)
        buildState(apps, data)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())

    private fun buildState(apps: List<AppInfo>, data: LauncherData): LauncherUiState {
        val byKey = apps.associateBy { it.key }
        val home = data.homeOrder.mapNotNull { entry ->
            if (entry.startsWith(FOLDER_PREFIX)) {
                data.folders[entry.removePrefix(FOLDER_PREFIX)]?.let { f ->
                    HomeItem.Folder(f.id, f.name, f.appKeys.mapNotNull(byKey::get))
                }
            } else {
                byKey[entry]?.let(HomeItem::App)
            }
        }
        return LauncherUiState(
            loaded = apps.isNotEmpty(),
            themeId = data.themeId,
            layout = data.layoutFor(data.themeId),
            apps = apps,
            home = home,
            dock = data.dock.mapNotNull(byKey::get),
            recents = data.recents.mapNotNull { (key, at) -> byKey[key]?.let { RecentApp(it, at) } },
            widgetIds = data.widgetIds,
            folders = data.folders.values.toList(),
            favoriteKeys = data.homeOrder.toSet() + data.folders.values.flatMap { it.appKeys },
            dockKeys = data.dock.toSet(),
        )
    }

    /** Na primeira execução, monta favoritos e dock com os apps padrão do aparelho. */
    private fun seedDefaults(apps: List<AppInfo>) {
        val pm = getApplication<Application>().packageManager
        fun packageFor(intent: Intent): String? =
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?.takeIf { it != "android" }

        val defaultPackages = listOfNotNull(
            packageFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            packageFor(Intent(Intent.ACTION_DIAL)),
            packageFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            packageFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)),
            packageFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)),
            "com.whatsapp", "com.spotify.music", "com.android.vending", "com.google.android.gm",
            "com.android.settings",
        ).distinct()

        val own = getApplication<Application>().packageName
        val candidates = apps.filter { it.packageName != own }
        val favorites = defaultPackages
            .mapNotNull { pkg -> candidates.firstOrNull { it.packageName == pkg } }
            .plus(candidates)
            .distinctBy { it.key }
            .take(8)

        prefs.update {
            it.copy(
                initialized = true,
                homeOrder = favorites.map(AppInfo::key),
                dock = favorites.take(4).map(AppInfo::key),
            )
        }
    }

    fun onAppLaunched(app: AppInfo) = prefs.update { d ->
        val now = System.currentTimeMillis()
        d.copy(recents = (listOf(app.key to now) + d.recents.filter { it.first != app.key }).take(MAX_RECENTS))
    }

    fun clearRecents() = prefs.update { it.copy(recents = emptyList()) }

    fun toggleFavorite(app: AppInfo) = prefs.update { d ->
        val inFolder = d.folders.values.firstOrNull { app.key in it.appKeys }
        when {
            inFolder != null -> d.removeFromFolder(app.key, inFolder.id, returnToHome = false)
            app.key in d.homeOrder -> d.copy(homeOrder = d.homeOrder - app.key)
            else -> d.copy(homeOrder = d.homeOrder + app.key)
        }
    }

    fun toggleDock(app: AppInfo) = prefs.update { d ->
        if (app.key in d.dock) d.copy(dock = d.dock - app.key)
        else d.copy(dock = (d.dock + app.key).takeLast(MAX_DOCK))
    }

    /** Move o app para uma pasta existente ou cria uma nova (quando [folderId] é nulo). */
    fun moveToFolder(app: AppInfo, folderId: String?, newFolderName: String = "Pasta") = prefs.update { d ->
        val current = d.folders.values.firstOrNull { app.key in it.appKeys }
        val base = if (current != null) d.removeFromFolder(app.key, current.id, returnToHome = false) else d
        if (folderId != null && base.folders[folderId] != null) {
            val folder = base.folders.getValue(folderId)
            base.copy(
                homeOrder = base.homeOrder - app.key,
                folders = base.folders + (folderId to folder.copy(appKeys = folder.appKeys + app.key)),
            )
        } else {
            val id = UUID.randomUUID().toString().take(8)
            val entry = FOLDER_PREFIX + id
            val index = base.homeOrder.indexOf(app.key)
            val order = if (index >= 0) {
                base.homeOrder.toMutableList().also { it[index] = entry }
            } else {
                base.homeOrder + entry
            }
            base.copy(homeOrder = order, folders = base.folders + (id to FolderData(id, newFolderName, listOf(app.key))))
        }
    }

    fun removeFromFolder(app: AppInfo, folderId: String) = prefs.update {
        it.removeFromFolder(app.key, folderId, returnToHome = true)
    }

    fun renameFolder(folderId: String, name: String) = prefs.update { d ->
        val folder = d.folders[folderId] ?: return@update d
        d.copy(folders = d.folders + (folderId to folder.copy(name = name.ifBlank { folder.name })))
    }

    /** Desfaz a pasta, devolvendo os apps para a tela inicial no lugar dela. */
    fun deleteFolder(folderId: String) = prefs.update { d ->
        val folder = d.folders[folderId] ?: return@update d
        val order = d.homeOrder.flatMap { if (it == FOLDER_PREFIX + folderId) folder.appKeys else listOf(it) }
        d.copy(homeOrder = order.distinct(), folders = d.folders - folderId)
    }

    fun moveFavorite(app: AppInfo, offset: Int) = prefs.update { d ->
        val index = d.homeOrder.indexOf(app.key)
        val target = index + offset
        if (index < 0 || target !in d.homeOrder.indices) return@update d
        d.copy(homeOrder = d.homeOrder.toMutableList().apply { add(target, removeAt(index)) })
    }

    fun setTheme(themeId: String) = prefs.update { it.copy(themeId = themeId) }

    fun updateLayout(transform: (ThemeLayout) -> ThemeLayout) = prefs.update { d ->
        d.copy(layouts = d.layouts + (d.themeId to transform(d.layoutFor(d.themeId))))
    }

    fun addWidget(id: Int) = prefs.update { it.copy(widgetIds = it.widgetIds + id) }

    fun removeWidget(id: Int) = prefs.update { it.copy(widgetIds = it.widgetIds - id) }

    private fun LauncherData.removeFromFolder(key: String, folderId: String, returnToHome: Boolean): LauncherData {
        val folder = folders[folderId] ?: return this
        val remaining = folder.appKeys - key
        val entry = FOLDER_PREFIX + folderId
        var order = homeOrder
        val newFolders = if (remaining.isEmpty()) {
            order = order - entry
            folders - folderId
        } else {
            folders + (folderId to folder.copy(appKeys = remaining))
        }
        if (returnToHome && key !in order) {
            val index = order.indexOf(entry)
            order = if (index >= 0) order.toMutableList().apply { add(index + 1, key) } else order + key
        }
        return copy(homeOrder = order, folders = newFolders)
    }

    override fun onCleared() {
        repository.close()
    }

    companion object {
        const val FOLDER_PREFIX = "folder:"
        const val MAX_RECENTS = 12
        const val MAX_DOCK = 5
    }
}
