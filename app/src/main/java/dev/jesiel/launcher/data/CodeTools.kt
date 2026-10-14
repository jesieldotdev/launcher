package dev.jesiel.launcher.data

/**
 * Ferramentas do editor de código dos widgets: destaque de sintaxe (HTML, CSS, JS e variáveis
 * `{{x}}`), indentação automática, fechamento de tags e verificação de tags. Sem dependência de
 * interface, para ser testável.
 */
object CodeTools {

    enum class Kind { TAG, ATTR, STRING, VAR, COMMENT, PROPERTY, NUMBER, KEYWORD }

    data class Span(val start: Int, val end: Int, val kind: Kind)

    private val VOID = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")

    private val TAG = Regex("""</?[a-zA-Z][\w-]*|/?>""")
    private val TAG_BODY = Regex("""<[a-zA-Z/!][^<>]*>""")
    private val ATTR = Regex("""(?<=\s)[a-zA-Z_:@][\w:.-]*(?==)""")
    private val STYLE_BLOCK = Regex("""(?is)<style\b[^>]*>(.*?)(</style>|$)""")
    private val SCRIPT_BLOCK = Regex("""(?is)<script\b[^>]*>(.*?)(</script>|$)""")
    private val PROPERTY = Regex("""(?m)(?<=[{;\s]|^)(--)?[a-zA-Z-]+(?=\s*:(?!:))""")
    private val NUMBER = Regex("""#[0-9a-fA-F]{3,8}\b|(?<![\w-])-?\d+(\.\d+)?(px|%|em|rem|vh|vw|vmin|vmax|cqh|cqw|s|ms|deg|fr)?\b""")
    private val KEYWORD = Regex("""\b(var|let|const|function|return|if|else|for|while|new|true|false|null|undefined|typeof|document|window|this|async|await)\b""")
    private val STRING = Regex(""""[^"\n]*"|'[^'\n]*'""")
    private val VARIABLE = Regex("""\{\{\s*\w+\s*\}\}|\blauncher\.\w+|var\(--\w+""")
    private val HTML_COMMENT = Regex("""(?s)<!--.*?(-->|$)""")
    private val BLOCK_COMMENT = Regex("""(?s)/\*.*?(\*/|$)""")
    private val LINE_COMMENT = Regex("""(?<![:"'\w\\])//[^\n]*""")

    /**
     * Trechos coloridos, em ordem de prioridade crescente: um trecho posterior se sobrepõe aos
     * anteriores (ex.: um comentário vence as palavras dentro dele).
     */
    fun highlight(text: String): List<Span> {
        if (text.length > 60_000) return emptyList()
        val spans = ArrayList<Span>()
        fun add(r: Regex, kind: Kind, range: IntRange = text.indices) {
            if (range.isEmpty()) return
            r.findAll(text.substring(range.first, range.last + 1)).forEach {
                spans += Span(range.first + it.range.first, range.first + it.range.last + 1, kind)
            }
        }
        val style = STYLE_BLOCK.findAll(text).mapNotNull { it.groups[1]?.range }.toList()
        val script = SCRIPT_BLOCK.findAll(text).mapNotNull { it.groups[1]?.range }.toList()
        val tags = TAG_BODY.findAll(text).map { it.range }.filter { r -> (style + script).none { r.first in it } }.toList()

        tags.forEach { add(TAG, Kind.TAG, it); add(ATTR, Kind.ATTR, it) }
        style.forEach { add(PROPERTY, Kind.PROPERTY, it); add(NUMBER, Kind.NUMBER, it) }
        script.forEach { add(NUMBER, Kind.NUMBER, it); add(KEYWORD, Kind.KEYWORD, it) }
        // Aspas só contam dentro de tags, CSS e JS: texto comum ("Bom dia") fica como está.
        (tags + style + script).forEach { add(STRING, Kind.STRING, it) }
        add(VARIABLE, Kind.VAR)
        style.forEach { add(BLOCK_COMMENT, Kind.COMMENT, it) }
        script.forEach { add(BLOCK_COMMENT, Kind.COMMENT, it); add(LINE_COMMENT, Kind.COMMENT, it) }
        add(HTML_COMMENT, Kind.COMMENT)
        return spans
    }

    data class Edit(val text: String, val cursor: Int)

