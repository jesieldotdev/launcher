package dev.jesiel.launcher.ui.macos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.CellSize
import dev.jesiel.launcher.ui.common.AppDock
import dev.jesiel.launcher.ui.common.AppDrawer
import dev.jesiel.launcher.ui.common.MenuBarButton
import dev.jesiel.launcher.ui.common.BatteryCard
import dev.jesiel.launcher.ui.common.BatteryGlyph
import dev.jesiel.launcher.ui.common.BuiltinSpec
import dev.jesiel.launcher.ui.common.CalendarCard
import dev.jesiel.launcher.ui.common.ClockCard
import dev.jesiel.launcher.ui.common.ControlCenter
import dev.jesiel.launcher.ui.common.DesktopCanvas
import dev.jesiel.launcher.ui.common.DockTray
import dev.jesiel.launcher.ui.common.DockDivider
import dev.jesiel.launcher.ui.common.DrawerStyle
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.ThemeRoot
import dev.jesiel.launcher.ui.common.backdrop
import dev.jesiel.launcher.ui.common.withTransparency
import dev.jesiel.launcher.ui.common.isDark
import dev.jesiel.launcher.ui.common.rememberBatteryState
import dev.jesiel.launcher.ui.common.rememberTimeText

fun macPalette(dark: Boolean) = if (dark) {
    LauncherPalette(
        dark = true, accent = Color(0xFF0A84FF),
        card = Color(0x66202024), cardStroke = Color(0x33FFFFFF), cardText = Color.White, cardTextSecondary = Color(0xCCFFFFFF),
        panel = Color(0xF21E1E20), panelStroke = Color(0x26FFFFFF), panelText = Color(0xFFF5F5F7), panelTextSecondary = Color(0xFFA1A1A6),
        tile = Color(0x26FFFFFF), field = Color(0xFF2C2C2E),
        dock = Color(0x8C2A2A2E), dockStroke = Color(0x33FFFFFF), folder = Color(0x40FFFFFF),
        cardShape = RoundedCornerShape(22.dp),
    )
} else {
    LauncherPalette(
        dark = false, accent = Color(0xFF007AFF),
        card = Color(0x40FFFFFF), cardStroke = Color(0x80FFFFFF), cardText = Color.White, cardTextSecondary = Color(0xE6FFFFFF),
        panel = Color(0xF5F5F5F7), panelStroke = Color(0x1A000000), panelText = Color(0xFF1D1D1F), panelTextSecondary = Color(0xFF6E6E73),
        tile = Color(0x14000000), field = Color.White,
        dock = Color(0x66FFFFFF), dockStroke = Color(0x99FFFFFF), folder = Color(0x55FFFFFF),
        cardShape = RoundedCornerShape(22.dp),
    )
}

/**
 * Tema macOS: barra de menus no topo com a Central de Controle, cartões de relógio, bateria e
 * calendário, Dock de vidro e o Launchpad com busca e categorias.
 */
