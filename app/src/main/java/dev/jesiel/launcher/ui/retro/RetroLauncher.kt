package dev.jesiel.launcher.ui.retro

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.R
import dev.jesiel.launcher.data.CellSize
import dev.jesiel.launcher.ui.common.AppDock
import dev.jesiel.launcher.ui.common.AppMenuLabels
import dev.jesiel.launcher.ui.common.BuiltinSpec
import dev.jesiel.launcher.ui.common.ControlCenter
import dev.jesiel.launcher.ui.common.DesktopCanvas
import dev.jesiel.launcher.ui.common.IconStyle
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.ThemeRoot
import dev.jesiel.launcher.ui.common.asFixedSp
import dev.jesiel.launcher.ui.common.backdrop
import dev.jesiel.launcher.ui.common.glass
import dev.jesiel.launcher.ui.common.isDark
import dev.jesiel.launcher.ui.common.rememberBatteryState
import dev.jesiel.launcher.ui.common.rememberMemoryState
import dev.jesiel.launcher.ui.common.rememberNow
import dev.jesiel.launcher.ui.common.withTransparency
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Orbitron (SIL Open Font License), embutida em res/font. */
val Orbitron = FontFamily(
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_black, FontWeight.Black),
)

internal val Amber = Color(0xFFE9C27A)
internal val AmberBright = Color(0xFFFFD98C)
internal val AmberDim = Color(0xFFC9A464)
internal val AmberLine = Color(0xFF7D6136)
internal val Ink = Color(0xFF0B0805)

/** Corta cantos opostos, como as placas de um painel de nave. */
internal val PlateShape = CutCornerShape(topStart = 18.dp, topEnd = 3.dp, bottomEnd = 18.dp, bottomStart = 3.dp)

/** Ícones em fósforo âmbar: tons de cinza do ícone original pintados de âmbar, com mais contraste. */
private val PhosphorFilter = run {
    val k = 1.2f
    fun row(scale: Float, bias: Float) = floatArrayOf(0.299f * k * scale, 0.587f * k * scale, 0.114f * k * scale, 0f, bias)
    ColorFilter.colorMatrix(
        ColorMatrix(row(1.05f, 18f) + row(0.80f, 6f) + row(0.42f, 0f) + floatArrayOf(0f, 0f, 0f, 1f, 0f)),
    )
}

fun retroPalette(dark: Boolean) = LauncherPalette(
    dark = true,
    accent = Amber,
    onAccent = Ink,
    onWallpaper = Color(0xFFF0D49A),
    card = if (dark) Color(0x99100D07) else Color(0x73140F08),
    cardStroke = AmberLine,
    cardText = Amber,
    cardTextSecondary = AmberDim,
    cardShape = PlateShape,
    panel = Color(0xF20E0B06),
    panelStroke = AmberLine,
    panelText = Color(0xFFF0D49A),
    panelTextSecondary = AmberDim,
    panelShape = CutCornerShape(14.dp),
    tile = Color(0x26E9C27A),
    field = Color(0xFF1A140B),
    dock = if (dark) Color(0xCC0E0B06) else Color(0x99140F08),
    dockStroke = AmberLine,
    folder = Color(0x33E9C27A),
    font = Orbitron,
    iconStyle = IconStyle(filter = PhosphorFilter, brackets = Amber.copy(alpha = 0.75f), uppercaseLabels = true),
)

internal val RetroLabels = AppMenuLabels(pinToDock = "Fixar no console", unpinFromDock = "Tirar do console")

/**
 * Tema Retrô futurista: a tela vira o painel de uma nave. Barra HUD no topo, ícones em fósforo
 * âmbar com cantoneiras de mira, placas de cantos cortados, console de comando no lugar do dock,
 * gaveta de apps em forma de terminal, tela de tubo (linhas e vinheta) e grade em movimento.
 */
