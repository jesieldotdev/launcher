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
)

data class FolderData(val id: String, val name: String, val appKeys: List<String>)

/**
 * Tudo o que o launcher persiste. Favoritos, pastas, dock e widgets são compartilhados entre
 * temas, para que o usuário não perca a organização ao alternar.
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
) {
    fun layoutFor(themeId: String) = layouts[themeId] ?: ThemeLayout()
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
        val folders = JSONObject(prefs.getString(KEY_FOLDERS, "{}")!!).let { json ->
            json.keys().asSequence().associateWith { id ->
                val f = json.getJSONObject(id)
                FolderData(id, f.getString("name"), f.getJSONArray("apps").strings())
            }
        }
        val recents = JSONArray(prefs.getString(KEY_RECENTS, "[]")!!).let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getString("key") to o.getLong("at")
            }
        }
        val widgets = JSONArray(prefs.getString(KEY_WIDGETS, "[]")!!).let { arr ->
            (0 until arr.length()).map { arr.getInt(it) }
        }
        val layouts = JSONObject(prefs.getString(KEY_LAYOUTS, "{}")!!).let { json ->
            json.keys().asSequence().associateWith { id ->
                val l = json.getJSONObject(id)
                ThemeLayout(
                    iconSizeDp = l.optInt("iconSize", 52),
                    gridColumns = l.optInt("columns", 4),
                    showLabels = l.optBoolean("labels", true),
                    useThemeWallpaper = l.optBoolean("themeWallpaper", true),
                )
            }
        }
        return LauncherData(
            themeId = prefs.getString(KEY_THEME, "windows")!!,
            initialized = prefs.getBoolean(KEY_INITIALIZED, false),
            homeOrder = JSONArray(prefs.getString(KEY_HOME, "[]")!!).strings(),
            folders = folders,
            dock = JSONArray(prefs.getString(KEY_DOCK, "[]")!!).strings(),
            recents = recents,
            widgetIds = widgets,
            layouts = layouts,
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
                        .put("themeWallpaper", l.useThemeWallpaper),
                )
            }
        }
        prefs.edit()
            .putString(KEY_THEME, d.themeId)
            .putBoolean(KEY_INITIALIZED, d.initialized)
            .putString(KEY_HOME, JSONArray(d.homeOrder).toString())
            .putString(KEY_FOLDERS, folders.toString())
            .putString(KEY_DOCK, JSONArray(d.dock).toString())
            .putString(KEY_RECENTS, recents.toString())
            .putString(KEY_WIDGETS, JSONArray(d.widgetIds).toString())
            .putString(KEY_LAYOUTS, layouts.toString())
            .apply()
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_HOME = "home"
        const val KEY_FOLDERS = "folders"
        const val KEY_DOCK = "dock"
        const val KEY_RECENTS = "recents"
        const val KEY_WIDGETS = "widgets"
        const val KEY_LAYOUTS = "layouts"
    }
}
