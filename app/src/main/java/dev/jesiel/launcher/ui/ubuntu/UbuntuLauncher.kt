package dev.jesiel.launcher.ui.ubuntu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import dev.jesiel.launcher.ui.common.AppMenuLabels
import dev.jesiel.launcher.ui.common.MenuBarButton
import dev.jesiel.launcher.ui.common.BatteryCard
import dev.jesiel.launcher.ui.common.BatteryGlyph
import dev.jesiel.launcher.ui.common.BuiltinSpec
import dev.jesiel.launcher.ui.common.ClockCard
import dev.jesiel.launcher.ui.common.ControlCenter
import dev.jesiel.launcher.ui.common.DesktopCanvas
import dev.jesiel.launcher.ui.common.DockDivider
import dev.jesiel.launcher.ui.common.DrawerStyle
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.ThemeRoot
import dev.jesiel.launcher.ui.common.backdrop
import dev.jesiel.launcher.ui.common.withTransparency
import dev.jesiel.launcher.ui.common.isDark
import dev.jesiel.launcher.ui.common.rememberBatteryState
import dev.jesiel.launcher.ui.common.rememberTimeText

private val Orange = Color(0xFFE95420)

fun ubuntuPalette(dark: Boolean) = if (dark) {
    LauncherPalette(
        dark = true, accent = Orange,
        card = Color(0x59000000), cardStroke = Color(0x22FFFFFF), cardText = Color.White, cardTextSecondary = Color(0xCCFFFFFF),
        panel = Color(0xF52C2C2C), panelStroke = Color(0x1AFFFFFF), panelText = Color(0xFFF2F2F2), panelTextSecondary = Color(0xFFB0B0B0),
        tile = Color(0x1FFFFFFF), field = Color(0xFF3A3A3A),
        dock = Color(0xB31E1E1E), dockStroke = Color(0x1AFFFFFF), folder = Color(0x40FFFFFF),
    )
} else {
    LauncherPalette(
        dark = false, accent = Orange,
        card = Color(0x4D3A0F1E), cardStroke = Color(0x40FFFFFF), cardText = Color.White, cardTextSecondary = Color(0xE6FFFFFF),
        panel = Color(0xF7FAFAFA), panelStroke = Color(0x1A000000), panelText = Color(0xFF3D3D3D), panelTextSecondary = Color(0xFF777777),
        tile = Color(0x14000000), field = Color.White,
        dock = Color(0xCCF6F6F6), dockStroke = Color(0x33FFFFFF), folder = Color(0x55FFFFFF),
    )
}

private val UbuntuLabels = AppMenuLabels(pinToDock = "Adicionar aos favoritos", unpinFromDock = "Remover dos favoritos")

/**
 * Tema Ubuntu (GNOME): barra superior com "Atividades" e menu do sistema, dock lateral com os
 * favoritos e a grade de aplicativos em tela cheia.
 */
@Composable
fun UbuntuLauncher(state: LauncherUiState, controller: LauncherController) {
    val dark = isDark(state.colorMode)
    val palette = remember(dark, state.transparency) { ubuntuPalette(dark).withTransparency(state.transparency) }
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
            BuiltinSpec("clock", "Relógio", { c -> CellSize((c - c / 2).toFloat(), 1.3f) }) { ClockCard(palette, it, showCard = false) },
            BuiltinSpec("battery", "Bateria", { c -> CellSize((c / 2).toFloat(), 1.3f) }) { BatteryCard(palette, it) },
        )
    }

    ThemeRoot(controller, palette) {
        Box(Modifier.fillMaxSize().backdrop(controller, drawerOpen || controller.controlCenterOpen, state.transparency)) {
            if (state.layout.useThemeWallpaper) UbuntuWallpaper(dark, Modifier.fillMaxSize())

            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (state.layout.showTopBar) {
                    TopBar(
                        palette = palette,
                        onActivities = { openDrawer(search = false) },
                        onSystem = { controller.controlCenterOpen = !controller.controlCenterOpen },
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                    )
                }
                Row(Modifier.fillMaxSize().padding(top = if (state.layout.showTopBar) 36.dp else 4.dp, bottom = 8.dp)) {
                    if (state.layout.showDock) {
                        AppDock(
                            state = state,
                            controller = controller,
                            palette = palette,
                            vertical = true,
                            iconSize = 40.dp,
                            shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                            labels = UbuntuLabels,
                            modifier = Modifier.fillMaxHeight(0.82f).align(Alignment.CenterVertically),
                            trailing = {
                                DockDivider(palette.cardTextSecondary.copy(alpha = 0.4f), vertical = true)
                                MenuBarButton(state, controller, palette, "Mostrar aplicativos", Icons.Default.Apps, palette.panelText, { openDrawer(search = false) }, size = 44.dp)
                            },
                        )
                    }
                    DesktopCanvas(
                        state = state,
                        controller = controller,
                        palette = palette,
                        builtins = builtins,
                        labels = UbuntuLabels,
                        onSwipeUp = { openDrawer(search = false) },
                        onSearch = { openDrawer(search = true) },
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp),
                    )
                }
            }
        }
        AppDrawer(
            visible = drawerOpen,
            state = state,
            controller = controller,
            palette = palette,
            style = DrawerStyle(categories = false, background = if (dark) Color(0xF5300A24) else Color(0xF7F4ECEF)),
            focusSearch = focusSearch,
            onClose = { drawerOpen = false },
            labels = UbuntuLabels,
        )
        ControlCenter(controller, palette)
    }
}

@Composable
private fun TopBar(palette: LauncherPalette, onActivities: () -> Unit, onSystem: () -> Unit, modifier: Modifier) {
    val time = rememberTimeText("EEEdMMM")
    val battery = rememberBatteryState()
    Box(modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xB3000000)).padding(horizontal = 2.dp)) {
        Text(
            "Atividades",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onActivities)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Text(
            time,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).clickable(onClick = onSystem).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .semantics { contentDescription = "Menu do sistema" }
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onSystem)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(Icons.Default.Wifi, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            BatteryGlyph(battery?.percent ?: 0, battery?.charging == true, Color.White, 20.dp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

/** Paisagem em polígonos com sol laranja, no espírito dos papéis de parede do Ubuntu. */
@Composable
fun UbuntuWallpaper(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sky = if (dark) listOf(Color(0xFF2C001E), Color(0xFF5E1A4A), Color(0xFFB8402A))
        else listOf(Color(0xFFF6D3C0), Color(0xFFF0A07A), Color(0xFFE2683C))
        drawRect(Brush.verticalGradient(sky))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFF8A3D), Color(0xFFE95420)), center = Offset(w * 0.72f, h * 0.36f), radius = w * 0.32f),
            radius = w * 0.3f,
            center = Offset(w * 0.72f, h * 0.36f),
        )
        val layers = if (dark) listOf(Color(0xFF7A1F3D), Color(0xFF55163A), Color(0xFF34102C), Color(0xFF1E0819))
        else listOf(Color(0xFFD9653F), Color(0xFFB9472F), Color(0xFF8E2F2A), Color(0xFF5F1E25))
        layers.forEachIndexed { i, color ->
            val base = h * (0.5f + i * 0.12f)
            val path = Path().apply {
                moveTo(0f, base + h * 0.05f)
                val peaks = 5 + i
                for (p in 0..peaks) {
                    val x = w * p / peaks
                    val y = base - (if (p % 2 == 0) h * 0.08f else h * 0.015f) * (1f - i * 0.15f)
                    lineTo(x, y)
                }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path, color)
        }
    }
}
