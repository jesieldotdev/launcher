package dev.jesiel.launcher.ui.windows

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
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
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.HomeItem
import dev.jesiel.launcher.ui.common.AllAppsContent
import dev.jesiel.launcher.ui.common.AppListRow
import dev.jesiel.launcher.ui.common.AppsViewSelector
import dev.jesiel.launcher.ui.common.entrance
import dev.jesiel.launcher.ui.common.AppTile
import dev.jesiel.launcher.ui.common.FolderTile
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.LocalPalette
import dev.jesiel.launcher.ui.common.acrylicPanel
import dev.jesiel.launcher.ui.common.rememberNow
import dev.jesiel.launcher.ui.common.relativeTime
import dev.jesiel.launcher.ui.common.search

private enum class StartPage { Pinned, AllApps }

@Composable
fun StartMenu(
    state: LauncherUiState,
    controller: LauncherController,
    focusSearch: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val home = if (state.layout.menuOpensAllApps) StartPage.AllApps else StartPage.Pinned
    var page by rememberSaveable(home) { mutableStateOf(home) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun closeAll() {
        keyboard?.hide()
        query = ""
        page = home
        onClose()
    }

    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .acrylicPanel()
            .padding(top = 18.dp),
    ) {
        StartSearchField(
            query = query,
            onQueryChange = { query = it },
            focus = focusSearch,
            onSubmit = {
                val best = state.apps.search(query).firstOrNull()
                if (best != null) controller.launch(best) else controller.webSearch(query)
                closeAll()
            },
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(14.dp))

        Box(Modifier.weight(1f)) {
            val mode = when {
                query.isNotBlank() -> 2
                page == StartPage.AllApps -> 1
                else -> 0
            }
            // Páginas do Iniciar deslizam para os lados, como no Windows 11.
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160)))
                },
                label = "páginas do Iniciar",
            ) { m ->
                when (m) {
                    2 -> SearchResults(query, state, controller, ::closeAll)
                    1 -> AllApps(state, controller, onBack = { page = StartPage.Pinned }, onLaunched = ::closeAll, showBack = !state.layout.menuOpensAllApps)
                    else -> PinnedPage(state, controller, onAllApps = { page = StartPage.AllApps }, onLaunched = ::closeAll)
                }
            }
        }

        HorizontalDivider(color = LocalPalette.current.panelStroke)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(LocalPalette.current.tile.copy(alpha = 0.04f)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            FooterButton(Icons.Default.Edit, "Personalizar") {
                closeAll()
                controller.settingsOpen = true
            }
            Spacer(Modifier.weight(1f))
            FooterButton(Icons.Default.Settings, "Configurações") {
                closeAll()
                controller.openSystemSettings()
            }
        }
    }
}

