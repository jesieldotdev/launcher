package dev.jesiel.launcher.ui.ios

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.CellSize
import dev.jesiel.launcher.ui.common.AppDock
import dev.jesiel.launcher.ui.common.AppDrawer
import dev.jesiel.launcher.ui.common.BatteryCard
import dev.jesiel.launcher.ui.common.BuiltinSpec
import dev.jesiel.launcher.ui.common.ControlCenter
import dev.jesiel.launcher.ui.common.DayCard
import dev.jesiel.launcher.ui.common.DesktopCanvas
import dev.jesiel.launcher.ui.common.DockTray
import dev.jesiel.launcher.ui.common.DrawerStyle
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.SearchBar
import dev.jesiel.launcher.ui.common.ThemeRoot
import dev.jesiel.launcher.ui.common.backdrop
import dev.jesiel.launcher.ui.common.withTransparency
import dev.jesiel.launcher.ui.common.isDark

fun iosPalette(dark: Boolean) = if (dark) {
    LauncherPalette(
        dark = true, accent = Color(0xFF0A84FF),
        card = Color(0xE61C1C1E), cardStroke = Color(0x1FFFFFFF), cardText = Color.White, cardTextSecondary = Color(0xFF98989F),
        panel = Color(0xF21C1C1E), panelStroke = Color(0x1FFFFFFF), panelText = Color.White, panelTextSecondary = Color(0xFF98989F),
        tile = Color(0x33FFFFFF), field = Color(0xFF2C2C2E),
        dock = Color(0x66323236), dockStroke = Color(0x26FFFFFF), folder = Color(0x40FFFFFF),
        cardShape = RoundedCornerShape(24.dp), panelShape = RoundedCornerShape(30.dp),
    )
} else {
    LauncherPalette(
        dark = false, accent = Color(0xFF007AFF), onWallpaper = Color(0xFF1C1C1E),
        card = Color(0xF2FFFFFF), cardStroke = Color(0x0D000000), cardText = Color(0xFF1C1C1E), cardTextSecondary = Color(0xFF8E8E93),
        panel = Color(0xF2F2F2F7), panelStroke = Color(0x0D000000), panelText = Color(0xFF1C1C1E), panelTextSecondary = Color(0xFF8E8E93),
        tile = Color(0x1F000000), field = Color.White,
        dock = Color(0x59FFFFFF), dockStroke = Color(0x66FFFFFF), folder = Color(0x66FFFFFF),
        cardShape = RoundedCornerShape(24.dp), panelShape = RoundedCornerShape(30.dp),
    )
}

/**
 * Tema iOS: widgets grandes, grade de apps em páginas, busca acima do dock e a Biblioteca de
 * Apps com categorias e índice alfabético. A Central de Controle desce do topo.
 */
@Composable
fun IosLauncher(state: LauncherUiState, controller: LauncherController) {
    val dark = isDark(state.colorMode)
    val palette = remember(dark, state.transparency) { iosPalette(dark).withTransparency(state.transparency) }
    var libraryOpen by rememberSaveable { mutableStateOf(false) }
    var focusSearch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controller.homePresses) { libraryOpen = false }

    fun openLibrary(search: Boolean) {
        if (controller.openHtmlDrawer(search)) return
        focusSearch = search
        controller.controlCenterOpen = false
        libraryOpen = true
    }

    val builtins = remember(palette) {
        listOf(
            BuiltinSpec("battery", "Bateria", { c -> CellSize((c / 2).toFloat(), 2f) }) { BatteryCard(palette, it) },
            BuiltinSpec("day", "Calendário", { c -> CellSize((c - c / 2).toFloat(), 2f) }) { DayCard(palette, it) },
        )
    }

    ThemeRoot(controller, palette) {
        Box(Modifier.fillMaxSize().backdrop(controller, libraryOpen || controller.controlCenterOpen, state.transparency)) {
            if (state.layout.useThemeWallpaper) IosWallpaper(dark, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                DesktopCanvas(
                    state = state,
                    controller = controller,
                    palette = palette,
                    builtins = builtins,
                    onSwipeUp = { openLibrary(search = false) },
                    onSearch = { openLibrary(search = true) },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SearchBar(
                        palette = palette,
                        placeholder = "Buscar",
                        onClick = { openLibrary(search = true) },
                        background = palette.dock,
                        modifier = Modifier.width(140.dp).height(36.dp),
                    )
                }
                if (state.layout.showDock) {
                    AppDock(
                        state = state,
                        controller = controller,
                        palette = palette,
                        iconSize = 54.dp,
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                        trailing = { DockTray(state, controller, palette) },
                    )
                }
            }
        }
        AppDrawer(
            visible = libraryOpen,
            state = state,
            controller = controller,
            palette = palette,
            style = DrawerStyle(alphabetIndex = true, searchPlaceholder = "Biblioteca de Apps"),
            focusSearch = focusSearch,
            onClose = { libraryOpen = false },
        )
        ControlCenter(controller, palette)
    }
}

/** Ondas em tons pastel (ou noturnos, no modo escuro). */
@Composable
fun IosWallpaper(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                if (dark) listOf(Color(0xFF151A3A), Color(0xFF2A1C4A), Color(0xFF0E1428))
                else listOf(Color(0xFFB9CBFF), Color(0xFFF4C6E0), Color(0xFFFFE1C6)),
                start = Offset.Zero,
                end = Offset(w, h),
            ),
        )
        val blobs = if (dark) {
            listOf(Offset(0.2f, 0.25f) to Color(0xAA3E4BD8), Offset(0.85f, 0.4f) to Color(0x99B03C92), Offset(0.35f, 0.75f) to Color(0x99335FD0), Offset(0.9f, 0.9f) to Color(0x886A3BC4))
        } else {
            listOf(Offset(0.2f, 0.25f) to Color(0xAA6F8CFF), Offset(0.85f, 0.4f) to Color(0x99FF8CC6), Offset(0.35f, 0.75f) to Color(0x998FB8FF), Offset(0.9f, 0.9f) to Color(0x88FFC48A))
        }
        blobs.forEach { (pos, color) ->
            val c = Offset(pos.x * w, pos.y * h)
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = w * 0.6f), radius = w * 0.6f, center = c)
        }
    }
}
