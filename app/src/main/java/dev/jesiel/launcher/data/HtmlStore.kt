package dev.jesiel.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Um widget da loja. */
data class StoreWidget(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val role: HtmlRole,
    val interactive: Boolean,
    val network: Boolean,
    val html: String,
    val updated: String,
)

/**
 * Loja de widgets HTML, sem servidor próprio: o catálogo é o arquivo `store/index.json` do
 * repositório público do Launcher no GitHub. Enviar abre uma issue já preenchida; quando o dono
 * aprova (etiqueta "aprovado"), um GitHub Action publica o widget no catálogo.
 */
object HtmlStore {
    const val REPO = "jesieldotdev/launcher"
    const val INDEX_URL = "https://raw.githubusercontent.com/$REPO/HEAD/store/index.json"
    /** Endereço do catálogo (os testes trocam por um que falha na hora, para usar a cópia guardada). */
    var indexUrl = INDEX_URL
    const val PAGE_URL = "https://github.com/$REPO/tree/HEAD/store"
    const val CACHE = "loja-index.json"
    /** Links mais longos que isso não abrem em todos os navegadores: o conteúdo vai pela área de transferência. */
    private const val MAX_URL = 7000

    fun parse(json: String): List<StoreWidget> {
        val list = JSONObject(json).optJSONArray("widgets") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val html = o.optString("html")
            if (html.isBlank()) return@mapNotNull null
            StoreWidget(
                id = o.optString("id").ifBlank { "item-$i" },
                name = o.optString("name").ifBlank { "Widget" },
                author = o.optString("author").ifBlank { "anônimo" },
                description = o.optString("description"),
                role = runCatching { HtmlRole.valueOf(o.optString("role", "DESKTOP").uppercase()) }.getOrDefault(HtmlRole.DESKTOP),
                interactive = o.optBoolean("interactive", html.contains("<script")),
                network = o.optBoolean("network", false),
                html = html,
                updated = o.optString("updated"),
            )
        }
    }

    /** Catálogo e se veio da cópia guardada (sem internet). */
    data class Catalog(val items: List<StoreWidget>, val offline: Boolean)

    /** Baixa o catálogo; sem internet, devolve a última cópia guardada (ou erro). */
    suspend fun fetch(context: Context): Result<Catalog> = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, CACHE)
        runCatching {
            val conn = (URL(indexUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                // O GitHub guarda em cache por alguns minutos; isso pede a versão nova.
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (conn.responseCode != 200) error("A loja respondeu ${conn.responseCode}")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                Catalog(parse(text), offline = false).also { cache.writeText(text) }
            } finally {
                conn.disconnect()
            }
        }.recoverCatching { e ->
            if (cache.exists()) Catalog(parse(cache.readText()), offline = true) else throw e
        }
    }

    /** Widget pronto para guardar, marcado com o item da loja de onde veio. */
    fun toWidget(item: StoreWidget, keepId: String? = null) = HtmlWidget(
        id = keepId ?: java.util.UUID.randomUUID().toString().take(8),
        name = item.name,
        html = item.html,
        interactive = item.interactive,
        network = item.network,
        onDesktop = keepId == null && item.role == HtmlRole.DESKTOP,
        storeId = item.id,
        storeUpdated = item.updated,
    )

    /** Texto da issue: explicação curta e o widget num bloco ```json (lido pelo Action). */
    fun issueBody(widget: HtmlWidget, author: String, description: String, role: HtmlRole): String {
        val json = JSONObject()
            .put("name", widget.name)
            .put("author", author.trim())
            .put("description", description.trim())
            .put("role", role.name)
            .put("interactive", widget.interactive)
            .put("network", widget.network)
            .put("html", widget.html)
            .toString(2)
        return "Enviado pelo app Launcher (Estúdio HTML).\n\n" +
            "**${widget.name}** · ${role.label}" + (if (description.isNotBlank()) "\n\n${description.trim()}" else "") +
            "\n\n```json\n$json\n```\n"
    }

    /**
     * Link para abrir a issue já preenchida. Se o widget for grande demais para caber no link,
     * [tooLong] é verdadeiro e o corpo deve ir pela área de transferência.
     */
    fun issueUrl(widget: HtmlWidget, body: String): Pair<String, Boolean> {
        fun enc(t: String) = URLEncoder.encode(t, "UTF-8").replace("+", "%20")
        val base = "https://github.com/$REPO/issues/new?template=widget-loja.md&title=" + enc("[Loja] " + widget.name)
        val full = "$base&body=${enc(body)}"
        if (full.length <= MAX_URL) return full to false
        val note = "O código do widget foi copiado pelo app. Cole aqui (segure e escolha Colar)."
        return "$base&body=${enc(note)}" to true
    }
}
