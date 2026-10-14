package dev.jesiel.launcher.data

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dados salvos pelos widgets HTML (o `localStorage` deles). Cada widget tem o seu espaço,
 * separado pelo id; os dados ficam no app, entram nos backups e somem com o widget.
 */
object WidgetStorage {
    private const val PREFS = "widget_storage"
    /** Limite por widget (chaves + valores, em caracteres). */
    const val MAX_PER_WIDGET = 1_000_000
    private const val SEP = '\u0000'

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun prefix(widgetId: String) = widgetId + SEP

    fun get(context: Context, widgetId: String, key: String): String? = prefs(context).getString(prefix(widgetId) + key, null)

    /** Falso se passar do limite do widget. */
    fun set(context: Context, widgetId: String, key: String, value: String): Boolean {
        val current = entries(context, widgetId)
        val size = current.entries.sumOf { (k, v) -> if (k == key) 0 else k.length + v.length } + key.length + value.length
        if (size > MAX_PER_WIDGET) return false
        prefs(context).edit().putString(prefix(widgetId) + key, value).apply()
        return true
    }

    fun remove(context: Context, widgetId: String, key: String) = prefs(context).edit().remove(prefix(widgetId) + key).apply()

    fun clear(context: Context, widgetId: String) {
        val p = prefs(context)
        val editor = p.edit()
        p.all.keys.filter { it.startsWith(prefix(widgetId)) }.forEach(editor::remove)
        editor.apply()
    }

    fun entries(context: Context, widgetId: String): Map<String, String> {
        val pre = prefix(widgetId)
        return prefs(context).all.mapNotNull { (k, v) -> if (k.startsWith(pre) && v is String) k.removePrefix(pre) to v else null }
            .sortedBy { it.first }.toMap()
    }

    /** Tamanho dos dados do widget, em caracteres (≈ bytes). */
    fun size(context: Context, widgetId: String): Int = entries(context, widgetId).entries.sumOf { (k, v) -> k.length + v.length }

    /** Ao duplicar um widget, a cópia começa com os mesmos dados. */
    fun copy(context: Context, fromId: String, toId: String) {
        val editor = prefs(context).edit()
        entries(context, fromId).forEach { (k, v) -> editor.putString(prefix(toId) + k, v) }
        editor.apply()
    }

    /** Para o backup: { idDoWidget: { chave: valor } }. */
    fun exportJson(context: Context): JSONObject {
        val out = JSONObject()
        prefs(context).all.forEach { (k, v) ->
            val i = k.indexOf(SEP)
            if (i > 0 && v is String) {
                val id = k.substring(0, i)
                (out.optJSONObject(id) ?: JSONObject().also { out.put(id, it) }).put(k.substring(i + 1), v)
            }
        }
        return out
    }

    fun importJson(context: Context, json: JSONObject) {
        val editor = prefs(context).edit().clear()
        json.keys().forEach { id ->
            val values = json.optJSONObject(id) ?: return@forEach
            values.keys().forEach { k -> editor.putString(prefix(id) + k, values.optString(k)) }
        }
        editor.commit()
    }

    /**
     * Ponte exposta à página como `__launcherStorage`. O launcher troca o `localStorage` da
     * página por um que chama esta ponte (ver [HtmlVariables.render]), então o código comum
     * (`localStorage.setItem(...)`) funciona e é salvo aqui.
     */
    class Bridge(context: Context, private val widgetId: String) {
        private val app = context.applicationContext

        @JavascriptInterface fun getItem(key: String): String? = get(app, widgetId, key)
        @JavascriptInterface fun setItem(key: String, value: String): Boolean = set(app, widgetId, key, value)
        @JavascriptInterface fun removeItem(key: String) = remove(app, widgetId, key)
        @JavascriptInterface fun clear() = clear(app, widgetId)
        @JavascriptInterface fun keys(): String = JSONArray(entries(app, widgetId).keys.toList()).toString()
    }
}