@Composable
fun MacLauncher(state: LauncherUiState, controller: LauncherController) {
    val dark = isDark(state.colorMode)
    val palette = remember(dark, state.transparency) { macPalette(dark).withTransparency(state.transparency) }
    var launchpadOpen by rememberSaveable { mutableStateOf(false) }
    var focusSearch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controller.homePresses) { launchpadOpen = false }

    fun openLaunchpad(search: Boolean) {
        if (controller.openHtmlDrawer(search)) return
        focusSearch = search
        controller.controlCenterOpen = false
        launchpadOpen = true
    }

    val builtins = remember(palette) {
        listOf(
            BuiltinSpec("clock", "Relógio", { c -> CellSize((c / 2).toFloat(), 1.5f) }) { ClockCard(palette, it, align = Alignment.CenterHorizontally) },
            BuiltinSpec("battery", "Bateria", { c -> CellSize((c - c / 2).toFloat(), 1.5f) }) { BatteryCard(palette, it) },
            BuiltinSpec("calendar", "Calendário", { c -> CellSize(c.toFloat(), 2.6f) }) { CalendarCard(palette, it) },
        )
    }

    ThemeRoot(controller, palette) {
        Box(Modifier.fillMaxSize().backdrop(controller, launchpadOpen || controller.controlCenterOpen, state.transparency)) {
            if (state.layout.useThemeWallpaper) MacWallpaper(dark, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (state.layout.showTopBar) {
                    MenuBar(
                        palette = palette,
                        onSpotlight = { openLaunchpad(search = true) },
                        onControlCenter = { controller.controlCenterOpen = !controller.controlCenterOpen },
                    )
                }
                DesktopCanvas(
                    state = state,
                    controller = controller,
                    palette = palette,
                    builtins = builtins,
                    onSwipeUp = { openLaunchpad(search = false) },
                    onSearch = { openLaunchpad(search = true) },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                )
                if (state.layout.showDock) {
                    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                        AppDock(
                            state = state,
                            controller = controller,
                            palette = palette,
                            iconSize = 48.dp,
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            trailing = {
                                DockTray(state, controller, palette)
                                DockDivider(palette.dockStroke, vertical = false)
                                MenuBarButton(state, controller, palette, "Launchpad", Icons.Default.Apps, Color.White, { openLaunchpad(search = false) }, size = 48.dp, background = Color(0x4D8E8E93))
                            },
                        )
                    }
                }
            }
        }
        AppDrawer(
            visible = launchpadOpen,
            state = state,
            controller = controller,
            palette = palette,
            style = DrawerStyle(background = palette.panel.copy(alpha = 0.92f)),
            focusSearch = focusSearch,
            onClose = { launchpadOpen = false },
        )
        ControlCenter(controller, palette)
    }
}

@Composable
private fun MenuBar(palette: LauncherPalette, onSpotlight: () -> Unit, onControlCenter: () -> Unit) {
    val time = rememberTimeText("EEEdMMM")
    val battery = rememberBatteryState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(Color(0x33000000))
            .padding(horizontal = 12.dp),
    ) {
        Text("Launcher", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Text("Arquivo", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        BatteryGlyph(battery?.percent ?: 0, battery?.charging == true, Color.White, 20.dp)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Wifi, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Default.Search, "Spotlight", tint = Color.White,
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onSpotlight).padding(4.dp),
        )
        Icon(
            Icons.Default.ToggleOn, null, tint = Color.White,
            modifier = Modifier
                .semantics { contentDescription = "Central de Controle" }
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onControlCenter)
                .padding(3.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(time, color = Color.White, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onControlCenter))
    }
}

/** Costa ao pôr do sol (ou à noite, no modo escuro). */
@Composable
fun MacWallpaper(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.6f
        val sky = if (dark) listOf(Color(0xFF0E1530), Color(0xFF2E2752), Color(0xFF7A4767), Color(0xFFC07A6A))
        else listOf(Color(0xFF6A7BA8), Color(0xFFB58BA4), Color(0xFFF0A585), Color(0xFFF7C98F))
        drawRect(Brush.verticalGradient(sky, endY = horizon), size = androidx.compose.ui.geometry.Size(w, horizon))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xCCFFE2B0), Color.Transparent), center = Offset(w * 0.6f, horizon), radius = w * 0.45f),
            radius = w * 0.45f,
            center = Offset(w * 0.6f, horizon),
        )
        val sea = if (dark) listOf(Color(0xFF3A3150), Color(0xFF121A33)) else listOf(Color(0xFFC79A8E), Color(0xFF3E6188), Color(0xFF223D5E))
        drawRect(Brush.verticalGradient(sea, startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(w, h - horizon))
        val cliff = if (dark) Color(0xFF0B0F1D) else Color(0xFF3B2E35)
        drawPath(
            Path().apply {
                moveTo(0f, horizon - h * 0.12f)
                cubicTo(w * 0.18f, horizon - h * 0.16f, w * 0.3f, horizon - h * 0.02f, w * 0.42f, horizon + h * 0.02f)
                lineTo(w * 0.36f, h * 0.78f)
                cubicTo(w * 0.25f, h * 0.84f, w * 0.1f, h * 0.9f, 0f, h * 0.92f)
                close()
            },
            cliff,
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.72f, horizon + 4f)
                cubicTo(w * 0.82f, horizon - h * 0.05f, w * 0.95f, horizon - h * 0.04f, w, horizon - h * 0.02f)
                lineTo(w, horizon + 6f)
                close()
            },
            cliff.copy(alpha = 0.8f),
        )
    }
}
