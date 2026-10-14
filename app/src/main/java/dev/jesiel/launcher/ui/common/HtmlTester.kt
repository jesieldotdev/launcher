package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.data.HtmlRole
import dev.jesiel.launcher.data.HtmlVariables
import dev.jesiel.launcher.data.HtmlWidget
import dev.jesiel.launcher.data.LockSettings
import dev.jesiel.launcher.data.LockStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uma linha do console do testador. */
private data class TestLog(val time: Long, val kind: String, val text: String)

/** Tamanhos de teste para widgets da área de trabalho, em células (colunas × linhas). */
private val TEST_SIZES = listOf(2 to 1, 2 to 2, 4 to 1, 4 to 2, 4 to 3, 4 to 4)

/**
 * Testador de widgets HTML: roda o widget de verdade, sem salvar, no formato escolhido (widget
 * em vários tamanhos, tela de bloqueio ou lista de apps), com JavaScript, links e dados ao vivo.
 * Mostra o console da página, os links tocados e o valor de cada variável que o código usa.
 */
@Composable
fun HtmlTester(widget: HtmlWidget, initialRole: HtmlRole, controller: LauncherController, palette: LauncherPalette) {
    val state by controller.viewModel.state.collectAsStateWithLifecycle()
    var role by remember { mutableStateOf(initialRole) }
    var size by remember { mutableStateOf(4 to 2) }
    var search by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    var runLinks by remember { mutableStateOf(true) }
    // Por padrão o teste salva num espaço próprio, para não mexer nos dados de verdade do widget.
    var realData by remember { mutableStateOf(false) }
    val storageId = if (realData) widget.id else "teste:" + widget.id
    // Recarregar: troca a chave e o WebView é recriado do zero.
    var reload by remember { mutableStateOf(0) }
    val logs = remember { mutableStateListOf<TestLog>() }
    fun log(kind: String, text: String) {
        logs += TestLog(System.currentTimeMillis(), kind, text)
        if (logs.size > 200) logs.removeAt(0)
    }
    fun close() { controller.htmlTesting = null }

    // Os links passam primeiro pelo testador: ficam no console e, se ligado, são executados.
    DisposableEffect(controller) {
        val previous = controller.linkInterceptor
        controller.linkInterceptor = { url ->
            log("link", url)
            when {
                url.startsWith("launcher://fechar") || url.startsWith("launcher://close") -> {
                    controller.toast("A lista de apps fecharia aqui (launcher://fechar)")
                    true
                }
                url.startsWith("launcher://desbloquear") || url.startsWith("launcher://unlock") -> {
                    controller.toast("A tela desbloquearia aqui")
                    true
                }
                // Sem executar: o link só aparece no console.
                !runLinks -> true
                else -> false
            }
        }
        onDispose { controller.linkInterceptor = previous }
    }
    LaunchedEffect(widget.id, reload) {
        logs.clear()
        log("info", "Testando \"${widget.name}\" · ${if (widget.interactive) "interativo (JavaScript)" else "leve (sem JavaScript)"}" +
            if (widget.network) " · com internet" else "")
    }

    val onConsole: (String, String, Int) -> Unit = { level, message, line -> log(level, if (line > 0) "$message (linha $line)" else message) }
    val onStatus: (String) -> Unit = { log("status", it) }
    val extra = remember(search) { mapOf("modoBusca" to if (search) "sim" else "") }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.runtime.key(reload, role, storageId) {
            when (role) {
                HtmlRole.DESKTOP -> BoxWithConstraints(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3A5BA0), Color(0xFF6B4A8C)))),
                    contentAlignment = Alignment.Center,
                ) {
                    // Célula como na área de trabalho: largura da tela em 4 colunas.
                    val cell = (maxWidth - 24.dp) / 4
                    Box(
                        Modifier
                            .size(cell * size.first, cell * size.second)
                            .padding(4.dp),
                    ) { HtmlWidgetView(widget, controller, palette, Modifier.fillMaxSize(), onStatus = onStatus, extra = extra, onConsole = onConsole, storageId = storageId) }
                }
                HtmlRole.DOCK -> Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3A5BA0), Color(0xFF6B4A8C)))),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, bottom = 110.dp)
                            .fillMaxWidth()
                            .height(state.dockHtmlHeight.dp),
                    ) { HtmlWidgetView(widget, controller, palette, Modifier.fillMaxSize(), onStatus = onStatus, extra = extra, onConsole = onConsole, storageId = storageId) }
                }
                HtmlRole.APPS -> Box(Modifier.fillMaxSize().imePadding()) {
                    HtmlWidgetView(widget, controller, palette, Modifier.fillMaxSize(), onStatus = onStatus, extra = extra, onConsole = onConsole, storageId = storageId)
                }
                HtmlRole.LOCK -> LockScreenContent(
                    settings = LockSettings(enabled = true, style = LockStyle.HTML, htmlWidgetId = widget.id),
                    state = state,
                    controller = controller,
                    palette = palette,
                    onUnlock = { log("info", "Desbloqueio (deslizar para cima)"); controller.toast("A tela desbloquearia aqui") },
                    widgetOverride = widget,
                    onConsole = onConsole,
                    storageId = storageId,
                )
            }
        }

        // Barra do testador, embaixo e recolhível, para não cobrir a página.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE6101418)),
        ) {
            if (panel == "data") StoragePanel(storageId, realData, reload) { reload++ }
            else if (panel != null) TesterPanel(panel!!, logs, widget, palette, controller, extra) { logs.clear() }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TesterChip("✕ Sair", false) { close() }
                val errors = logs.count { it.kind == "error" }
                TesterChip(if (errors > 0) "Console · $errors erro${if (errors > 1) "s" else ""}" else "Console (${logs.size})", panel == "console", alert = errors > 0) {
                    panel = if (panel == "console") null else "console"
                }
                TesterChip("Variáveis", panel == "vars") { panel = if (panel == "vars") null else "vars" }
                TesterChip("Dados", panel == "data") { panel = if (panel == "data") null else "data" }
                TesterChip(if (realData) "Dados reais" else "Dados de teste", realData) { realData = !realData }
                TesterChip("↻ Recarregar", false) { reload++ }
                HtmlRole.entries.forEach { r -> TesterChip(r.label, role == r) { role = r } }
                if (role == HtmlRole.DESKTOP) TEST_SIZES.forEach { s -> TesterChip("${s.first}×${s.second}", size == s) { size = s } }
                if (role == HtmlRole.APPS) TesterChip("Aberta pela busca", search) { search = !search; reload++ }
                if (role == HtmlRole.DOCK) {
                    TesterChip("Altura −", false) { controller.viewModel.setDockHtmlHeight(state.dockHtmlHeight - 8) }
                    TesterChip("${state.dockHtmlHeight} dp", false) {}
                    TesterChip("Altura +", false) { controller.viewModel.setDockHtmlHeight(state.dockHtmlHeight + 8) }
                }
                TesterChip(if (runLinks) "Links: executar" else "Links: só registrar", runLinks) { runLinks = !runLinks }
            }
        }
        // Aviso discreto no topo: é um teste, nada foi salvo.
        Text(
            "TESTE · nada é salvo",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCCB3261E))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun TesterPanel(
    panel: String,
    logs: List<TestLog>,
    widget: HtmlWidget,
    palette: LauncherPalette,
    controller: LauncherController,
    extra: Map<String, String>,
    onClear: () -> Unit,
) {
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
        if (panel == "console") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Console da página e links", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TesterChip("Limpar", false, onClick = onClear)
            }
            val listState = rememberLazyListState()
            LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex) }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                if (logs.isEmpty()) item { Text("Nada ainda. console.log() da página aparece aqui.", color = Color.Gray, fontSize = 12.sp) }
                items(logs) { l ->
                    val color = when (l.kind) {
                        "error" -> Color(0xFFFF8A80)
                        "warning" -> Color(0xFFFFD180)
                        "link" -> Color(0xFF80D8FF)
                        "status", "info" -> Color(0xFFB0BEC5)
                        else -> Color(0xFFE0E0E0)
                    }
                    val prefix = when (l.kind) { "link" -> "→ "; "error" -> "✖ "; "warning" -> "⚠ "; else -> "" }
                    Text(
                        "${time.format(Date(l.time))}  $prefix${l.text}",
                        color = color,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        } else {
            // Valores atuais das variáveis que o código usa.
            val used = remember(widget.html) { HtmlVariables.used(widget.html) }
            val values = rememberHtmlValues(palette, used, controller, extra)
            Text("Variáveis usadas (${used.size})", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                if (used.isEmpty()) item { Text("O código não usa nenhuma variável.", color = Color.Gray, fontSize = 12.sp) }
                items(used.sorted()) { k ->
                    val v = values[k].orEmpty()
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(k, color = Color(0xFF80D8FF), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.38f))
                        Text(
                            when {
                                v.isEmpty() -> "(vazio)"
                                v.startsWith("data:") -> "imagem (${v.length / 1024} KB)"
                                v.startsWith("[") -> "JSON · ${v.length} caracteres"
                                else -> v.replace('\n', '⏎')
                            },
                            color = if (v.isEmpty()) Color.Gray else Color(0xFFE0E0E0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.62f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TesterChip(text: String, selected: Boolean, alert: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = if (selected) Color(0xFF0B1A2A) else if (alert) Color(0xFFFF8A80) else Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF8AB4F8) else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

/** O que o widget salvou no localStorage (no espaço do teste ou no de verdade). */
@Composable
private fun StoragePanel(storageId: String, real: Boolean, reload: Int, onCleared: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableStateOf(0) }
    // Relê de tempos em tempos: a página pode salvar a qualquer momento.
    LaunchedEffect(storageId, reload) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            tick++
        }
    }
    val entries = remember(storageId, tick) { dev.jesiel.launcher.data.WidgetStorage.entries(context, storageId) }
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "localStorage · ${if (real) "dados reais do widget" else "espaço do teste"} (${entries.size})",
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            TesterChip("Apagar", false) {
                dev.jesiel.launcher.data.WidgetStorage.clear(context, storageId)
                tick++
                onCleared()
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
            if (entries.isEmpty()) item { Text("Nada salvo. Use localStorage.setItem(\"chave\", \"valor\") no código.", color = Color.Gray, fontSize = 12.sp) }
            items(entries.entries.toList()) { (k, v) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(k, color = Color(0xFF80D8FF), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.38f))
                    Text(v.replace('\n', '⏎'), color = Color(0xFFE0E0E0), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.62f))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
