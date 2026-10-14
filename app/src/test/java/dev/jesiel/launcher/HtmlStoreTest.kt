package dev.jesiel.launcher

import dev.jesiel.launcher.data.HtmlRole
import dev.jesiel.launcher.data.HtmlStore
import dev.jesiel.launcher.data.HtmlWidget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HtmlStoreTest {

    /** O catálogo do repositório (store/index.json) é lido pelo app. */
    @Test
    fun parsesTheRepositoryCatalog() {
        val items = HtmlStore.parse(File("../store/index.json").readText())
        assertTrue("catálogo com os widgets iniciais", items.size >= 9)
        assertTrue(items.any { it.role == HtmlRole.LOCK && it.name == "Moto Display" })
        assertTrue(items.any { it.role == HtmlRole.DOCK })
        assertTrue(items.all { it.html.isNotBlank() && it.updated.isNotBlank() })
        val w = HtmlStore.toWidget(items.first { it.role == HtmlRole.APPS })
        assertFalse("lista de apps não vai para a área de trabalho", w.onDesktop)
        assertTrue(w.storeId != null && w.storeUpdated != null)
    }

    /** O corpo da issue leva o widget num bloco ```json que o Action (store/publish.py) lê. */
    @Test
    fun issueBodyCarriesTheWidget() {
        val widget = HtmlWidget("x", "Relógio \"neon\"", "<b>{{hora}}</b>\n<script>var a = `x`;</script>", interactive = true)
        val body = HtmlStore.issueBody(widget, "Jesiel", "Um relógio", HtmlRole.DESKTOP)
        val json = Regex("```(?:json)?\\s*(\\{.*\\})\\s*```", RegexOption.DOT_MATCHES_ALL).find(body)!!.groupValues[1]
        val o = JSONObject(json)
        assertEquals(widget.html, o.getString("html"))
        assertEquals("DESKTOP", o.getString("role"))
        assertEquals("Jesiel", o.getString("author"))
        // Para conferir com o script do Action.
        File("build/loja-issue.txt").writeText(body)

        val (shortUrl, shortTooLong) = HtmlStore.issueUrl(widget, body)
        assertFalse(shortTooLong)
        assertTrue(shortUrl.startsWith("https://github.com/${HtmlStore.REPO}/issues/new?template=widget-loja.md&title="))
        val big = widget.copy(html = "x".repeat(20_000))
        val (bigUrl, bigTooLong) = HtmlStore.issueUrl(big, HtmlStore.issueBody(big, "", "", HtmlRole.DOCK))
        assertTrue(bigTooLong)
        assertTrue(bigUrl.length < 7000)
    }
}