@Composable
fun RetroLauncher(state: LauncherUiState, controller: LauncherController) {
    val dark = isDark(state.colorMode)
    val palette = remember(dark, state.transparency) { retroPalette(dark).withTransparency(state.transparency) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var focusSearch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controller.homePresses) { drawerOpen = false }

    fun openDrawer(search: Boolean) {
        focusSearch = search
        controller.controlCenterOpen = false
        drawerOpen = true
    }

    val current by rememberUpdatedState(state)
    val builtins = remember(palette) {
        listOf(
            BuiltinSpec("clock", "Relógio de bordo", { c -> CellSize(c.toFloat(), 2.2f) }) { RetroClockCard(palette, it) },
            BuiltinSpec("log", "Log de bordo", { c -> CellSize(c.toFloat(), 1.6f) }) { RetroLogCard(current, controller, palette, it) },
            BuiltinSpec("monitor", "Monitor do sistema", { c -> CellSize(c.toFloat(), 1.4f) }) { RetroMonitorCard(palette, it) },
        )
    }

    ThemeRoot(controller, palette) {
        Box(Modifier.fillMaxSize().backdrop(controller, drawerOpen || controller.controlCenterOpen, state.transparency)) {
            if (state.layout.useThemeWallpaper) RetroWallpaper(Modifier.fillMaxSize(), moving = !drawerOpen)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (state.layout.showTray) RetroHud(state, controller, Modifier.padding(horizontal = 12.dp).padding(top = 6.dp))
                DesktopCanvas(
                    state = state,
                    controller = controller,
                    palette = palette,
                    builtins = builtins,
                    labels = RetroLabels,
                    onSwipeUp = { openDrawer(search = false) },
                    onSearch = { openDrawer(search = true) },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                )
                if (state.layout.showDock) {
                    AppDock(
                        state = state,
                        controller = controller,
                        palette = palette,
                        iconSize = 40.dp,
                        shape = PlateShape,
                        labels = RetroLabels,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        leading = { ExecButton { openDrawer(search = false) } },
                        trailing = { PromptButton { openDrawer(search = true) } },
                    )
                }
            }
        }
        TerminalDrawer(
            visible = drawerOpen,
            state = state,
            controller = controller,
            palette = palette,
            focusSearch = focusSearch,
            onClose = { drawerOpen = false },
        )
        ControlCenter(controller, palette)
        CrtOverlay(Modifier.fillMaxSize())
        BootSequence()
    }
}

/** Faixas horizontais finas por cima do conteúdo, como um monitor de tubo. */
internal fun Modifier.scanlines(color: Color = Color(0x09FFC878)): Modifier = drawWithContent {
    drawContent()
    val step = 4.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(color, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f))
        y += step
    }
}

/** Texto âmbar listrado com brilho, como letreiro de fósforo. */
@Composable
internal fun phosphor(size: Dp, color: Color = AmberBright): TextStyle {
    val stripe = with(LocalDensity.current) { (size * 0.075f).coerceAtLeast(2.dp).toPx() }
    return TextStyle(
        brush = Brush.linearGradient(
            0f to color, 0.62f to color, 0.62f to color.copy(alpha = 0.45f), 1f to color.copy(alpha = 0.45f),
            start = Offset(0f, 0f), end = Offset(0f, stripe), tileMode = TileMode.Repeated,
        ),
        shadow = Shadow(Color(0x99FFB43C), Offset.Zero, blurRadius = with(LocalDensity.current) { (size * 0.12f).toPx() }),
        fontFamily = Orbitron,
        fontWeight = FontWeight.Black,
        fontSize = size.asFixedSp(),
        letterSpacing = 0.02.em,
    )
}

@Composable
internal fun RetroFrame(palette: LauncherPalette, modifier: Modifier, onClick: () -> Unit, content: @Composable (maxW: Dp, maxH: Dp) -> Unit) {
    BoxWithConstraints(
        modifier
            .glass(palette.card, palette.cardShape, palette.cardStroke)
            .scanlines()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { content(maxWidth, maxHeight) }
}

@Composable
internal fun Header(left: String, right: String, size: Dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size * 0.75f, size * 0.6f).background(Amber))
        Spacer(Modifier.width(size * 0.55f))
        Text(left.uppercase(), color = Amber, fontSize = size.asFixedSp(), letterSpacing = 0.12.em, maxLines = 1, modifier = Modifier.weight(1f))
        Text(right.uppercase(), color = Amber, fontSize = size.asFixedSp(), letterSpacing = 0.1.em, maxLines = 1)
    }
}

