package dev.jesiel.launcher.ui.windows

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.CellSize
import dev.jesiel.launcher.ui.common.AppListRow
import dev.jesiel.launcher.ui.common.AppTile
import dev.jesiel.launcher.ui.common.BatteryCard
import dev.jesiel.launcher.ui.common.BatteryGlyph
import dev.jesiel.launcher.ui.common.BuiltinSpec
import dev.jesiel.launcher.ui.common.ClockCard
import dev.jesiel.launcher.ui.common.ControlCenter
import dev.jesiel.launcher.ui.common.DesktopCanvas
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.LocalPalette
import dev.jesiel.launcher.ui.common.MenuButtonIcon
import dev.jesiel.launcher.ui.common.backdrop
import dev.jesiel.launcher.ui.common.pressScale
import dev.jesiel.launcher.ui.common.withTransparency
import dev.jesiel.launcher.ui.common.PanelPosition
import dev.jesiel.launcher.ui.common.SearchBar
import dev.jesiel.launcher.ui.common.glass
import dev.jesiel.launcher.ui.common.isDark
import dev.jesiel.launcher.ui.common.rememberBatteryState
import dev.jesiel.launcher.ui.common.rememberNow
import dev.jesiel.launcher.ui.common.relativeTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TaskbarHeight = 60.dp

/**
 * Tema Windows: área de trabalho com cartões, apps fixados e recentes; barra de tarefas
 * inferior com Iniciar, busca, apps e bandeja (que abre as configurações rápidas).
 */
