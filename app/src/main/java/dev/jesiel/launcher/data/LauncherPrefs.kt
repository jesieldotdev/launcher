package dev.jesiel.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** Ajustes visuais que cada tema guarda separadamente. */
data class ThemeLayout(
    val iconSizeDp: Int = 52,
    val gridColumns: Int = 4,
    val showLabels: Boolean = true,
    val useThemeWallpaper: Boolean = true,
    /** Posição livre (como a área de trabalho do Windows) em vez de encaixar na grade. */
    val freePlacement: Boolean = false,
    /** Várias telas, alternadas deslizando para o lado. */
    val multiPage: Boolean = false,
    val pageCount: Int = 1,
    /** Ícone do botão que abre o menu de apps (Iniciar, Launchpad…). */
    val menuIcon: MenuIcon = MenuIcon.DEFAULT,
    /** Barra de tarefas / dock / painel inferior visível. */
    val showDock: Boolean = true,
    /** Fundo (painel translúcido) atrás dos ícones da dock / barra de tarefas. */
    val dockBackground: Boolean = true,
    /** O menu de apps abre direto na lista completa (como no KDE) em vez dos fixados. */
    val menuOpensAllApps: Boolean = false,
    /** Barra superior (Ubuntu e macOS) visível. */
    val showTopBar: Boolean = true,
)

enum class MenuIcon(val label: String) {
    DEFAULT("Padrão do tema"),
    FLAT("Plano"),
    ORB("Orbe"),
    GRID("Grade de apps"),
    CUSTOM("Imagem da galeria"),
}

/** O que aparece ao deslizar para a direita a partir da primeira tela. */
enum class LeftScreen(val label: String) {
    NONE("Nenhuma"),
    FEED("Feed de notícias"),
    GOOGLE("Abrir app do Google (externo)"),
}

/** Como "Todos os apps" é exibido. */
enum class AppsView(val label: String) { CATEGORY("Categoria"), GRID("Grade"), LIST("Lista") }

data class FolderData(val id: String, val name: String, val appKeys: List<String>)

enum class ColorMode(val label: String) { SYSTEM("Seguir o sistema"), LIGHT("Claro"), DARK("Escuro") }

enum class SwipeAction(val label: String) {
    CONTROL_CENTER("Painel de controle"),
    NOTIFICATIONS("Notificações do sistema"),
    SEARCH("Busca de apps"),
    NONE("Nada"),
}

/**
 * Tudo o que o launcher persiste. Favoritos, pastas, dock e widgets são compartilhados entre
 * temas, para que o usuário não perca a organização ao alternar. Posições na área de trabalho
 * são guardadas por tema, porque cada tema organiza a tela de um jeito.
 */
data class LauncherData(
    val themeId: String,
    val initialized: Boolean,
    /** Ordem da tela inicial. Cada entrada é a chave de um app ou "folder:<id>". */
    val homeOrder: List<String>,
    val folders: Map<String, FolderData>,
    val dock: List<String>,
    /** Chave do app → momento da última abertura pelo launcher. */
    val recents: List<Pair<String, Long>>,
    val widgetIds: List<Int>,
    val layouts: Map<String, ThemeLayout>,
    val colorMode: ColorMode,
    val swipeDown: SwipeAction,
    /** Tema → id do item na área de trabalho → posição. */
    val placements: Map<String, Map<String, Placement>>,
    /** Tema → componentes próprios do tema que o usuário removeu da área de trabalho. */
    val hiddenBuiltins: Map<String, Set<String>>,
    /** Painéis translúcidos com o fundo desfocado, como os efeitos de transparência do Windows. */
    val transparency: Boolean,
    val appsView: AppsView,
    val leftScreen: LeftScreen,
) {
    fun layoutFor(themeId: String) = layouts[themeId] ?: defaultLayout(themeId)

    companion object {
        /** iOS e macOS começam com várias telas, como nos sistemas originais. */
        fun defaultLayout(themeId: String) = when (themeId) {
            "ios" -> ThemeLayout(iconSizeDp = 56, multiPage = true, pageCount = 2)
            "macos" -> ThemeLayout(multiPage = true, pageCount = 2)
            else -> ThemeLayout()
        }
    }
}