@Composable
private fun StartSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focus: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { focusRequester.requestFocus() } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(LocalPalette.current.field)
            .padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = LocalPalette.current.panelText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Buscar apps", color = LocalPalette.current.panelTextSecondary, fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = LocalPalette.current.panelText, fontSize = 15.sp),
                cursorBrush = SolidColor(LocalPalette.current.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Limpar",
                tint = LocalPalette.current.panelTextSecondary,
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, actionIcon: ImageVector?, leading: Boolean = false, onAction: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
    ) {
        Text(title, color = LocalPalette.current.panelText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalPalette.current.tile)
                    .clickable(onClick = onAction)
                    .padding(start = if (leading) 4.dp else 10.dp, end = if (leading) 10.dp else 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                if (leading && actionIcon != null) Icon(actionIcon, null, tint = LocalPalette.current.panelText, modifier = Modifier.size(18.dp))
                Text(action, color = LocalPalette.current.panelText, fontSize = 13.sp)
                if (!leading && actionIcon != null) Icon(actionIcon, null, tint = LocalPalette.current.panelText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PinnedPage(state: LauncherUiState, controller: LauncherController, onAllApps: () -> Unit, onLaunched: () -> Unit) {
    // Os "Fixados" do Iniciar são os mesmos favoritos da área de trabalho, para que a
    // organização seja preservada entre temas.
    val pinned = state.home.ifEmpty { state.apps.take(12).map(HomeItem::App) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        SectionHeader("Fixados", "Todos os apps", Icons.AutoMirrored.Filled.KeyboardArrowRight, onAction = onAllApps)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.padding(horizontal = 10.dp)) {
            pinned.chunked(4).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { c, item ->
                        Box(Modifier.weight(1f).entrance(r * 4 + c), contentAlignment = Alignment.TopCenter) {
                            when (item) {
                                is HomeItem.App -> AppTile(
                                    app = item.app,
                                    state = state,
                                    controller = controller,
                                    iconSize = 44.dp,
                                    showLabel = true,
                                    labelStyle = LocalPalette.current.panelLabel,
                                    labels = WindowsMenuLabels,
                                    onLaunched = onLaunched,
                                )
                                is HomeItem.Folder -> FolderTile(
                                    folder = item,
                                    state = state,
                                    controller = controller,
                                    background = LocalPalette.current.tile,
                                    iconSize = 44.dp,
                                    labelStyle = LocalPalette.current.panelLabel,
                                    panelColor = LocalPalette.current.panel,
                                    panelTextColor = LocalPalette.current.panelText,
                                    labels = WindowsMenuLabels,
                                )
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionHeader("Recomendados", null, null)
        Spacer(Modifier.height(4.dp))
        val now = rememberNow()
        Column(Modifier.padding(horizontal = 12.dp)) {
            if (state.recents.isEmpty()) {
                Text(
                    "Os apps que você abrir recentemente aparecerão aqui.",
                    color = LocalPalette.current.panelTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
            state.recents.take(4).forEachIndexed { i, recent ->
                Box(Modifier.entrance(pinned.size + i)) {
                    RecentRow(recent.app, relativeTime(recent.launchedAt, now), state, controller, onLaunched)
                }
            }
        }
    }
}

@Composable
private fun AllApps(state: LauncherUiState, controller: LauncherController, onBack: () -> Unit, onLaunched: () -> Unit, showBack: Boolean = true) {
    val palette = LocalPalette.current
    var category by rememberSaveable { mutableStateOf<AppCategory?>(null) }
    val categories = remember(state.apps) { AppCategory.entries.filter { c -> state.apps.any { it.category == c } } }
    val apps = remember(state.apps, category) { category?.let { c -> state.apps.filter { it.category == c } } ?: state.apps }
    Column {
        if (showBack) {
            SectionHeader("Todos os apps", "Voltar", Icons.AutoMirrored.Filled.KeyboardArrowLeft, leading = true, onAction = onBack)
        } else {
            SectionHeader("Todos os apps", "Fixados", Icons.AutoMirrored.Filled.KeyboardArrowRight, onAction = onBack)
        }
        Row(Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
            AppsViewSelector(state, controller, palette)
        }
        // Filtros por categoria, como no menu do KDE.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        ) {
            item { CategoryChip("Todos", category == null, palette) { category = null } }
            items(categories) { c -> CategoryChip(c.label, category == c, palette) { category = c } }
        }
        AllAppsContent(
            apps = apps,
            view = state.appsView,
            state = state,
            controller = controller,
            palette = palette,
            labels = WindowsMenuLabels,
            onLaunched = onLaunched,
        )
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, palette: LauncherPalette, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) palette.onAccent else palette.panelText,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accent else palette.tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SearchResults(query: String, state: LauncherUiState, controller: LauncherController, onLaunched: () -> Unit) {
    val results = remember(query, state.apps) { state.apps.search(query) }
    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        if (results.isNotEmpty()) {
            item { ResultHeader("Melhor correspondência") }
            item(key = "best") {
                AppListRow(
                    app = results.first(),
                    state = state,
                    controller = controller,
                    iconSize = 44.dp,
                    labels = WindowsMenuLabels,
                    onLaunched = onLaunched,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(LocalPalette.current.tile),
                ) {
                    Column {
                        Text(results.first().label, color = LocalPalette.current.panelText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Aplicativo", color = LocalPalette.current.panelTextSecondary, fontSize = 12.sp)
                    }
                }
            }
            if (results.size > 1) {
                item { ResultHeader("Aplicativos") }
                items(results.drop(1), key = { it.key }) { app -> AppRow(app, state, controller, onLaunched) }
            }
        }
        item { ResultHeader("Pesquisar na Web") }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        controller.webSearch(query)
                        onLaunched()
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.Search, null, tint = LocalPalette.current.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Text("\"$query\" — ver resultados da Web", color = LocalPalette.current.panelText, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ResultHeader(text: String) {
    Text(
        text,
        color = LocalPalette.current.panelTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(app: AppInfo, state: LauncherUiState, controller: LauncherController, onLaunched: () -> Unit) {
    AppListRow(app = app, state = state, controller = controller, labels = WindowsMenuLabels, onLaunched = onLaunched) {
        Text(app.label, color = LocalPalette.current.panelText, fontSize = 14.sp)
    }
}

@Composable
private fun FooterButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = LocalPalette.current.panelText, modifier = Modifier.size(20.dp))
        Text(label, color = LocalPalette.current.panelText, fontSize = 13.sp)
    }
}
