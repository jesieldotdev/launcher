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
import dev.jesiel.launcher.data.AppsView
import dev.jesiel.launcher.data.Backups
import dev.jesiel.launcher.data.MenuIcon
import dev.jesiel.launcher.data.ColorMode
import dev.jesiel.launcher.data.Placement
import dev.jesiel.launcher.data.SwipeAction
import dev.jesiel.launcher.data.FolderData
import dev.jesiel.launcher.data.HomeItem
import dev.jesiel.launcher.data.LauncherData
import dev.jesiel.launcher.data.HomeImporter
import dev.jesiel.launcher.data.ImportedItem
import dev.jesiel.launcher.data.LauncherPrefs
import dev.jesiel.launcher.data.LeftScreen
import dev.jesiel.launcher.data.NewsRepository
import dev.jesiel.launcher.data.NewsState
import dev.jesiel.launcher.data.NewsTopic
import dev.jesiel.launcher.data.RecentApp
import dev.jesiel.launcher.data.ThemeLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
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
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val swipeDown: SwipeAction = SwipeAction.CONTROL_CENTER,
    /** Posições salvas na área de trabalho do tema atual. */
    val placements: Map<String, Placement> = emptyMap(),
    val hiddenBuiltins: Set<String> = emptySet(),
    val transparency: Boolean = true,
    val appsView: AppsView = AppsView.GRID,
    /** Muda quando a imagem personalizada do ícone do menu é trocada. */
    val menuIconVersion: Long = 0,
    val leftScreen: LeftScreen = LeftScreen.FEED,
    val windowMode: Boolean = false,
    val sharedLayout: Boolean = true,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = LauncherPrefs(application)
    private val repository = AppRepository(application, viewModelScope)
    private val menuIconVersion = MutableStateFlow(0L)
    val backups = Backups(application, prefs)

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { backups.dailySnapshot() }
    }

    /** Remove widgets que não existem mais neste aparelho (ex.: depois de restaurar um backup). */
    fun pruneWidgets(exists: (Int) -> Boolean) = prefs.update { d ->
        val valid = d.widgetIds.filter(exists)
        if (valid.size == d.widgetIds.size) d else d.copy(widgetIds = valid)
    }

    val state: StateFlow<LauncherUiState> = combine(repository.apps, prefs.data, menuIconVersion) { apps, data, _ ->
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
            dock = data.dock.mapNotNull(byKey::get).take(data.layoutFor(data.themeId).dockMaxApps),
            recents = data.recents.mapNotNull { (key, at) -> byKey[key]?.let { RecentApp(it, at) } },
            widgetIds = data.widgetIds,
            folders = data.folders.values.toList(),
            favoriteKeys = data.homeOrder.toSet() + data.folders.values.flatMap { it.appKeys },
            dockKeys = data.dock.toSet(),
            colorMode = data.colorMode,
            swipeDown = data.swipeDown,
            placements = data.placementsFor(data.themeId),
            hiddenBuiltins = data.hiddenBuiltins[data.themeId].orEmpty(),
            transparency = data.transparency,
            appsView = data.appsView,
            menuIconVersion = menuIconVersion.value,
            leftScreen = data.leftScreen,
            windowMode = data.windowMode,
            sharedLayout = data.sharedLayout,
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
        else d.copy(dock = (d.dock + app.key).takeLast(d.layoutFor(d.themeId).dockMaxApps.coerceIn(1, MAX_DOCK)))
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
    fun deleteFolder(folderId: String) {
        backups.snapshot("Antes de desfazer uma pasta")
        applyDeleteFolder(folderId)
    }

    private fun applyDeleteFolder(folderId: String) = prefs.update { d ->
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

    fun setTheme(themeId: String) {
        if (themeId != prefs.data.value.themeId) backups.snapshot("Antes de trocar para o tema $themeId")
        applyTheme(themeId)
    }

    private fun applyTheme(themeId: String) = prefs.update { d ->
        if (!d.sharedLayout || themeId == d.themeId) return@update d.copy(themeId = themeId)
        // Com a organização compartilhada, grade e telas acompanham, para as posições baterem.
        val from = d.layoutFor(d.themeId)
        val to = d.layoutFor(themeId).copy(
            gridColumns = from.gridColumns,
            multiPage = from.multiPage,
            pageCount = from.pageCount,
            freePlacement = from.freePlacement,
        )
        d.copy(themeId = themeId, layouts = d.layouts + (themeId to to))
    }

    fun updateLayout(transform: (ThemeLayout) -> ThemeLayout) = prefs.update { d ->
        d.copy(layouts = d.layouts + (d.themeId to transform(d.layoutFor(d.themeId))))
    }

    private val newsRepository = NewsRepository()
    private val _news = MutableStateFlow<Map<NewsTopic, NewsState>>(emptyMap())
    val news: StateFlow<Map<NewsTopic, NewsState>> = _news

    /** Atualiza um assunto do feed; sem [force], só se tiver mais de 30 minutos. */
    fun refreshNews(topic: NewsTopic, force: Boolean = false) {
        val current = _news.value[topic]
        if (!force && current is NewsState.Loaded && System.currentTimeMillis() - current.fetchedAt < 30 * 60_000) return
        if (!force && current is NewsState.Loading) return
        if (current !is NewsState.Loaded) _news.value += topic to NewsState.Loading
        viewModelScope.launch {
            val next = runCatching { newsRepository.fetch(topic) }.fold(
                onSuccess = { NewsState.Loaded(it, System.currentTimeMillis()) },
                onFailure = { if (current is NewsState.Loaded) current else NewsState.Failed("Não foi possível carregar as notícias.") },
            )
            _news.value += topic to next
        }
    }

    private val importer = HomeImporter(application)

    fun hasUsageAccess() = importer.hasUsageAccess()

    /**
     * Importa a tela inicial do launcher do sistema (ou, se ele não permitir, os apps mais
     * usados) e substitui favoritos, pastas, dock e posições do tema atual.
     * Devolve uma descrição do resultado para mostrar ao usuário.
     */
    fun importHome(mostUsed: Boolean): String {
        backups.snapshot("Antes de importar a tela inicial")
        val apps = repository.apps.value
        val imported = (if (mostUsed) importer.importMostUsed(apps) else importer.importFromSystemLauncher(apps))
            ?: return if (mostUsed) "Permita o acesso ao uso de apps para importar os mais usados."
            else "O launcher do sistema não permite ler a tela inicial. Tente importar os apps mais usados."
        prefs.update { d ->
            val folders = LinkedHashMap<String, FolderData>()
            val order = mutableListOf<String>()
            val placements = LinkedHashMap<String, Placement>()
            val columns = d.layoutFor(d.themeId).gridColumns
            imported.home.forEach { item ->
                val id = when (item) {
                    is ImportedItem.App -> item.key.takeIf { it !in order }
                    is ImportedItem.Folder -> {
                        val folderId = UUID.randomUUID().toString().take(8)
                        folders[folderId] = FolderData(folderId, item.name, item.keys)
                        FOLDER_PREFIX + folderId
                    }
                } ?: return@forEach
                order += id
                if (item.x < columns) placements[id] = Placement(item.page, item.x.toFloat(), item.y.toFloat(), 1f, 1f)
            }
            val pages = (imported.home.maxOfOrNull { it.page } ?: 0) + 1
            val layout = d.layoutFor(d.themeId).let { if (pages > 1) it.copy(multiPage = true, pageCount = maxOf(it.pageCount, pages)) else it }
            d.copy(
                initialized = true,
                homeOrder = order,
                folders = folders,
                dock = imported.dock.take(MAX_DOCK).ifEmpty { d.dock },
                placements = d.placements + ((if (d.sharedLayout) LauncherData.SHARED_PLACEMENTS else d.themeId) to placements),
                layouts = d.layouts + (d.themeId to layout),
            )
        }
        val folderCount = imported.home.count { it is ImportedItem.Folder }
        return "Importado de ${imported.source}: ${imported.home.size - folderCount} apps, $folderCount pastas e ${imported.dock.size} no dock."
    }

    fun setWindowMode(on: Boolean) = prefs.update { it.copy(windowMode = on) }

    fun setLeftScreen(screen: LeftScreen) = prefs.update { it.copy(leftScreen = screen) }

    fun setTransparency(on: Boolean) = prefs.update { it.copy(transparency = on) }

    fun setAppsView(view: AppsView) = prefs.update { it.copy(appsView = view) }

    /** Arquivo com a imagem escolhida para o ícone do menu do tema [themeId]. */
    fun menuIconFile(themeId: String) = java.io.File(getApplication<Application>().filesDir, "menu_icon_$themeId.png")

    /** Copia a imagem escolhida (reduzida) e passa a usá-la como ícone do menu. */
    fun setCustomMenuIcon(uri: android.net.Uri) {
        val app = getApplication<Application>()
        val themeId = prefs.data.value.themeId
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching {
                val source = app.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
                val size = 192
                val side = minOf(source.width, source.height)
                val square = android.graphics.Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
                val scaled = android.graphics.Bitmap.createScaledBitmap(square, size, size, true)
                menuIconFile(themeId).outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
            if (ok) {
                updateLayout { it.copy(menuIcon = MenuIcon.CUSTOM) }
                menuIconVersion.value = System.currentTimeMillis()
            }
        }
    }

    fun setColorMode(mode: ColorMode) = prefs.update { it.copy(colorMode = mode) }

    fun setSwipeDown(action: SwipeAction) = prefs.update { it.copy(swipeDown = action) }

    /** Grava posições na área de trabalho do tema atual (mesclando com as existentes). */
    /**
     * Grava posições do tema [themeId]. Ignora temas que não estão ativos: durante a transição
     * entre temas, o que está saindo ainda aparece e não deve gravar nada.
     */
    fun savePlacements(themeId: String, changes: Map<String, Placement>) = prefs.update { d ->
        if (themeId != d.themeId) return@update d
        val current = d.placementsFor(themeId)
        if (changes.all { (id, p) -> current[id] == p }) return@update d
        if (!d.sharedLayout) {
            return@update d.copy(placements = d.placements + (themeId to d.placements[themeId].orEmpty() + changes))
        }
        val (own, shared) = changes.entries.partition { LauncherData.isBuiltin(it.key) }
        val themeMap = d.placements[themeId].orEmpty() + own.associate { it.key to it.value }
        val sharedMap = current.filterKeys { !LauncherData.isBuiltin(it) } + shared.associate { it.key to it.value }
        d.copy(placements = d.placements + (themeId to themeMap) + (LauncherData.SHARED_PLACEMENTS to sharedMap))
    }

    fun setSharedLayout(on: Boolean) {
        backups.snapshot("Antes de mudar a organização compartilhada")
        applySharedLayout(on)
    }

    private fun applySharedLayout(on: Boolean) = prefs.update { d ->
        // Ao ligar, a organização do tema atual passa a valer para todos.
        val sharedMap = d.placements[d.themeId].orEmpty().filterKeys { !LauncherData.isBuiltin(it) }
        if (on) d.copy(sharedLayout = true, placements = d.placements + (LauncherData.SHARED_PLACEMENTS to sharedMap))
        else d.copy(sharedLayout = false, placements = d.placements + (d.themeId to d.placementsFor(d.themeId)))
    }

    fun setBuiltinHidden(id: String, hidden: Boolean) = prefs.update { d ->
        val set = d.hiddenBuiltins[d.themeId].orEmpty()
        d.copy(hiddenBuiltins = d.hiddenBuiltins + (d.themeId to if (hidden) set + id else set - id))
    }

    fun addPage() = updateLayout { it.copy(pageCount = it.pageCount + 1) }

    fun removePage(page: Int) {
        backups.snapshot("Antes de remover uma tela")
        applyRemovePage(page)
    }

    private fun applyRemovePage(page: Int) = prefs.update { d ->
        val layout = d.layoutFor(d.themeId)
        if (layout.pageCount <= 1) return@update d
        // Itens das páginas seguintes sobem uma página; os da removida voltam a ser encaixados.
        fun shift(map: Map<String, Placement>) = map
            .filterValues { it.page != page }
            .mapValues { (_, p) -> if (p.page > page) p.copy(page = p.page - 1) else p }
        val keys = listOf(d.themeId) + if (d.sharedLayout) listOf(LauncherData.SHARED_PLACEMENTS) else emptyList()
        d.copy(
            placements = d.placements + keys.associateWith { shift(d.placements[it].orEmpty()) },
            layouts = d.layouts + (d.themeId to layout.copy(pageCount = layout.pageCount - 1)),
        )
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
        const val MAX_DOCK = 8
    }
}
