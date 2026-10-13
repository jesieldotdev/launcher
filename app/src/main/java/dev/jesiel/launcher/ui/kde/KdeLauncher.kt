package dev.jesiel.launcher.ui.kde

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.CellSize
import dev.jesiel.launcher.ui.common.AppDock
import dev.jesiel.launcher.ui.common.AppDrawer
import dev.jesiel.launcher.ui.common.AppMenuLabels
import dev.jesiel.launcher.ui.common.MenuBarButton
import dev.jesiel.launcher.ui.common.BatteryGlyph
import dev.jesiel.launcher.ui.common.BuiltinSpec
import dev.jesiel.launcher.ui.common.ClockCard
import dev.jesiel.launcher.ui.common.ControlCenter
import dev.jesiel.launcher.ui.common.DayCard
import dev.jesiel.launcher.ui.common.DesktopCanvas
import dev.jesiel.launcher.ui.common.DockTray
import dev.jesiel.launcher.ui.common.DrawerStyle
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.SystemMonitorCard
import dev.jesiel.launcher.ui.common.ThemeRoot
import dev.jesiel.launcher.ui.common.backdrop
import dev.jesiel.launcher.ui.common.withTransparency
import dev.jesiel.launcher.ui.common.isDark
import dev.jesiel.launcher.ui.common.rememberBatteryState
import dev.jesiel.launcher.ui.common.rememberTimeText

private val Breeze = Color(0xFF3DAEE9)

fun kdePalette(dark: Boolean) = if (dark) {
    LauncherPalette(
        dark = true, accent = Breeze, onAccent = Color(0xFF07203A),
        card = Color(0x8C142438), cardStroke = Color(0x4D3DAEE9), cardText = Color.White, cardTextSecondary = Color(0xFFB9CBDD),
        panel = Color(0xF50F1A2A), panelStroke = Color(0x333DAEE9), panelText = Color(0xFFEFF0F1), panelTextSecondary = Color(0xFF9FB2C6),
        tile = Color(0x26FFFFFF), field = Color(0xFF1B2A3E),
        dock = Color(0xCC12203A), dockStroke = Color(0x553DAEE9), folder = Color(0x403DAEE9),
        iconBackground = Color(0x33A9D4FF),
    )
} else {
    LauncherPalette(
        dark = false, accent = Color(0xFF1D99F3),
        card = Color(0xB3F4F8FC), cardStroke = Color(0x661D99F3), cardText = Color(0xFF232629), cardTextSecondary = Color(0xFF5B6670),
        panel = Color(0xF7EFF0F1), panelStroke = Color(0x331D99F3), panelText = Color(0xFF232629), panelTextSecondary = Color(0xFF66707A),
        tile = Color(0x1A000000), field = Color.White,
        dock = Color(0xE6F4F7FA), dockStroke = Color(0x551D99F3), folder = Color(0x80FFFFFF),
        iconBackground = Color(0x66FFFFFF),
    )
}

private val KdeLabels = AppMenuLabels(pinToDock = "Fixar no painel", unpinFromDock = "Desafixar do painel")

/**
 * Tema KDE Plasma: cartões com dados do sistema, ícones em blocos translúcidos, painel inferior
 * flutuante com lançador e bandeja, e o menu de apps com categorias e abas.
 */
@Composable
fun KdeLauncher(state: LauncherUiState, controller: LauncherController) {
    val dark = isDark(state.colorMode)
    val palette = remember(dark, state.transparency) { kdePalette(dark).withTransparency(state.transparency) }
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
            BuiltinSpec("clock", "Relógio", { c -> CellSize((c / 2).toFloat(), 1.5f) }) { ClockCard(palette, it) },
            BuiltinSpec("day", "Dia e alarme", { c -> CellSize((c - c / 2).toFloat(), 1.5f) }) { DayCard(palette, it, highlight = palette.accent) },
            BuiltinSpec("monitor", "Monitor do sistema", { c -> CellSize(c.toFloat(), 1.3f) }) { SystemMonitorCard(palette, it) },
        )
    }

    ThemeRoot(controller, palette) {
        Box(Modifier.fillMaxSize().backdrop(controller, drawerOpen || controller.controlCenterOpen, state.transparency)) {
            if (state.layout.useThemeWallpaper) KdeWallpaper(dark, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                DesktopCanvas(
                    state = state,
                    controller = controller,
                    palette = palette,
                    builtins = builtins,
                    labels = KdeLabels,
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
                        shape = RoundedCornerShape(20.dp),
                        labels = KdeLabels,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        leading = {
                            MenuBarButton(state, controller, palette, "Lançador de aplicativos", Icons.Default.Apps, palette.onAccent, { openDrawer(search = false) }, size = 44.dp, background = palette.accent)
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
            style = DrawerStyle(bottomTabs = true),
            focusSearch = focusSearch,
            onClose = { drawerOpen = false },
            labels = KdeLabels,
        )
        ControlCenter(controller, palette)
    }
}

/** Montanhas geométricas azuis com lua, no estilo dos papéis de parede do Plasma. */
@Composable
fun KdeWallpaper(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sky = if (dark) listOf(Color(0xFF0A1630), Color(0xFF123A6E), Color(0xFF0B1E3D))
        else listOf(Color(0xFFBFD8F2), Color(0xFF6FA8E0), Color(0xFF2E6CB0))
        drawRect(Brush.verticalGradient(sky))
        // Facetas diagonais
        val facet = if (dark) Color(0x223DAEE9) else Color(0x33FFFFFF)
        for (i in 0 until 6) {
            val x = w * (i / 5f)
            drawPath(Path().apply { moveTo(x, 0f); lineTo(x + w * 0.35f, 0f); lineTo(x - w * 0.2f, h * 0.55f); close() }, facet)
        }
        val moon = Offset(w * 0.7f, h * 0.42f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFDCEBFF), Color(0xFF7FA9DC)), center = moon, radius = w * 0.16f),
            radius = w * 0.15f,
            center = moon,
            alpha = if (dark) 0.85f else 0.7f,
        )
        val ranges = if (dark) listOf(Color(0xFF1E4F8C), Color(0xFF143766), Color(0xFF0B2347), Color(0xFF06142C))
        else listOf(Color(0xFF4F8FD1), Color(0xFF3672B4), Color(0xFF235692), Color(0xFF173E6E))
        ranges.forEachIndexed { i, color ->
            val base = h * (0.48f + i * 0.11f)
            drawPath(
                Path().apply {
                    moveTo(0f, base)
                    val n = 4 + i
                    for (p in 0..n) lineTo(w * p / n, base - (if ((p + i) % 2 == 0) h * 0.09f else 0f))
                    lineTo(w, h); lineTo(0f, h); close()
                },
                color,
            )
        }
    }
}
