package dev.jesiel.launcher.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class NewsItem(
    val title: String,
    val source: String,
    val link: String,
    val publishedAt: Long?,
    val imageUrl: String?,
)

sealed interface NewsState {
    data object Loading : NewsState
    data class Loaded(val items: List<NewsItem>, val fetchedAt: Long) : NewsState
    data class Failed(val message: String) : NewsState
}

/** Assuntos do feed. Cada um junta feeds RSS públicos que trazem imagens. */
enum class NewsTopic(val label: String, val feeds: List<Pair<String, String>>) {
    FOR_YOU("Para você", emptyList()),
    TOP("Principais", listOf("g1" to "https://g1.globo.com/rss/g1/")),
    TECH("Tecnologia", listOf("g1" to "https://g1.globo.com/rss/g1/tecnologia/", "Tecmundo" to "https://rss.tecmundo.com.br/feed")),
    SPORTS("Esportes", listOf("ge" to "https://ge.globo.com/rss/ge/")),
    WORLD("Mundo", listOf("BBC News Brasil" to "https://feeds.bbci.co.uk/portuguese/rss.xml")),
    ENTERTAINMENT("Entretenimento", listOf("g1" to "https://g1.globo.com/rss/g1/pop-arte/")),
    ECONOMY("Economia", listOf("g1" to "https://g1.globo.com/rss/g1/economia/")),
    ;

    /** "Para você" mistura um pouco de cada assunto. */
    val sources: List<Pair<String, String>>
        get() = if (this == FOR_YOU) entries.filter { it != FOR_YOU }.flatMap { it.feeds } else feeds
}

class NewsRepository {

    suspend fun fetch(topic: NewsTopic): List<NewsItem> = coroutineScope {
        val results = topic.sources.map { (name, url) ->
            async(Dispatchers.IO) { runCatching { fetchFeed(name, url) }.getOrDefault(emptyList()) }
        }.awaitAll()
        if (results.all { it.isEmpty() }) error("Nenhuma fonte respondeu")
        val perSource = if (topic == NewsTopic.FOR_YOU) 6 else 40
        results.flatMap { it.take(perSource) }
            .distinctBy { it.title.lowercase() }
            .sortedByDescending { it.publishedAt ?: 0L }
    }

    private suspend fun fetchFeed(name: String, url: String): List<NewsItem> = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Launcher")
        }
        try {
            connection.inputStream.use { parse(it, name) }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(stream: java.io.InputStream, sourceName: String): List<NewsItem> {
        val parser = Xml.newPullParser().apply { setInput(stream, null) }
        val dates = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        val items = mutableListOf<NewsItem>()
        var inItem = false
        var title = StringBuilder()
        var link = StringBuilder()
        var description = StringBuilder()
        var date: Long? = null
        var image: String? = null
        var tag: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when {
                        tag == null -> Unit
                        tag == "item" -> {
                            inItem = true
                            title = StringBuilder(); link = StringBuilder(); description = StringBuilder()
                            date = null; image = null
                        }
                        // Com ou sem processamento de namespace, o nome pode vir como "media:content" ou "content".
                        inItem && tag.substringAfter(':') in MEDIA_TAGS -> {
                            val type = parser.getAttributeValue(null, "type")
                            val url = parser.getAttributeValue(null, "url")
                            if (url != null && image == null && (type == null || type.startsWith("image"))) image = url
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) {
                    val text = parser.text
                    when (tag) {
                        "title" -> title.append(text)
                        "link" -> link.append(text)
                        "description" -> description.append(text)
                        "pubDate" -> date = parseDate(dates, text.trim())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        inItem = false
                        val img = image ?: IMG.find(description)?.groupValues?.get(1)
                        val t = title.toString().trim()
                        val l = link.toString().trim()
                        if (t.isNotBlank() && l.isNotBlank()) items += NewsItem(t, sourceName, l, date, img?.replace("&amp;", "&"))
                    }
                    tag = null
                }
            }
        }
        return items
    }

    private fun parseDate(format: SimpleDateFormat, text: String): Long? =
        runCatching { format.parse(text)?.time }.getOrNull()
            ?: runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(text)?.time }.getOrNull()

    private companion object {
        val MEDIA_TAGS = setOf("content", "thumbnail", "enclosure")
        val IMG = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    }
}
