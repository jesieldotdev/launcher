package dev.jesiel.launcher

import dev.jesiel.launcher.data.NewsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewsParserTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0"><channel>
          <item>
            <title><![CDATA[Manchete com mídia]]></title>
            <link>https://exemplo.com/a</link>
            <media:content url="https://img.exemplo.com/a.jpg" medium="image"/>
            <pubDate>Tue, 13 Oct 2026 12:00:00 -0300</pubDate>
          </item>
          <item>
            <title>Manchete com imagem na descrição</title>
            <link>https://exemplo.com/b</link>
            <description><![CDATA[<img src="https://img.exemplo.com/b.png" /><br/>Texto]]></description>
            <pubDate>Tue, 13 Oct 2026 11:00:00 GMT</pubDate>
          </item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun extractsTitlesLinksDatesAndImages() {
        val items = NewsRepository().parse(rss.byteInputStream(), "Fonte")
        assertEquals(2, items.size)
        assertEquals("Manchete com mídia", items[0].title)
        assertEquals("https://img.exemplo.com/a.jpg", items[0].imageUrl)
        assertEquals("https://img.exemplo.com/b.png", items[1].imageUrl)
        assertNotNull(items[0].publishedAt)
        assertNotNull(items[1].publishedAt)
    }

    @Test
    fun parsesRealFeedSample() {
        val file = java.io.File(System.getenv("RSS_SAMPLE") ?: "/nonexistent")
        if (!file.exists()) return
        val items = file.inputStream().use { NewsRepository().parse(it, "ge") }
        assert(items.isNotEmpty())
        assert(items.count { it.imageUrl != null } > items.size / 2)
    }
}
