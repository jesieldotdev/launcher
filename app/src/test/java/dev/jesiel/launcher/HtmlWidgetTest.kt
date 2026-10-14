package dev.jesiel.launcher

import androidx.compose.ui.graphics.asImageBitmap
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

    @Test
    fun materialYouColorsAreHexInBothModes() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(true, false).forEach { dark ->
            val colors = dev.jesiel.launcher.ui.common.MaterialColors.of(context, dark, 0xFF3366CC.toInt())
            assertTrue(colors.keys == dev.jesiel.launcher.ui.common.MaterialColors.names.toSet())
            colors.values.forEach { assertTrue("cor inválida: $it", Regex("#[0-9A-F]{6}").matches(it)) }
        }
        // Todas as cores do Material You aparecem na lista de variáveis do editor.
        dev.jesiel.launcher.ui.common.MaterialColors.names.forEach { n -> assertTrue(HtmlVariables.all.any { it.first == n }) }
    }

    @Test
    fun aiGuideListsEveryVariableAndOptionalCode() {
        val guide = dev.jesiel.launcher.data.HtmlWidgetGuide.build()
        HtmlVariables.all.forEach { (k, _) -> assertTrue("$k fora do guia", guide.contains("`$k`")) }
        listOf("launcher://musica/alternar", "onLauncherData", "data-lsrc", "Interativo", "Acesso à internet").forEach { assertTrue(it, guide.contains(it)) }
        assertFalse(guide.contains("Código atual do widget"))
        // O exemplo do guia segue as próprias regras.
        val example = guide.substringAfter("```html\n").substringBefore("```")
        assertTrue(dev.jesiel.launcher.data.CodeTools.tagProblem(example) == null)

        val withCode = dev.jesiel.launcher.data.HtmlWidgetGuide.build("<b>{{hora}}</b>", interactive = true, network = false)
        assertTrue(withCode.contains("<b>{{hora}}</b>"))
        assertTrue(withCode.contains("Interativo = sim, Acesso à internet = não"))
        assertTrue(withCode.trimEnd().endsWith("[descreva aqui o que mudar]"))
    }

    @Test
    fun englishNamesWorkLikePortugueseOnes() {
        // Toda variável tem nome em inglês, sem repetir.
        HtmlVariables.all.forEach { (k, _) -> assertTrue("$k sem nome em inglês", HtmlVariables.english.containsKey(k)) }
        assertTrue(HtmlVariables.english.values.toSet().size == HtmlVariables.english.size)
        // Os nomes em inglês são detectados como as variáveis internas.
        val used = HtmlVariables.used("<b>{{time}}</b><i style=\"width:calc(var(--battery)*1%)\"></i><script>launcher.musicTitle</script>")
        assertTrue(used.containsAll(setOf("hora", "bateria", "musicaTitulo")))
        assertTrue(HtmlVariables.needsNotificationAccess(used))
        // O valor chega pelos dois nomes.
        val values = HtmlVariables.withEnglish(mapOf("hora" to "17:03", "bateria" to "71"))
        val html = HtmlVariables.render("<p>{{time}} {{hora}} {{battery}}%</p>", values, interactive = false)
        assertTrue(html.contains("17:03 17:03 71%"))
        assertTrue(html.contains("--battery:71"))
    }

    @Test
    fun lockScreenLinksThatNeedUnlocking() {
        val lock = dev.jesiel.launcher.data.LockScreen
        // Música funciona com a tela bloqueada; abrir apps, páginas e ajustes pede desbloqueio.
        assertFalse(lock.needsUnlock("launcher://musica/alternar"))
        assertFalse(lock.needsUnlock("launcher://music/next"))
        assertFalse(lock.needsUnlock("launcher://desbloquear"))
        assertTrue(lock.needsUnlock("launcher://musica"))
        assertTrue(lock.needsUnlock("launcher://app/com.whatsapp"))
        assertTrue(lock.needsUnlock("launcher://ajustes"))
        assertTrue(lock.needsUnlock("https://example.com"))
    }

    @Test
    fun notificationContentIsSafeInsideTheWidget() {
        val n = dev.jesiel.launcher.data.NotificationItem("WhatsApp", "com.whatsapp", "João", "oi\n</style><b>x", 0L)
        assertTrue(HtmlVariables.notificationLine(n) == "WhatsApp · João: oi\n</style><b>x")
        assertTrue(HtmlVariables.notificationLine(n.copy(title = "")) == "WhatsApp · oi\n</style><b>x")
        val text = HtmlVariables.notificationLine(n) + "\n" + "Gmail · Fatura"
        val html = HtmlVariables.render("<p>{{notificacoesTexto}}</p>", mapOf("notificacoesTexto" to text, "musicaTitulo" to "#1 Hit"), interactive = false)
        // Só o </style> da própria página existe; o do texto foi escapado no CSS e no HTML.
        assertTrue(html.split("</style>").size == 2)
        assertTrue(html.contains("&lt;/style&gt;"))
        assertTrue(html.contains("--musicaTitulo:\"#1 Hit\""))
        val json = HtmlVariables.notificationsJson(listOf(n)) { "12:30" }
        val parsed = org.json.JSONArray(json).getJSONObject(0)
        assertTrue(parsed.getString("app") == "WhatsApp" && parsed.getString("hora") == "12:30" && parsed.getString("pacote") == "com.whatsapp")
        assertTrue(HtmlVariables.needsNotificationAccess(HtmlVariables.used("{{lastNotification}}")))
    }

    @Test
    fun appsListIsJsonWithIconsAndLetters() {
        val bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        fun app(label: String, pkg: String) = dev.jesiel.launcher.data.AppInfo(
            key = "$pkg/.Main#0", label = label, component = android.content.ComponentName(pkg, "$pkg.Main"),
            user = android.os.Process.myUserHandle(), icon = bitmap.asImageBitmap(),
        )
        val apps = listOf(app("Éden", "com.eden"), app("2048", "com.game"), app("WhatsApp", "com.whatsapp"))
        val parsed = org.json.JSONArray(HtmlVariables.appsJson(apps, favorites = setOf("com.whatsapp/.Main#0")))
        assertTrue(parsed.length() == 3)
        val eden = parsed.getJSONObject(0)
        assertTrue(eden.getString("letra") == "E" && eden.getString("nome") == "Éden" && eden.getString("name") == "Éden")
        assertTrue(eden.getString("icone") == "launcher-icon://com.eden" && eden.getString("icon") == eden.getString("icone"))
        assertTrue(parsed.getJSONObject(1).getString("letra") == "#")
        assertTrue(parsed.getJSONObject(2).getBoolean("favorito") && !eden.getBoolean("favorito"))
    }

    @Test
    fun jsonListsStayOutOfCssVariables() {
        val html = HtmlVariables.render("<script>launcher.appsLista</script>", mapOf("appsLista" to "[{\"nome\":\"A\"}]", "appsTotal" to "1"), interactive = true)
        assertFalse(html.contains("--appsLista"))
        assertTrue(html.contains("--appsTotal:1"))
        assertTrue(HtmlVariables.used("<script>launcher.appsList; launcher.searchMode</script>").containsAll(listOf("appsLista", "modoBusca")))
    }

    @Test
    fun appListTemplatesAreInteractiveAndUseTheApps() {
        val lists = HtmlTemplates.all.filter { it.role == dev.jesiel.launcher.data.HtmlRole.APPS }
        assertTrue(lists.size >= 2)
        lists.forEach { t ->
            assertTrue(t.name, t.html.contains("<script") && "appsLista" in HtmlVariables.used(t.html) && "modoBusca" in HtmlVariables.used(t.html))
            assertTrue(t.name, t.html.contains("launcher://fechar") || t.html.contains("launcher://app/"))
        }
        assertTrue(HtmlTemplates.all.any { it.role == dev.jesiel.launcher.data.HtmlRole.LOCK })
    }
}
