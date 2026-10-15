package dev.jesiel.launcher.ui.retro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.ui.common.AppListRow
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.asFixedSp
import dev.jesiel.launcher.ui.common.glass
import dev.jesiel.launcher.ui.common.initialOf
import dev.jesiel.launcher.ui.common.rememberBatteryState
import dev.jesiel.launcher.ui.common.rememberMemoryState
import dev.jesiel.launcher.ui.common.rememberNow
import dev.jesiel.launcher.ui.common.search
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Hud = TextStyle(fontFamily = Orbitron, fontSize = 10.5.sp, letterSpacing = 0.12.em, color = Amber)

/** Pisca a cada meio segundo (cursor de terminal, luz de gravação). */
@Composable
private fun rememberBlink(periodMs: Long = 530): Boolean {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            on = !on
        }
    }
    return on
}

/** Barra HUD no topo: indicativo, hora, bateria, memória e luz de sistema. Toque abre o painel. */
@Composable
fun RetroHud(state: LauncherUiState, controller: LauncherController, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val battery = rememberBatteryState()
    val memory = rememberMemoryState()
    val blink = rememberBlink(900)
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val shape = CutCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 0.dp, bottomStart = 12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .glass(Color(0xB30B0805), shape, AmberLine)
            .scanlines()
            .clickable { controller.controlCenterOpen = true }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Box(Modifier.size(9.dp, 9.dp).background(Amber))
        Spacer(Modifier.width(8.dp))
        Text("NAV-01", style = Hud.copy(fontWeight = FontWeight.Bold))
        Sep()
        Text(time, style = Hud.copy(color = AmberBright, fontWeight = FontWeight.Bold))
        Sep()
        Text("BAT ${battery?.percent ?: "--"}%", style = Hud)
        Sep()
        Text("MEM ${memory?.usedPercent ?: "--"}%", style = Hud, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (blink) Color(0xFFFF7A4A) else Color(0x33FF7A4A)))
        Spacer(Modifier.width(6.dp))
        Text("SYS", style = Hud.copy(color = AmberDim))
    }
}

@Composable
private fun Sep() {
    Text(" │ ", style = Hud.copy(color = AmberLine), maxLines = 1)
}

/** Botão principal do console: abre a lista de programas. */
@Composable
fun ExecButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(44.dp)
            .clip(CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
            .background(Brush.verticalGradient(listOf(AmberBright, Color(0xFFD9A85A))))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Programas" }
            .padding(horizontal = 12.dp),
    ) {
        Text("▶ EXEC", style = Hud.copy(color = Ink, fontWeight = FontWeight.Black, fontSize = 12.sp))
    }
}

/** Prompt piscando no fim do console: abre a busca. */
@Composable
fun PromptButton(onClick: () -> Unit) {
    val blink = rememberBlink()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(44.dp)
            .clip(CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp))
            .border(1.dp, AmberLine, CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(">", style = Hud.copy(color = AmberBright, fontWeight = FontWeight.Bold, fontSize = 14.sp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(8.dp, 14.dp).background(if (blink) Amber else Color.Transparent))
    }
}

/** Log de bordo: os últimos apps abertos como linhas de terminal; tocar reabre o app. */
@Composable
fun RetroLogCard(state: LauncherUiState, controller: LauncherController, palette: LauncherPalette, modifier: Modifier = Modifier) {
    val blink = rememberBlink()
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    RetroFrame(palette, modifier, onClick = {}) { w, h ->
        val small = minOf(h * 0.085f, w * 0.032f).coerceAtLeast(9.dp)
        Column(Modifier.fillMaxSize()) {
            Header("Log de bordo", "tty_03", small)
            Divider(small * 0.6f)
            val lines = state.recents.take(((h / (small * 2f)).toInt() - 2).coerceIn(1, 8))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(small * 0.45f)) {
                if (lines.isEmpty()) {
                    Text("> nenhum programa executado", color = AmberDim, fontSize = small.asFixedSp(), maxLines = 1)
                }
                lines.forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { controller.launch(r.app) },
                    ) {
                        Text(fmt.format(Date(r.launchedAt)), color = AmberLine, fontSize = small.asFixedSp(), maxLines = 1)
                        Text("  > exec ", color = AmberDim, fontSize = small.asFixedSp(), maxLines = 1)
                        Text(
                            r.app.label.lowercase(), color = Amber, fontSize = small.asFixedSp(), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text("[ OK ]", color = AmberBright, fontSize = small.asFixedSp(), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("> ", color = AmberBright, fontSize = small.asFixedSp())
                    Box(Modifier.size(small * 0.6f, small).background(if (blink) Amber else Color.Transparent))
                }
            }
        }
    }
}

/**
 * Lista de programas em forma de terminal: prompt de busca com cursor, linhas numeradas e
 * separadores de letra. Abre "ligando" como um tubo (expande do centro).
 */
@Composable
fun TerminalDrawer(
    visible: Boolean,
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    focusSearch: Boolean,
    onClose: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onClose)
    AnimatedVisibility(
        visible,
        enter = expandVertically(tween(220), expandFrom = Alignment.CenterVertically) + fadeIn(tween(120)),
        exit = shrinkVertically(tween(160), shrinkTowards = Alignment.CenterVertically) + fadeOut(tween(160)),
    ) {
        TerminalContent(state, controller, palette, focusSearch, onClose)
    }
}

