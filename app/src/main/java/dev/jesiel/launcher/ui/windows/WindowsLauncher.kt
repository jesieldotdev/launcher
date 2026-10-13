package dev.jesiel.launcher.ui.windows

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.HomeItem
import dev.jesiel.launcher.data.RecentApp
import dev.jesiel.launcher.ui.common.AppListRow
import dev.jesiel.launcher.ui.common.AppTile
import dev.jesiel.launcher.ui.common.AppWidget
import dev.jesiel.launcher.ui.common.FolderTile
import dev.jesiel.launcher.ui.common.relativeTime

/**
 * Tema Windows: área de trabalho com cartões, apps fixados e recentes; barra de tarefas
 * inferior com botão Iniciar, e Menu Iniciar com busca, "Fixados", "Todos os apps" e "Recomendados".
 */
@Composable
fun WindowsLauncher(state: LauncherUiState, controller: LauncherController) {
    var startOpen by rememberSaveable { mutableStateOf(false) }
    var searchFocused by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller.homePresses) { startOpen = false }
    BackHandler(enabled = startOpen) { startOpen = false }

    fun openStart(search: Boolean) {
        searchFocused = search
        startOpen = true
    }

    MaterialTheme(colorScheme = WindowsColorScheme) {
        Box(Modifier.fillMaxSize()) {
            if (state.layout.useThemeWallpaper) WindowsBloomWallpaper(Modifier.fillMaxSize())

            Desktop(
                state = state,
                controller = controller,
                onSearch = { openStart(search = true) },
                onSwipeUp = { openStart(search = false) },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = TaskbarHeight + 12.dp),
            )

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
                        .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = TaskbarHeight + 18.dp),
                )
            }

            Taskbar(
                state = state,
                controller = controller,
                startOpen = startOpen,
                onStart = { if (startOpen) startOpen = false else openStart(search = false) },
                onSearch = { openStart(search = true) },
                onLaunched = { startOpen = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Desktop(
    state: LauncherUiState,
    controller: LauncherController,
    onSearch: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val scroll = rememberScrollState()
    // Deslizar para cima no fim da área de trabalho abre o Menu Iniciar.
    val swipeUp = remember(onSwipeUp) {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y < -1500f) onSwipeUp()
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { pos ->
                    menuOffset = DpOffset(pos.x.toDp(), pos.y.toDp())
                    menuOpen = true
                })
            }
            .nestedScroll(swipeUp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BatteryCard(Modifier.weight(1f).height(132.dp))
                ClockCard(Modifier.weight(1f).height(132.dp))
            }

            SearchPill(onClick = onSearch)

            state.widgetIds.forEach { id ->
                AppWidget(
                    id = id,
                    widgets = controller.widgets,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)),
                )
            }

            PinnedGrid(state, controller)

            RecentCard(state.recents, state, controller)
        }

        Box(Modifier.align(Alignment.TopStart).padding(start = menuOffset.x, top = menuOffset.y)) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(12.dp)) {
                DropdownMenuItem(text = { Text("Adicionar widget") }, onClick = {
                    menuOpen = false
                    controller.widgetPickerOpen = true
                })
                DropdownMenuItem(text = { Text("Papel de parede") }, onClick = {
                    menuOpen = false
                    controller.openWallpaperPicker()
                })
                DropdownMenuItem(text = { Text("Personalizar launcher") }, onClick = {
                    menuOpen = false
                    controller.settingsOpen = true
                })
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .acrylic(Color(0xEEF5F8FD), RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        WindowsLogo(20.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            "Pesquisar na web ou no dispositivo",
            color = WinColors.TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.Search, contentDescription = null, tint = WinColors.Text)
    }
}

@Composable
private fun PinnedGrid(state: LauncherUiState, controller: LauncherController) {
    if (state.home.isEmpty()) {
        Text(
            "Toque e segure um app no Menu Iniciar para fixá-lo aqui.",
            style = WallpaperLabel.copy(fontSize = 14.sp),
            modifier = Modifier.padding(8.dp),
        )
        return
    }
    val columns = state.layout.gridColumns
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.home.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        HomeItemTile(item, state, controller)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HomeItemTile(item: HomeItem, state: LauncherUiState, controller: LauncherController) {
    when (item) {
        is HomeItem.App -> AppTile(
            app = item.app,
            state = state,
            controller = controller,
            labelStyle = WallpaperLabel,
            labels = WindowsMenuLabels,
        )
        is HomeItem.Folder -> FolderTile(
            folder = item,
            state = state,
            controller = controller,
            background = Color(0x55FFFFFF),
            labelStyle = WallpaperLabel,
            panelColor = WinColors.Panel,
            panelTextColor = WinColors.Text,
            labels = WindowsMenuLabels,
        )
    }
}

@Composable
private fun RecentCard(recents: List<RecentApp>, state: LauncherUiState, controller: LauncherController) {
    Column(
        Modifier
            .fillMaxWidth()
            .acrylic(Color(0x8CE9F0FA), RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Recente", color = WinColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (recents.isNotEmpty()) {
                Text(
                    "Limpar",
                    color = WinColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { controller.viewModel.clearRecents() }.padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (recents.isEmpty()) {
            Text(
                "Os apps que você abrir pelo launcher aparecem aqui.",
                color = WinColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(8.dp),
            )
        }
        val now = rememberNow()
        recents.take(4).forEach { recent ->
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
    AppListRow(
        app = app,
        state = state,
        controller = controller,
        iconSize = 34.dp,
        labels = WindowsMenuLabels,
        onLaunched = onLaunched,
    ) {
        Column {
            Text(app.label, color = WinColors.Text, fontSize = 14.sp)
            Text("Aberto $subtitle", color = WinColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

private val TaskbarHeight = 60.dp

@Composable
private fun Taskbar(
    state: LauncherUiState,
    controller: LauncherController,
    startOpen: Boolean,
    onStart: () -> Unit,
    onSearch: () -> Unit,
    onLaunched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .height(TaskbarHeight)
            .acrylic(WinColors.Taskbar, RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp),
    ) {
        TaskbarButton(active = startOpen, description = "Iniciar", onClick = onStart) { WindowsLogo(26.dp) }
        TaskbarButton(active = false, description = "Pesquisar", onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = null, tint = WinColors.Text, modifier = Modifier.size(26.dp))
        }
        if (state.dock.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(28.dp).background(WinColors.Divider))
        }
        state.dock.forEach { app ->
            AppTile(
                app = app,
                state = state,
                controller = controller,
                iconSize = 34.dp,
                showLabel = false,
                labels = WindowsMenuLabels,
                onLaunched = onLaunched,
            )
        }
    }
}

@Composable
private fun TaskbarButton(active: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .semantics { contentDescription = description }
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0x22000000) else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        content()
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 16.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(WinColors.Accent),
            )
        }
    }
}
