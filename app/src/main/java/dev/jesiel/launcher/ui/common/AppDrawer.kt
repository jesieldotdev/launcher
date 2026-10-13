package dev.jesiel.launcher.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.AppCategory
import dev.jesiel.launcher.data.AppsView
import kotlinx.coroutines.launch

/** Variações de gaveta de apps entre os temas. */
data class DrawerStyle(
    val searchPlaceholder: String = "Buscar apps",
    val categories: Boolean = true,
    /** Índice A–Z na lateral (iOS). */
    val alphabetIndex: Boolean = false,
    /** Abas inferiores Apps / Widgets / Papéis de parede / Personalizar (KDE). */
    val bottomTabs: Boolean = false,
    /** Fundo da gaveta; por padrão usa o painel do tema. */
    val background: Color? = null,
    val columns: Int = 4,
)

/** Gaveta de apps em tela cheia com busca e filtros, usada pelos temas além do Windows. */
@Composable
fun AppDrawer(
    visible: Boolean,
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    style: DrawerStyle,
    focusSearch: Boolean,
    onClose: () -> Unit,
    labels: AppMenuLabels = AppMenuLabels(),
) {
    BackHandler(enabled = visible, onBack = onClose)
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(240)) { it / 4 } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(160)),
    ) {
        DrawerContent(state, controller, palette, style, focusSearch, onClose, labels)
    }
}

@Composable
private fun DrawerContent(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    style: DrawerStyle,
    focusSearch: Boolean,
    onClose: () -> Unit,
    labels: AppMenuLabels,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<AppCategory?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val grid = rememberLazyGridState()

    fun close() {
        keyboard?.hide()
        query = ""
        onClose()
    }

    val apps = remember(state.apps, query, category) {
        when {
            query.isNotBlank() -> state.apps.search(query)
            category != null -> state.apps.filter { it.category == category }
            else -> state.apps
        }
    }
    val categories = remember(state.apps) { AppCategory.entries.filter { c -> state.apps.any { it.category == c } } }

    Column(
        Modifier
            .fillMaxSize()
            .background((style.background ?: palette.panel).let { if (palette.panel.alpha < 1f) it.copy(alpha = minOf(it.alpha, palette.panel.alpha)) else it })
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(12.dp))
        DrawerSearch(query, { query = it }, style.searchPlaceholder, palette, focusSearch, onClose = ::close) {
            val best = state.apps.search(query).firstOrNull()
            if (best != null) controller.launch(best) else controller.webSearch(query)
            close()
        }
        if (query.isBlank()) {
            Row(Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp)) { AppsViewSelector(state, controller, palette) }
        }
        if (style.categories && query.isBlank()) {
            LazyRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                item { Chip("Todos", category == null, palette) { category = null } }
                items(categories) { c -> Chip(c.label, category == c, palette) { category = c } }
            }
        }
        Row(Modifier.weight(1f).padding(top = 8.dp)) {
            if (query.isBlank()) {
                AllAppsContent(
                    apps = apps,
                    view = state.appsView,
                    state = state,
                    controller = controller,
                    palette = palette,
                    labels = labels,
                    onLaunched = ::close,
                    columns = style.columns,
                    gridState = grid,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            } else LazyVerticalGrid(
                columns = GridCells.Fixed(style.columns),
                state = grid,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    Box(contentAlignment = Alignment.TopCenter) {
                        AppTile(
                            app = app,
                            state = state,
                            controller = controller,
                            labelStyle = palette.panelLabel,
                            showLabel = true,
                            labels = labels,
                            iconBackground = palette.iconBackground,
                            onLaunched = ::close,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { controller.webSearch(query); close() }
                            .padding(12.dp),
                    ) {
                        Icon(Icons.Default.Search, null, tint = palette.accent)
                        Spacer(Modifier.width(12.dp))
                        Text("Pesquisar \"$query\" na Web", color = palette.panelText)
                    }
                }
            }
            if (style.alphabetIndex && query.isBlank() && category == null && state.appsView != AppsView.CATEGORY) {
                val letters = remember(apps) { letterPositions(apps) }
                val scope = rememberCoroutineScope()
                Column(
                    Modifier.fillMaxHeight().padding(end = 4.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    letters.forEach { (letter, position) ->
                        Text(
                            letter.toString(),
                            color = palette.accent,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { scope.launch { grid.animateScrollToItem(position) } }.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        if (style.bottomTabs) {
            Row(
                Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .glass(palette.card, RoundedCornerShape(18.dp), palette.panelStroke)
                    .padding(6.dp),
            ) {
                BottomTab(Icons.Default.Apps, "Apps", true, palette, Modifier.weight(1f)) {}
                BottomTab(Icons.Default.Widgets, "Widgets", false, palette, Modifier.weight(1f)) {
                    close(); controller.widgetPickerOpen = true
                }
                BottomTab(Icons.Default.Wallpaper, "Papéis de parede", false, palette, Modifier.weight(1f)) {
                    close(); controller.openWallpaperPicker()
                }
                BottomTab(Icons.Default.Palette, "Personalizar", false, palette, Modifier.weight(1f)) {
                    close(); controller.settingsOpen = true
                }
            }
        }
    }
}

@Composable
private fun DrawerSearch(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    palette: LauncherPalette,
    focus: Boolean,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { focusRequester.requestFocus() } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(48.dp)
            .glass(palette.card, RoundedCornerShape(24.dp), palette.panelStroke)
            .padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Default.Search, null, tint = palette.panelText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, color = palette.panelTextSecondary, fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = palette.panelText, fontSize = 15.sp),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        Icon(
            Icons.Default.Close,
            contentDescription = if (query.isEmpty()) "Fechar" else "Limpar",
            tint = palette.panelTextSecondary,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).clickable { if (query.isEmpty()) onClose() else onQuery("") },
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, palette: LauncherPalette, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) palette.onAccent else palette.panelText,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.accent else palette.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun BottomTab(icon: ImageVector, label: String, selected: Boolean, palette: LauncherPalette, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) palette.accent else palette.panelText, modifier = Modifier.size(22.dp))
        Text(label, color = palette.panelText, fontSize = 10.sp, maxLines = 1)
    }
}

