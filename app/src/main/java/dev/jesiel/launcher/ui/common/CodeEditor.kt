package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.data.CodeTools

/** Texto do editor com histórico para desfazer/refazer e os ajustes automáticos de [CodeTools]. */
@Stable
class CodeEditorState(initial: String) {
    var value by mutableStateOf(TextFieldValue(initial))
        private set
    val text: String get() = value.text

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastPush = 0L
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private fun pushHistory(previous: TextFieldValue, force: Boolean) {
        val now = System.currentTimeMillis()
        // Agrupa a digitação contínua: um passo de desfazer a cada pausa, palavra ou linha.
        if (force || now - lastPush > 700 || undoStack.isEmpty()) {
            undoStack.addLast(previous)
            if (undoStack.size > 200) undoStack.removeFirst()
        }
        lastPush = now
        redoStack.clear()
        canUndo = undoStack.isNotEmpty()
        canRedo = false
    }

    fun onChange(new: TextFieldValue) {
        if (new.text == value.text) {
            value = new
            return
        }
        val cursor = new.selection.end
        val typedSpace = new.text.length == value.text.length + 1 && cursor > 0 && new.text[cursor - 1].isWhitespace()
        pushHistory(value, force = typedSpace)
        val auto = if (new.selection.collapsed) CodeTools.autoEdit(value.text, new.text, cursor) else null
        value = if (auto != null) TextFieldValue(auto.text, TextRange(auto.cursor)) else new
    }

    /** Troca o texto inteiro (modelo, arquivo importado), mantendo o desfazer. */
    fun set(text: String) {
        pushHistory(value, force = true)
        value = TextFieldValue(text, TextRange(0))
    }

    /** Insere no cursor (ou no lugar da seleção); [cursorBack] recua o cursor, ex.: para dentro de "{{}}". */
    fun insert(piece: String, cursorBack: Int = 0) {
        pushHistory(value, force = true)
        val s = value.selection
        val t = value.text.replaceRange(s.min, s.max, piece)
        value = TextFieldValue(t, TextRange(s.min + piece.length - cursorBack))
    }

    fun select(start: Int, end: Int) {
        value = value.copy(selection = TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length)))
    }

    fun replaceSelection(replacement: String) {
        if (value.selection.collapsed) return
        insert(replacement)
    }

    fun replaceAll(query: String, replacement: String): Int {
        val hits = CodeTools.find(text, query)
        if (hits.isEmpty()) return 0
        pushHistory(value, force = true)
        val sb = StringBuilder(text)
        hits.asReversed().forEach { sb.replace(it, it + query.length, replacement) }
        value = TextFieldValue(sb.toString(), TextRange(hits.first()))
        return hits.size
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(value)
        value = prev
        canUndo = undoStack.isNotEmpty()
        canRedo = true
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(value)
        value = next
        canUndo = true
        canRedo = redoStack.isNotEmpty()
    }
}

/** Cores do destaque de sintaxe, para editor escuro ou claro. */
private class SyntaxColors(dark: Boolean) {
    val background = if (dark) Color(0xFF1E2127) else Color(0xFFFAFAFB)
    val text = if (dark) Color(0xFFDCDFE4) else Color(0xFF24292F)
    val gutter = if (dark) Color(0xFF5C6370) else Color(0xFFA0A4AB)
    val gutterLine = if (dark) Color(0xFF2C313A) else Color(0xFFE4E6EA)
    val styles: Map<CodeTools.Kind, SpanStyle> = if (dark) mapOf(
        CodeTools.Kind.TAG to SpanStyle(color = Color(0xFFE06C75)),
        CodeTools.Kind.ATTR to SpanStyle(color = Color(0xFFD19A66)),
        CodeTools.Kind.STRING to SpanStyle(color = Color(0xFF98C379)),
        CodeTools.Kind.VAR to SpanStyle(color = Color(0xFF56B6C2), fontWeight = FontWeight.Bold, background = Color(0x2256B6C2)),
        CodeTools.Kind.COMMENT to SpanStyle(color = Color(0xFF7F848E), fontStyle = FontStyle.Italic),
        CodeTools.Kind.PROPERTY to SpanStyle(color = Color(0xFF61AFEF)),
        CodeTools.Kind.NUMBER to SpanStyle(color = Color(0xFFD19A66)),
        CodeTools.Kind.KEYWORD to SpanStyle(color = Color(0xFFC678DD)),
    ) else mapOf(
        CodeTools.Kind.TAG to SpanStyle(color = Color(0xFFB4232F)),
        CodeTools.Kind.ATTR to SpanStyle(color = Color(0xFF9A5200)),
        CodeTools.Kind.STRING to SpanStyle(color = Color(0xFF2E7D32)),
        CodeTools.Kind.VAR to SpanStyle(color = Color(0xFF00727F), fontWeight = FontWeight.Bold, background = Color(0x1A00838F)),
        CodeTools.Kind.COMMENT to SpanStyle(color = Color(0xFF8A8F98), fontStyle = FontStyle.Italic),
        CodeTools.Kind.PROPERTY to SpanStyle(color = Color(0xFF1558B0)),
        CodeTools.Kind.NUMBER to SpanStyle(color = Color(0xFF9A5200)),
        CodeTools.Kind.KEYWORD to SpanStyle(color = Color(0xFF7B1FA2)),
    )
}