class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _data = MutableStateFlow(read())
    val data: StateFlow<LauncherData> = _data

    fun update(transform: (LauncherData) -> LauncherData) {
        _data.update(transform)
        write(_data.value)
    }

    private fun read(): LauncherData {
        val folders = obj(KEY_FOLDERS).let { json ->
            json.keys().asSequence().associateWith { id ->
                val f = json.getJSONObject(id)
                FolderData(id, f.getString("name"), f.getJSONArray("apps").strings())
            }
        }
        val recents = arr(KEY_RECENTS).let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                o.getString("key") to o.getLong("at")
            }
        }
        val widgets = arr(KEY_WIDGETS).let { a -> (0 until a.length()).map { a.getInt(it) } }
        val layouts = obj(KEY_LAYOUTS).let { json ->
            json.keys().asSequence().associateWith { id ->
                val l = json.getJSONObject(id)
                ThemeLayout(
                    iconSizeDp = l.optInt("iconSize", 52),
                    gridColumns = l.optInt("columns", 4),
                    showLabels = l.optBoolean("labels", true),
                    useThemeWallpaper = l.optBoolean("themeWallpaper", true),
                    freePlacement = l.optBoolean("free", false),
                    multiPage = l.optBoolean("multiPage", false),
                    pageCount = l.optInt("pages", 1),
                    menuIcon = enumOr(l.optString("menuIcon"), MenuIcon.DEFAULT),
                    showDock = l.optBoolean("dock", true),
                    dockBackground = l.optBoolean("dockBackground", true),
                    menuOpensAllApps = l.optBoolean("menuAllApps", false),
                    showTopBar = l.optBoolean("topBar", true),
                )
            }
        }
        val placements = obj(KEY_PLACEMENTS).let { json ->
            json.keys().asSequence().associateWith { theme ->
                val items = json.getJSONObject(theme)
                items.keys().asSequence().associateWith { id ->
                    val p = items.getJSONArray(id)
                    Placement(p.getInt(0), p.getDouble(1).toFloat(), p.getDouble(2).toFloat(), p.getDouble(3).toFloat(), p.getDouble(4).toFloat())
                }
            }
        }
        val hidden = obj(KEY_HIDDEN).let { json ->
            json.keys().asSequence().associateWith { theme -> json.getJSONArray(theme).strings().toSet() }
        }
        return LauncherData(
            themeId = prefs.getString(KEY_THEME, "windows")!!,
            initialized = prefs.getBoolean(KEY_INITIALIZED, false),
            homeOrder = arr(KEY_HOME).strings(),
            folders = folders,
            dock = arr(KEY_DOCK).strings(),
            recents = recents,
            widgetIds = widgets,
            layouts = layouts,
            colorMode = enumOr(prefs.getString(KEY_COLOR_MODE, null), ColorMode.SYSTEM),
            swipeDown = enumOr(prefs.getString(KEY_SWIPE_DOWN, null), SwipeAction.CONTROL_CENTER),
            placements = placements,
            hiddenBuiltins = hidden,
            transparency = prefs.getBoolean(KEY_TRANSPARENCY, true),
            appsView = enumOr(prefs.getString(KEY_APPS_VIEW, null), AppsView.GRID),
            leftScreen = enumOr(prefs.getString(KEY_LEFT_SCREEN, null), LeftScreen.FEED),
        )
    }

    private fun write(d: LauncherData) {
        val folders = JSONObject().apply {
            d.folders.values.forEach { f ->
                put(f.id, JSONObject().put("name", f.name).put("apps", JSONArray(f.appKeys)))
            }
        }
        val recents = JSONArray().apply {
            d.recents.forEach { (key, at) -> put(JSONObject().put("key", key).put("at", at)) }
        }
        val layouts = JSONObject().apply {
            d.layouts.forEach { (id, l) ->
                put(
                    id,
                    JSONObject()
                        .put("iconSize", l.iconSizeDp)
                        .put("columns", l.gridColumns)
                        .put("labels", l.showLabels)
                        .put("themeWallpaper", l.useThemeWallpaper)
                        .put("free", l.freePlacement)
                        .put("multiPage", l.multiPage)
                        .put("pages", l.pageCount)
                        .put("menuIcon", l.menuIcon.name)
                        .put("dock", l.showDock)
                        .put("dockBackground", l.dockBackground)
                        .put("menuAllApps", l.menuOpensAllApps)
                        .put("topBar", l.showTopBar),
                )
            }
        }
        val placements = JSONObject().apply {
            d.placements.forEach { (theme, items) ->
                put(theme, JSONObject().apply {
                    items.forEach { (id, p) ->
                        put(id, JSONArray().put(p.page).put(p.x.toDouble()).put(p.y.toDouble()).put(p.w.toDouble()).put(p.h.toDouble()))
                    }
                })
            }
        }
        val hidden = JSONObject().apply { d.hiddenBuiltins.forEach { (theme, ids) -> put(theme, JSONArray(ids.toList())) } }
        prefs.edit()
            .putString(KEY_THEME, d.themeId)
            .putBoolean(KEY_INITIALIZED, d.initialized)
            .putString(KEY_HOME, JSONArray(d.homeOrder).toString())
            .putString(KEY_FOLDERS, folders.toString())
            .putString(KEY_DOCK, JSONArray(d.dock).toString())
            .putString(KEY_RECENTS, recents.toString())
            .putString(KEY_WIDGETS, JSONArray(d.widgetIds).toString())
            .putString(KEY_LAYOUTS, layouts.toString())
            .putString(KEY_COLOR_MODE, d.colorMode.name)
            .putString(KEY_SWIPE_DOWN, d.swipeDown.name)
            .putString(KEY_PLACEMENTS, placements.toString())
            .putString(KEY_HIDDEN, hidden.toString())
            .putBoolean(KEY_TRANSPARENCY, d.transparency)
            .putString(KEY_APPS_VIEW, d.appsView.name)
            .putString(KEY_LEFT_SCREEN, d.leftScreen.name)
            .apply()
    }

    private fun obj(key: String) = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrDefault(JSONObject())
    private fun arr(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")!!) }.getOrDefault(JSONArray())
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_HOME = "home"
        const val KEY_FOLDERS = "folders"
        const val KEY_DOCK = "dock"
        const val KEY_RECENTS = "recents"
        const val KEY_WIDGETS = "widgets"
        const val KEY_LAYOUTS = "layouts"
        const val KEY_COLOR_MODE = "colorMode"
        const val KEY_SWIPE_DOWN = "swipeDown"
        const val KEY_PLACEMENTS = "placements"
        const val KEY_HIDDEN = "hiddenBuiltins"
        const val KEY_TRANSPARENCY = "transparency"
        const val KEY_APPS_VIEW = "appsView"
        const val KEY_LEFT_SCREEN = "leftScreen"
    }
}
