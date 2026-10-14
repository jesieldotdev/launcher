package dev.jesiel.launcher

import dev.jesiel.launcher.data.HtmlTemplates
import dev.jesiel.launcher.data.HtmlVariables
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HtmlWidgetTest {

    private val values = mapOf(
        "hora" to "17:03", "data" to "ter., 13 de out.", "diaSemana" to "terça-feira", "dia" to "13", "mes" to "outubro",
        "ano" to "2026", "saudacao" to "Boa tarde", "bateria" to "71", "carregando" to "Carregando", "alarme" to "qua 07:00",
        "memoria" to "48", "armazenamento" to "92", "tema" to "escuro", "cor" to "#4CC2FF",
    )

    @Test
    fun replacesTextAndAttributes() {
        val html = HtmlVariables.render("""<div style="width:{{bateria}}%">{{hora}} <b>{{x}}</b></div>""", values, interactive = false)
        assertTrue(html.contains("width:71%"))
        assertTrue(html.contains(">17:03 <b></b>"))
        assertTrue(html.contains("--bateria:71"))
        assertFalse(html.contains("<script"))
        // Toque longo não deve selecionar texto do widget.
        assertTrue(html.contains("user-select:none"))
    }

    @Test
    fun interactiveModeMarksValuesForLiveUpdates() {
        val html = HtmlVariables.render("<p>{{hora}}</p>", values, interactive = true)
        assertTrue(html.contains("""<span data-lv="hora">17:03</span>"""))
        assertTrue(html.contains("__launcherUpdate"))
    }

    @Test
    fun escapesValues() {
        val html = HtmlVariables.render("<p>{{hora}}</p>", mapOf("hora" to "<b>&"), interactive = false)
        assertTrue(html.contains("&lt;b&gt;&amp;"))
    }

    @Test
    fun usedVariablesAreDetected() {
        val used = HtmlVariables.used("<p>{{hora}}</p><style>.x{width:calc(var(--bateria)*1%)}</style><script>launcher.memoria</script>")
        assertTrue(used.containsAll(listOf("hora", "bateria", "memoria")))
        assertFalse("alarme" in used)
    }

    /** Grava os modelos renderizados para conferir no navegador (build/html-widgets). */
    @Test
    fun dumpTemplates() {
        val dir = java.io.File("build/html-widgets").apply { mkdirs() }
        HtmlTemplates.all.forEachIndexed { i, (_, code) ->
            java.io.File(dir, "template-$i.html").writeText(HtmlVariables.render(code, values, interactive = code.contains("<script")))
        }
    }

    @Test
    fun musicTemplateDeclaresItsVariables() {
        val used = HtmlVariables.used(HtmlTemplates.MUSIC.second)
        listOf("musicaTitulo", "musicaArtista", "musicaAlbum", "musicaApp", "musicaTocando", "musicaPosicao", "musicaDuracao", "musicaProgresso", "musicaCapa")
            .forEach { assertTrue("$it não detectada", it in used) }
        assertTrue(HtmlVariables.needsNotificationAccess(used))
        assertFalse(HtmlVariables.needsNotificationAccess(setOf("hora", "bateria")))
    }

    @Test
    fun interactiveUpdateRefreshesImageSources() {
        val html = HtmlVariables.render("""<img data-lsrc="musicaCapa" src="{{musicaCapa}}">""", mapOf("musicaCapa" to "data:image/jpeg;base64,AAA"), interactive = true)
        assertTrue(html.contains("""src="data:image/jpeg;base64,AAA""""))
        assertTrue(html.contains("[data-lsrc]"))
    }

    @Test
    fun formatsMusicTime() {
        assertTrue(dev.jesiel.launcher.data.MediaWatcher.formatTime(83_000) == "1:23")
        assertTrue(dev.jesiel.launcher.data.MediaWatcher.formatTime(3_725_000) == "1:02:05")
        assertTrue(dev.jesiel.launcher.data.MediaWatcher.formatTime(-5) == "0:00")
    }

    @Test
    fun positionAdvancesOnlyWhilePlaying() {
        val base = dev.jesiel.launcher.data.NowPlaying("t", "a", "", "", "p", playing = true, positionMs = 10_000, positionAt = 1_000, speed = 1f, durationMs = 60_000, artDataUri = "")
        assertTrue(base.positionNow(6_000) == 15_000L)
        assertTrue(base.copy(playing = false).positionNow(6_000) == 10_000L)
        assertTrue(base.positionNow(999_999) == 60_000L)
    }
}