    /**
     * Ajustes ao digitar: ao pular linha mantém (e aumenta, depois de `{` ou de uma tag aberta)
     * a indentação; ao fechar `<tag>` insere `</tag>` depois do cursor. Retorna nulo se nada muda.
     */
    fun autoEdit(oldText: String, newText: String, cursor: Int): Edit? {
        if (newText.length != oldText.length + 1 || cursor <= 0 || cursor > newText.length) return null
        if (newText.removeRange(cursor - 1, cursor) != oldText) return null
        val typed = newText[cursor - 1]
        val before = newText.substring(0, cursor - 1)
        val after = newText.substring(cursor)
        return when (typed) {
            '\n' -> {
                val prevLine = before.substringAfterLast('\n')
                val indent = prevLine.takeWhile { it == ' ' || it == '\t' }
                val trimmed = prevLine.trimEnd()
                val opens = trimmed.endsWith("{") || trimmed.endsWith("(") || opensTag(trimmed)
                val extra = if (opens) "  " else ""
                val closesNext = after.trimStart(' ', '\t').let { it.startsWith("}") || it.startsWith(")") || it.startsWith("</") }
                if (indent.isEmpty() && extra.isEmpty()) return null
                if (opens && closesNext) {
                    val rest = after.trimStart(' ', '\t')
                    val inserted = "\n" + indent + extra
                    Edit(before + inserted + "\n" + indent + rest, before.length + inserted.length)
                } else {
                    val inserted = "\n" + indent + extra
                    Edit(before + inserted + after, before.length + inserted.length)
                }
            }
            '>' -> {
                val m = Regex("""<([a-zA-Z][\w-]*)(\s[^<>]*)?$""").find(before) ?: return null
                val tag = m.groupValues[1]
                if (before.endsWith("/") || tag.lowercase() in VOID) return null
                val close = "</$tag>"
                if (after.startsWith(close)) return null
                Edit(before + ">" + close + after, cursor)
            }
            else -> null
        }
    }

    private fun opensTag(line: String): Boolean {
        val m = Regex("""<([a-zA-Z][\w-]*)[^<>]*>$""").find(line) ?: return false
        if (line.endsWith("/>")) return false
        val tag = m.groupValues[1].lowercase()
        return tag !in VOID && !line.substring(m.range.first).contains("</$tag")
    }

    /** Primeiro problema de tags (não fechada, fechada fora de ordem, fechamento sobrando), ou nulo. */
    fun tagProblem(html: String): String? {
        val clean = HTML_COMMENT.replace(html) { " ".repeat(it.value.length) }
        fun lineOf(i: Int) = clean.substring(0, i).count { it == '\n' } + 1
        val stack = ArrayDeque<Pair<String, Int>>()
        val tagRe = Regex("""<(/?)([a-zA-Z][\w-]*)\b[^<>]*?(/?)>""")
        var pos = 0
        while (true) {
            val m = tagRe.find(clean, pos) ?: break
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            val selfClosing = m.groupValues[3] == "/"
            pos = m.range.last + 1
            when {
                closing -> {
                    if (name in VOID) continue
                    val open = stack.lastOrNull()
                    when {
                        open == null -> return "Linha ${lineOf(m.range.first)}: </$name> sem <$name> aberta"
                        open.first != name -> return "Linha ${lineOf(m.range.first)}: </$name> fecha <${open.first}> (aberta na linha ${open.second})"
                        else -> stack.removeLast()
                    }
                }
                selfClosing || name in VOID -> Unit
                name == "script" || name == "style" -> {
                    // Conteúdo bruto: pula até o fechamento.
                    val end = clean.indexOf("</$name", pos, ignoreCase = true)
                    if (end < 0) return "Linha ${lineOf(m.range.first)}: <$name> não foi fechada"
                    pos = end
                    stack.addLast(name to lineOf(m.range.first))
                }
                else -> stack.addLast(name to lineOf(m.range.first))
            }
        }
        return stack.lastOrNull()?.let { "Linha ${it.second}: <${it.first}> não foi fechada" }
    }

    /** Posições de todas as ocorrências (sem diferenciar maiúsculas). */
    fun find(text: String, query: String): List<Int> {
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        var i = text.indexOf(query, ignoreCase = true)
        while (i >= 0) {
            out += i
            i = text.indexOf(query, i + query.length.coerceAtLeast(1), ignoreCase = true)
        }
        return out
    }
}
