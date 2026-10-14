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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.jesiel.launcher.ui.common.AppDrawer
import dev.jesiel.launcher.ui.common.AppMenuLabels
import dev.jesiel.launcher.ui.common.BuiltinSpec
import dev.jesiel.launcher.ui.common.ControlCenter
import dev.jesiel.launcher.ui.common.DesktopCanvas
import dev.jesiel.launcher.ui.common.DockTray
import dev.jesiel.launcher.ui.common.DrawerStyle
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.MenuBarButton
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

private val Amber = Color(0xFFE9C27A)
private val AmberBright = Color(0xFFFFD98C)
private val AmberDim = Color(0xFFC9A464)
private val AmberLine = Color(0xFF7D6136)

fun retroPalette(dark: Boolean) = LauncherPalette(
    dark = true,
    accent = Amber,
    onAccent = Color(0xFF1A1208),
    onWallpaper = Color(0xFFF0D49A),
    card = if (dark) Color(0x8C100D07) else Color(0x66140F08),
    cardStroke = AmberLine,
    cardText = Amber,
    cardTextSecondary = AmberDim,
    cardShape = RoundedCornerShape(22.dp),
    panel = Color(0xF20E0B06),
    panelStroke = AmberLine,
    panelText = Color(0xFFF0D49A),
    panelTextSecondary = AmberDim,
    panelShape = RoundedCornerShape(22.dp),
    tile = Color(0x26E9C27A),
    field = Color(0xFF1A140B),
    dock = if (dark) Color(0xB30E0B06) else Color(0x8C140F08),
    dockStroke = AmberLine,
    folder = Color(0x40E9C27A),
    font = Orbitron,
)

private val RetroLabels = AppMenuLabels(pinToDock = "Fixar no console", unpinFromDock = "Tirar do console")

/**
 * Tema Retrô futurista: âmbar sobre fundo escuro translúcido, fonte Orbitron, linhas de
 * varredura, relógio de painel de nave e papel de parede com sol listrado e grade.
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

    val builtins = remember(palette) {
        listOf(
            BuiltinSpec("clock", "Relógio de bordo", { c -> CellSize(c.toFloat(), 2.2f) }) { RetroClockCard(palette, it) },
            BuiltinSpec("monitor", "Monitor do sistema", { c -> CellSize(c.toFloat(), 1.4f) }) { RetroMonitorCard(palette, it) },
        )
    }

    ThemeRoot(controller, palette) {
        Box(Modifier.fillMaxSize().backdrop(controller, drawerOpen || controller.controlCenterOpen, state.transparency)) {
            if (state.layout.useThemeWallpaper) RetroWallpaper(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
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
                        iconSize = 42.dp,
                        shape = RoundedCornerShape(22.dp),
                        labels = RetroLabels,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        leading = {
                            MenuBarButton(
                                state, controller, palette, "Apps", Icons.Default.Apps, palette.onAccent, { openDrawer(search = false) },
                                size = 44.dp, background = Amber, shape = RoundedCornerShape(14.dp),
                            )
                        },
                        trailing = { DockTray(state, controller, palette) },
                    )
                }
            }
        }
        AppDrawer(
            visible = drawerOpen,
            state = state,
            controller = controller,
            palette = palette,
            style = DrawerStyle(searchPlaceholder = "Buscar no sistema"),
            focusSearch = focusSearch,
            onClose = { drawerOpen = false },
            labels = RetroLabels,
        )
        ControlCenter(controller, palette)
    }
}

/** Faixas horizontais finas por cima do conteúdo, como um monitor de tubo. */
private fun Modifier.scanlines(color: Color = Color(0x09FFC878)): Modifier = drawWithContent {
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
private fun phosphor(size: Dp, color: Color = AmberBright): TextStyle {
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
private fun RetroFrame(palette: LauncherPalette, modifier: Modifier, onClick: () -> Unit, content: @Composable (maxW: Dp, maxH: Dp) -> Unit) {
    BoxWithConstraints(
        modifier
            .glass(palette.card, palette.cardShape, palette.cardStroke)
            .scanlines()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { content(maxWidth, maxHeight) }
}

@Composable
private fun Header(left: String, right: String, size: Dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size * 0.75f, size * 0.6f).background(Amber))
        Spacer(Modifier.width(size * 0.55f))
        Text(left.uppercase(), color = Amber, fontSize = size.asFixedSp(), letterSpacing = 0.12.em, maxLines = 1, modifier = Modifier.weight(1f))
        Text(right.uppercase(), color = Amber, fontSize = size.asFixedSp(), letterSpacing = 0.1.em, maxLines = 1)
    }
}

@Composable
private fun Divider(vertical: Dp) {
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
private fun SegmentBar(value: Float, modifier: Modifier, segments: Int = 20) {
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
fun RetroWallpaper(modifier: Modifier = Modifier) {
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
        var t = 0.02f
        while (t < 1f) {
            val y = horizon + (h - horizon) * t
            drawLine(line.copy(alpha = 0.15f + 0.4f * t), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
            t = t * 1.45f + 0.02f
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