@Composable
internal fun Divider(vertical: Dp) {
    Box(Modifier.padding(vertical = vertical).fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color(0xFF6B5330), Color(0xFF4A3A22)))))
}

/** Relógio de bordo: hora em fósforo âmbar, saudação, data e bateria. */
@Composable
fun RetroClockCard(palette: LauncherPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow()
    val battery = rememberBatteryState()
    val locale = remember { Locale.getDefault() }
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val time = remember(now) {
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(Date(now))
    }
    val date = remember(now) { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(Date(now)) }
    val greeting = when (cal.get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    RetroFrame(palette, modifier, onClick = {
        runCatching { context.startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) { w, h ->
        val small = minOf(h * 0.075f, w * 0.036f).coerceAtLeast(9.dp)
        val big = minOf(h * 0.36f, w * 0.2f)
        Column(Modifier.fillMaxSize()) {
            Header("Local time", "${cal.get(Calendar.YEAR)} / clock_01", small)
            Divider(small * 0.7f)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(time, style = phosphor(big), maxLines = 1)
            }
            Row(Modifier.fillMaxWidth()) {
                Text(greeting, color = Amber, fontSize = (small * 1.15f).asFixedSp(), maxLines = 1, modifier = Modifier.weight(1f))
                Text(date, color = Amber, fontSize = (small * 1.15f).asFixedSp(), maxLines = 1)
            }
            Divider(small * 0.7f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(small * 0.8f).clip(CircleShape).background(AmberDim))
                Spacer(Modifier.width(small * 0.6f))
                Text("SISTEMA ONLINE", color = AmberDim, fontSize = small.asFixedSp(), letterSpacing = 0.1.em, maxLines = 1, modifier = Modifier.weight(1f))
                val pct = battery?.percent ?: 0
                Text("BAT $pct%", color = AmberDim, fontSize = small.asFixedSp(), letterSpacing = 0.1.em, maxLines = 1)
                Spacer(Modifier.width(small * 0.8f))
                SegmentBar(pct / 100f, Modifier.width(small * 7f).height(small * 0.8f), segments = 1)
            }
        }
    }
}

/** Monitor com barras segmentadas de memória, armazenamento e bateria. */
@Composable
fun RetroMonitorCard(palette: LauncherPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val battery = rememberBatteryState()
    val memory = rememberMemoryState()
    RetroFrame(palette, modifier, onClick = {
        runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) { w, h ->
        val small = minOf(h * 0.12f, w * 0.034f).coerceAtLeast(9.dp)
        val storageUsed = memory?.let { 1f - it.storageFreeGb / it.storageTotalGb.coerceAtLeast(1f) } ?: 0f
        val rows = listOf(
            Triple("MEM", (memory?.usedPercent ?: 0) / 100f, "${memory?.usedPercent ?: "--"}%"),
            Triple("ARMAZ", storageUsed, memory?.let { "%.0f GB livres".format(it.storageFreeGb) } ?: "--"),
            Triple("BAT", (battery?.percent ?: 0) / 100f, "${battery?.percent ?: "--"}%" + if (battery?.charging == true) " ⚡" else ""),
        )
        Column(Modifier.fillMaxSize()) {
            Header("System monitor", "sys_02", small)
            Divider(small * 0.6f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                rows.forEach { (label, value, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = AmberDim, fontSize = small.asFixedSp(), letterSpacing = 0.1.em, maxLines = 1, modifier = Modifier.width(small * 5.5f))
                        SegmentBar(value, Modifier.weight(1f).height(small * 0.9f))
                        Text(
                            text, color = Amber, fontSize = small.asFixedSp(), maxLines = 1, overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(start = small * 0.8f).width(small * 9f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SegmentBar(value: Float, modifier: Modifier, segments: Int = 20) {
    Canvas(modifier) {
        val v = value.coerceIn(0f, 1f)
        drawRect(AmberLine, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        if (segments <= 1) {
            drawRect(Amber, topLeft = Offset(2f, 2f), size = androidx.compose.ui.geometry.Size((size.width - 4f) * v, size.height - 4f))
            return@Canvas
        }
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (segments + 1)) / segments
        val on = (v * segments).toInt()
        for (i in 0 until segments) {
            val color = if (i < on) (if (v > 0.85f && i >= segments - 3) Color(0xFFFF8A5A) else Amber) else Color(0x22E9C27A)
            drawRect(color, Offset(gap + i * (segW + gap), gap), androidx.compose.ui.geometry.Size(segW, size.height - gap * 2))
        }
    }
}

/** Céu escuro, sol âmbar listrado no horizonte e grade em perspectiva. */
@Composable
fun RetroWallpaper(modifier: Modifier = Modifier, moving: Boolean = true) {
    // A grade anda em direção a quem olha. ~15 quadros/s bastam e gastam pouco; o laço para
    // quando a tela inicial sai de cena (o Compose deixa de desenhar) ou a gaveta está aberta.
    val phase = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    if (moving) LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(66)
            phase.floatValue = (phase.floatValue + 0.02f) % 1f
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF07060A), Color(0xFF1A1208), Color(0xFF3A230C)), endY = horizon))
        // Estrelas fixas (posições determinísticas).
        val rnd = java.util.Random(7)
        repeat(70) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * horizon * 0.85f
            drawCircle(Color(0xFFFFE6B0), radius = 0.6f + rnd.nextFloat() * 1.6f, center = Offset(x, y), alpha = 0.25f + rnd.nextFloat() * 0.5f)
        }
        // Sol listrado.
        val r = w * 0.26f
        val center = Offset(w / 2f, horizon - r * 0.45f)
        drawCircle(Brush.radialGradient(listOf(Color(0x55FFB43C), Color.Transparent), center = center, radius = r * 1.9f), r * 1.9f, center)
        val sun = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
        clipPath(sun) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF0B8), Color(0xFFFFC65A), Color(0xFFE07A2A)), startY = center.y - r, endY = center.y + r))
            var y = center.y - r * 0.2f
            var gap = r * 0.035f
            while (y < center.y + r) {
                drawRect(Color(0xFF1A1208), Offset(center.x - r, y), androidx.compose.ui.geometry.Size(r * 2, gap))
                y += gap + r * 0.1f
                gap *= 1.3f
            }
        }
        // Chão com grade em perspectiva.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF140D05), Color(0xFF070504)), startY = horizon), Offset(0f, horizon), androidx.compose.ui.geometry.Size(w, h - horizon))
        val line = Color(0xFFE9A84A)
        val vanish = Offset(w / 2f, horizon)
        for (i in -12..12) {
            val x = w / 2f + i * w * 0.16f
            drawLine(line.copy(alpha = 0.35f), vanish, Offset(x * 1f + (x - w / 2f) * 2.2f, h), strokeWidth = 1.5f)
        }
        // Linhas transversais em perspectiva: na profundidade z ficam em horizonte + altura / z.
        val p = phase.floatValue
        for (k in 0..18) {
            val z = 1f + (k + 1 - p) * 0.9f
            val t = 1f / z
            val y = horizon + (h - horizon) * t
            drawLine(line.copy(alpha = (0.1f + 0.55f * t).coerceAtMost(0.65f)), Offset(0f, y), Offset(w, y), strokeWidth = 1f + 1.5f * t)
        }
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFD98C), Color.Transparent)), Offset(0f, horizon), Offset(w, horizon), strokeWidth = 3f)
        // Linhas de varredura.
        var y = 0f
        while (y < h) {
            drawRect(Color(0x10000000), Offset(0f, y), androidx.compose.ui.geometry.Size(w, 2f))
            y += 6f
        }
    }
}