@Composable
private fun TerminalContent(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    focusSearch: Boolean,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val blink = rememberBlink()
    fun close() {
        query = ""
        keyboard?.hide()
        onClose()
    }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }
    val apps = remember(state.apps, query) { if (query.isBlank()) state.apps else state.apps.search(query) }
    val rows = remember(apps, state.layout.showLetters, query) {
        // Cada letra entra uma vez só: com acentos, a ordem do Collator pode intercalar iniciais,
        // e uma chave repetida derruba a lista.
        val seen = HashSet<Char>()
        buildList<Any> {
            apps.forEachIndexed { i, app ->
                val letter = initialOf(app.label)
                if (state.layout.showLetters && query.isBlank() && seen.add(letter)) add(letter)
                add(i to app)
            }
        }
    }
    val listState = rememberLazyListState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF5080604))
            .scanlines(Color(0x0DFFC878))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp, 8.dp).background(Amber))
            Spacer(Modifier.width(8.dp))
            Text("/SYS/PROGRAMAS", style = Hud.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("${apps.size} ENTRADAS", style = Hud.copy(color = AmberDim))
            Spacer(Modifier.width(12.dp))
            Text(
                "[ESC]",
                style = Hud.copy(color = AmberBright, fontWeight = FontWeight.Bold),
                modifier = Modifier.clickable { close() }.padding(4.dp),
            )
        }
        Divider(8.dp)
        // Prompt de busca: no topo ou embaixo (Personalizar › Busca embaixo).
        val promptBar: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AmberLine, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                .clickable { runCatching { focus.requestFocus() } }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("root@nav-01:~$ ", style = Hud.copy(color = AmberDim, fontSize = 13.sp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = Hud.copy(color = AmberBright, fontSize = 14.sp),
                    cursorBrush = SolidColor(Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val best = apps.firstOrNull()
                        if (best != null) controller.launch(best) else controller.webSearch(query)
                        close()
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (query.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("buscar", style = Hud.copy(color = AmberLine, fontSize = 14.sp))
                        Box(Modifier.padding(start = 3.dp).size(8.dp, 15.dp).background(if (blink) Amber else Color.Transparent))
                    }
                }
            }
        }
        }
        val bottomSearch = state.layout.searchAtBottom
        if (!bottomSearch) promptBar()
        Spacer(Modifier.height(6.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(rows, key = { r -> if (r is Char) "h-$r" else (r as Pair<*, *>).let { "a-${it.first}-" + (it.second as dev.jesiel.launcher.data.AppInfo).key } }, contentType = { if (it is Char) "letra" else "app" }) { r ->
                if (r is Char) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                        Text("── $r ", style = Hud.copy(color = AmberBright, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                        Box(Modifier.weight(1f).height(1.dp).background(AmberLine))
                    }
                } else {
                    val pair = r as Pair<*, *>
                    val i = pair.first as Int
                    val app = pair.second as dev.jesiel.launcher.data.AppInfo
                    androidx.compose.runtime.CompositionLocalProvider(dev.jesiel.launcher.ui.common.LocalDragToHome provides true) {
                    AppListRow(
                        app = app, state = state, controller = controller, labels = RetroLabels,
                        iconSize = (state.layout.drawerIconSizeDp * 0.6f).coerceAtLeast(22f).dp,
                        onLaunched = { close() },
                    ) {
                        Text("%03d".format(i + 1), style = Hud.copy(color = AmberLine, fontSize = 12.sp))
                        Text(
                            app.label.uppercase(), style = Hud.copy(color = Amber, fontSize = 13.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text("▸", style = Hud.copy(color = AmberDim, fontSize = 13.sp))
                    }
                    }
                }
            }
        }
        if (bottomSearch) {
            Spacer(Modifier.height(6.dp))
            promptBar()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Tela de tubo por cima de tudo: vinheta nas bordas e linhas de varredura. Não captura toques. */
@Composable
fun CrtOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent, 1f to Color(0x8C000000),
                center = Offset(size.width / 2f, size.height / 2f), radius = size.maxDimension * 0.62f,
            ),
        )
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(Color(0x0F000000), Offset(0f, y), Size(size.width, 1f))
            y += step
        }
    }
}

/** Sequência de inicialização (uma vez por abertura do Launcher), em menos de um segundo. */
@Composable
fun BootSequence() {
    var done by rememberSaveable { mutableStateOf(false) }
    if (done) return
    val lines = remember {
        listOf("NAV-01 BIOS v2.6", "> verificando memória ........ OK", "> carregando programas ...... OK", "> link de dados .............. OK", "SISTEMA ONLINE")
    }
    var shown by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (shown < lines.size) {
            delay(110)
            shown++
        }
        delay(180)
        alpha.animateTo(0f, tween(260))
        done = true
    }
    Box(
        Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .background(Color(0xFF080604))
            .scanlines(Color(0x14FFC878))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.take(shown).forEachIndexed { i, l ->
                Text(
                    l,
                    style = Hud.copy(
                        color = if (i == lines.lastIndex) AmberBright else Amber,
                        fontSize = if (i == lines.lastIndex) 18.sp else 12.sp,
                        fontWeight = if (i == lines.lastIndex) FontWeight.Black else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}