/** Pinta o texto sem mudar o tamanho (o cursor e a seleção continuam nas mesmas posições). */
private class SyntaxHighlight(private val colors: SyntaxColors) : VisualTransformation {
    private var lastText: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = if (raw == lastText) lastResult!! else AnnotatedString.Builder(raw).apply {
            CodeTools.highlight(raw).forEach { s -> colors.styles[s.kind]?.let { addStyle(it, s.start, s.end) } }
        }.toAnnotatedString().also {
            lastText = raw
            lastResult = it
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Editor de código com destaque de sintaxe e números de linha. */
@Composable
fun CodeEditor(
    state: CodeEditorState,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: (Boolean) -> Unit = {},
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = remember(dark) { SyntaxColors(dark) }
    val highlight = remember(colors) { SyntaxHighlight(colors) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val measurer = rememberTextMeasurer()
    val numberStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize * 0.85f, color = colors.gutter)
    val lines = remember(state.text) { state.text.count { it == '\n' } + 1 }
    val gutter = (14 + 8 * lines.toString().length).dp
    val pad = 8.dp

    BasicTextField(
        value = state.value,
        onValueChange = state::onChange,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize, lineHeight = fontSize * 1.45f, color = colors.text),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = highlight,
        onTextLayout = { layout = it },
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.background)
            .border(1.dp, colors.gutterLine, RoundedCornerShape(10.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { onFocus(it.isFocused) },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val gw = gutter.toPx()
                        drawRect(colors.gutterLine, topLeft = Offset(gw - 1.dp.toPx(), 0f), size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height))
                        val l = layout ?: return@drawBehind
                        // Um número por linha do código, na altura da primeira linha visual dela.
                        val text = state.text
                        var start = 0
                        var n = 1
                        val top = pad.toPx()
                        while (start <= text.length) {
                            val visual = l.getLineForOffset(start.coerceAtMost(l.layoutInput.text.length))
                            val y = top + l.getLineTop(visual)
                            if (y > size.height) break
                            val label = measurer.measure(n.toString(), numberStyle)
                            drawText(label, topLeft = Offset(gw - 6.dp.toPx() - label.size.width, y + (l.getLineBottom(visual) - l.getLineTop(visual) - label.size.height) / 2f))
                            val next = text.indexOf('\n', start)
                            if (next < 0) break
                            start = next + 1
                            n++
                        }
                    }
                    .padding(start = gutter + 8.dp, top = pad, bottom = pad, end = 10.dp),
            ) { inner() }
        },
    )
}

/** Barra de símbolos difíceis de digitar no teclado do celular, com desfazer/refazer e busca. */
@Composable
fun CodeToolbar(state: CodeEditorState, onFind: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    @Composable
    fun Key(label: String, enabled: Boolean = true, onClick: () -> Unit) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) c.onSurface else c.onSurface.copy(alpha = 0.3f),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(c.onSurface.copy(alpha = 0.07f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Key("↶", state.canUndo) { state.undo() }
        Key("↷", state.canRedo) { state.redo() }
        Key("⇥") { state.insert("  ") }
        Key("🔍", onClick = onFind)
        Key("{{ }}") { state.insert("{{}}", cursorBack = 2) }
        listOf("<", ">", "/", "=", "\"", "'", "{", "}", "(", ")", ":", ";", "#", ".", "-", "%").forEach { k -> Key(k) { state.insert(k) } }
        Key("</>") { state.insert("</>", cursorBack = 1) }
        Key("var()") { state.insert("var(--)", cursorBack = 1) }
    }
}

/** Buscar e substituir no código. */
@Composable
fun FindBar(state: CodeEditorState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var index by remember { mutableStateOf(0) }
    val hits = remember(state.text, query) { CodeTools.find(state.text, query) }
    fun go(i: Int) {
        if (hits.isEmpty()) return
        index = ((i % hits.size) + hits.size) % hits.size
        state.select(hits[index], hits[index] + query.length)
    }
    @Composable
    fun Field(value: String, hint: String, onChange: (String) -> Unit, m: Modifier) {
        Box(m.clip(RoundedCornerShape(8.dp)).background(c.onSurface.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = c.onSurface),
                cursorBrush = SolidColor(c.primary), modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = c.onSurface.copy(alpha = 0.45f))
        }
    }
    @Composable
    fun Action(label: String, onClick: () -> Unit) {
        Text(
            label, fontSize = 13.sp, color = c.primary, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
    Column(modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(query, "Buscar", { query = it; index = 0 }, Modifier.weight(1f))
            Text(
                if (query.isEmpty()) "" else if (hits.isEmpty()) "0" else "${index + 1}/${hits.size}",
                fontSize = 12.sp, color = c.onSurface.copy(alpha = 0.6f), modifier = Modifier.width(48.dp).padding(start = 8.dp),
            )
            Action("▲") { go(index - 1) }
            Action("▼") { go(index + 1) }
            Action("✕", onClose)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(replacement, "Substituir por", { replacement = it }, Modifier.weight(1f))
            Action("Trocar") {
                val sel = state.value.selection
                if (hits.isNotEmpty() && !sel.collapsed && state.text.substring(sel.min, sel.max).equals(query, ignoreCase = true)) {
                    state.replaceSelection(replacement)
                }
                go(index)
            }
            Action("Todas") { state.replaceAll(query, replacement) }
        }
    }
}

/** Altura mínima do editor fora da tela cheia. */
val CodeEditorMinHeight = Modifier.heightIn(min = 220.dp)
