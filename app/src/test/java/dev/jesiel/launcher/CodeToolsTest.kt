package dev.jesiel.launcher

import dev.jesiel.launcher.data.CodeTools
import dev.jesiel.launcher.data.CodeTools.Kind
import dev.jesiel.launcher.data.HtmlTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeToolsTest {

    private fun kindsAt(text: String, piece: String): Set<Kind> {
        val at = text.indexOf(piece)
        // O último trecho que cobre a posição é o que vale na tela.
        return CodeTools.highlight(text).filter { at >= it.start && at < it.end }.map { it.kind }.toSet()
    }

    @Test
    fun highlightsHtmlCssJsAndVariables() {
        val code = """
            <style>.a{color:#ffd586;width:12px} /* nota */</style>
            <div class="a" id='x'>Bom dia {{hora}}</div>
            <!-- fim -->
            <script>var n = 3; // conta
            </script>
        """.trimIndent()
        assertTrue(Kind.TAG in kindsAt(code, "<div"))
        assertTrue(Kind.ATTR in kindsAt(code, "class="))
        assertTrue(Kind.STRING in kindsAt(code, "\"a\""))
        assertTrue(Kind.PROPERTY in kindsAt(code, "color:"))
        assertTrue(Kind.NUMBER in kindsAt(code, "#ffd586"))
        assertTrue(Kind.VAR in kindsAt(code, "{{hora}}"))
        assertTrue(Kind.KEYWORD in kindsAt(code, "var n"))
        assertTrue(Kind.COMMENT in kindsAt(code, "/* nota"))
        assertTrue(Kind.COMMENT in kindsAt(code, "// conta"))
        assertTrue(Kind.COMMENT in kindsAt(code, "<!-- fim"))
        // Texto comum não é colorido como código.
        assertTrue(kindsAt(code, "Bom dia").isEmpty())
    }

    @Test
    fun urlInsideStringIsNotAComment() {
        val code = "<script>fetch('https://ntfy.sh/x')</script>"
        assertTrue(Kind.COMMENT !in kindsAt(code, "//ntfy"))
    }

    @Test
    fun newlineKeepsAndIncreasesIndent() {
        val old = "<div>\n  <p>oi</p>"
        val typed = old + "\n"
        val e = CodeTools.autoEdit(old, typed, typed.length)!!
        assertEquals(old + "\n  ", e.text)
        assertEquals(e.text.length, e.cursor)

        val open = "<div>"
        val withClose = "<div></div>"
        // Enter entre <div> e </div>: </div> desce para a própria linha e o cursor fica indentado no meio.
        val newText = "<div>\n</div>"
        val e2 = CodeTools.autoEdit(withClose, newText, open.length + 1)!!
        assertEquals("<div>\n  \n</div>", e2.text)
        assertEquals("<div>\n  ".length, e2.cursor)
    }

    @Test
    fun closesTagsButNotVoidOnes() {
        val e = CodeTools.autoEdit("<div class=\"a\"", "<div class=\"a\">", 15)!!
        assertEquals("<div class=\"a\"></div>", e.text)
        assertEquals(15, e.cursor)
        assertNull(CodeTools.autoEdit("<br", "<br>", 4))
        assertNull(CodeTools.autoEdit("<img src=\"x\" /", "<img src=\"x\" />", 15))
        assertNull(CodeTools.autoEdit("a > b", "a >> b", 3))
    }

    @Test
    fun findsTagProblemsWithLines() {
        assertNull(CodeTools.tagProblem("<div><p>oi</p><br><img src=x></div>"))
        assertNull(CodeTools.tagProblem("<style>a>b{}</style><script>if (a < b) {}</script>"))
        // </div> chega antes de </span>: o erro aponta a troca e onde a tag foi aberta.
        assertTrue(CodeTools.tagProblem("<div>\n<span>x\n</div>")!!.startsWith("Linha 3: </div> fecha <span>"))
        assertEquals("Linha 1: <div> não foi fechada", CodeTools.tagProblem("<div><p>x</p>"))
        assertTrue(CodeTools.tagProblem("</p>")!!.contains("sem <p> aberta"))
    }

    @Test
    fun builtInTemplatesHaveNoTagProblems() {
        HtmlTemplates.all.forEach { (name, html) -> assertNull("$name: ${CodeTools.tagProblem(html)}", CodeTools.tagProblem(html)) }
    }

    @Test
    fun findsAllMatchesIgnoringCase() {
        assertEquals(listOf(0, 6), CodeTools.find("Hora: hora", "hora"))
        assertTrue(CodeTools.find("abc", "").isEmpty())
    }
}