@Composable
fun WindowsLauncher(state: LauncherUiState, controller: LauncherController) {
    val dark = isDark(state.colorMode)
    val palette = remember(dark, state.transparency) { windowsPalette(dark).withTransparency(state.transparency) }
    var startOpen by rememberSaveable { mutableStateOf(false) }
    var searchFocused by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller.homePresses) { startOpen = false }
    BackHandler(enabled = startOpen) { startOpen = false }
    BackHandler(enabled = controller.controlCenterOpen) { controller.controlCenterOpen = false }
    BackHandler(enabled = controller.editMode) { controller.editMode = false }

    fun openStart(search: Boolean) {
        controller.controlCenterOpen = false
        searchFocused = search
        startOpen = true
    }

    val bar = if (state.layout.showDock) TaskbarHeight else 0.dp

    val builtins = remember(palette, state.recents) {
        listOf(
            BuiltinSpec("battery", "Bateria", { c -> CellSize((c / 2).toFloat(), 1.4f) }) { BatteryCard(palette, it) },
            BuiltinSpec("clock", "Relógio", { c -> CellSize((c - c / 2).toFloat(), 1.4f) }) { ClockCard(palette, it) },
            BuiltinSpec("search", "Pesquisa", { c -> CellSize(c.toFloat(), 0.75f) }) { m ->
                SearchBar(
                    palette = palette,
                    placeholder = "Pesquisar na web ou no dispositivo",
                    onClick = { openStart(search = true) },
                    modifier = m.padding(vertical = 4.dp),
                    background = palette.panel.copy(alpha = 0.93f),
                    leading = { WindowsLogo(20.dp) },
                )
            },
            BuiltinSpec("recent", "Recente", { c -> CellSize(c.toFloat(), 2.8f) }) { RecentCard(state, controller, palette, it) },
        )
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.colorScheme) {
            Box(Modifier.fillMaxSize()) {
                // Fundo (papel de parede + área de trabalho) desfocado quando um painel abre.
                Box(Modifier.fillMaxSize().backdrop(controller, startOpen || controller.controlCenterOpen, state.transparency)) {
                if (state.layout.useThemeWallpaper) WindowsBloomWallpaper(dark, Modifier.fillMaxSize())

                DesktopCanvas(
                    state = state,
                    controller = controller,
                    palette = palette,
                    builtins = builtins,
                    labels = WindowsMenuLabels,
                    onSwipeUp = { openStart(search = false) },
                    onSearch = { openStart(search = true) },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bar + 12.dp),
                )
                }

                AnimatedVisibility(visible = startOpen, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x33000000))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                startOpen = false
                            },
                    )
                }
                AnimatedVisibility(
                    visible = startOpen,
                    enter = slideInVertically(tween(220)) { it / 3 } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(180)) { it / 3 } + fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    StartMenu(
                        state = state,
                        controller = controller,
                        focusSearch = searchFocused,
                        onClose = { startOpen = false },
                        modifier = Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = bar + 18.dp),
                    )
                }

                ControlCenter(
                    controller,
                    palette,
                    if (state.layout.showDock) PanelPosition.BOTTOM_END else PanelPosition.TOP,
                    bottomInset = bar + 10.dp,
                )

                if (state.layout.showDock) {
                    Taskbar(
                        state = state,
                        controller = controller,
                        palette = palette,
                        startOpen = startOpen,
                        onStart = { if (startOpen) startOpen = false else openStart(search = false) },
                        onSearch = { openStart(search = true) },
                        onTray = {
                            startOpen = false
                            controller.controlCenterOpen = !controller.controlCenterOpen
                        },
                        onLaunched = { startOpen = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCard(state: LauncherUiState, controller: LauncherController, palette: LauncherPalette, modifier: Modifier) {
    Column(
        modifier
            .glass(recentCardColor(palette.dark), RoundedCornerShape(20.dp), palette.panelStroke)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Recente", color = palette.panelText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (state.recents.isNotEmpty()) {
                Text(
                    "Limpar",
                    color = palette.accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { controller.viewModel.clearRecents() }.padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (state.recents.isEmpty()) {
            Text(
                "Os apps que você abrir pelo launcher aparecem aqui.",
                color = palette.panelTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(8.dp),
            )
        }
        val now = rememberNow()
        state.recents.take(6).forEach { recent ->
            RecentRow(recent.app, relativeTime(recent.launchedAt, now), state, controller)
        }
    }
}

@Composable
fun RecentRow(
    app: AppInfo,
    subtitle: String,
    state: LauncherUiState,
    controller: LauncherController,
    onLaunched: () -> Unit = {},
) {
    val palette = LocalPalette.current
    AppListRow(
        app = app,
        state = state,
        controller = controller,
        iconSize = 34.dp,
        labels = WindowsMenuLabels,
        onLaunched = onLaunched,
    ) {
        Column {
            Text(app.label, color = palette.panelText, fontSize = 14.sp)
            Text("Aberto $subtitle", color = palette.panelTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Taskbar(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    startOpen: Boolean,
    onStart: () -> Unit,
    onSearch: () -> Unit,
    onTray: () -> Unit,
    onLaunched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(TaskbarHeight)
            .glass(palette.dock, WindowsShape, palette.dockStroke)
            .padding(horizontal = 6.dp),
    ) {
        TaskbarButton(active = startOpen, description = "Iniciar", palette = palette, onClick = onStart) {
            // O logotipo "afunda" de leve enquanto o Iniciar está aberto.
            val scale by animateFloatAsState(if (startOpen) 0.9f else 1f, spring(dampingRatio = 0.4f), label = "iniciar")
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                MenuButtonIcon(state, controller, palette, 28.dp) { WindowsLogo(26.dp) }
            }
        }
        TaskbarButton(active = false, description = "Pesquisar", palette = palette, onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = null, tint = palette.panelText, modifier = Modifier.size(26.dp))
        }
        if (state.dock.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(28.dp).background(palette.panelStroke))
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.dock.forEach { app ->
                AppTile(
                    app = app,
                    state = state,
                    controller = controller,
                    iconSize = 32.dp,
                    showLabel = false,
                    labels = WindowsMenuLabels,
                    onLaunched = onLaunched,
                )
            }
        }
        Tray(palette, active = controller.controlCenterOpen, onClick = onTray)
    }
}

/** Bandeja do sistema: Wi-Fi, bateria e hora. Abre as configurações rápidas. */
@Composable
private fun Tray(palette: LauncherPalette, active: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow()
    val battery = rememberBatteryState()
    val locale = remember { Locale.getDefault() }
    val time = remember(now) {
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(Date(now))
    }
    val date = remember(now) { SimpleDateFormat("dd/MM", locale).format(Date(now)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .semantics { contentDescription = "Configurações rápidas" }
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) palette.tile else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Default.Wifi, contentDescription = null, tint = palette.panelText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        BatteryGlyph(battery?.percent ?: 0, battery?.charging == true, palette.panelText, 20.dp)
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = palette.panelText, fontSize = 12.sp, maxLines = 1)
            Text(date, color = palette.panelTextSecondary, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TaskbarButton(
    active: Boolean,
    description: String,
    palette: LauncherPalette,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(if (active) palette.tile else Color.Transparent, label = "botão")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .semantics { contentDescription = description }
            .pressScale(interaction, 0.85f)
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        content()
        val indicator by animateDpAsState(if (active) 16.dp else 0.dp, spring(dampingRatio = 0.55f), label = "indicador")
        if (indicator > 0.dp) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = indicator, height = 3.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
            )
        }
    }
}
