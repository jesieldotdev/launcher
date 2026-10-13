package dev.jesiel.launcher.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class NewsItem(val title: String, val source: String, val link: String, val publishedAt: Long?)

sealed interface NewsState {
    data object Loading : NewsState
    data class Loaded(val items: List<NewsItem>, val fetchedAt: Long) : NewsState
    data class Failed(val message: String) : NewsState
}

/** Principais notícias do Google Notícias (RSS público) no idioma e país do aparelho. */
class NewsRepository {

    suspend fun fetch(locale: Locale = Locale.getDefault()): List<NewsItem> = withContext(Dispatchers.IO) {
        val country = locale.country.ifBlank { "BR" }
        val language = locale.language.ifBlank { "pt" }
        val ceid = if (language == "pt" && country == "BR") "BR:pt-419" else "$country:$language"
        val url = URL("https://news.google.com/rss?hl=$language-$country&gl=$country&ceid=$ceid")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            connection.inputStream.use(::parse)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(stream: java.io.InputStream): List<NewsItem> {
        val parser = Xml.newPullParser().apply { setInput(stream, null) }
        val dates = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        val items = mutableListOf<NewsItem>()
        var inItem = false
        var title = ""
        var link = ""
        var source = ""
        var date: Long? = null
        var tag: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "item") {
                        inItem = true
                        title = ""; link = ""; source = ""; date = null
                    }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val text = parser.text.trim()
                    when (tag) {
                        "title" -> title += text
                        "link" -> link += text
                        "source" -> source += text
                        "pubDate" -> date = runCatching { dates.parse(text)?.time }.getOrNull()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        inItem = false
                        // O título vem como "Manchete - Veículo"; o veículo já aparece à parte.
                        val clean = if (source.isNotBlank()) title.removeSuffix(" - $source") else title
                        if (clean.isNotBlank() && link.isNotBlank()) items += NewsItem(clean, source, link, date)
                    }
                    tag = null
                }
            }
        }
        return items
    }
}
