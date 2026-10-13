package dev.jesiel.launcher.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.AppCategory
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.AppsView

val AppsView.icon: ImageVector
    get() = when (this) {
        AppsView.CATEGORY -> Icons.Default.Category
        AppsView.GRID -> Icons.Default.GridView
        AppsView.LIST -> Icons.AutoMirrored.Filled.ViewList
    }

/** Seletor "Exibição: Categoria / Grade / Lista", como no Iniciar do Windows 11. */
@Composable
fun AppsViewSelector(state: LauncherUiState, controller: LauncherController, palette: LauncherPalette) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(palette.tile)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(state.appsView.icon, null, tint = palette.panelText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Exibição: ${state.appsView.label}", color = palette.panelText, fontSize = 13.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(12.dp)) {
            AppsView.entries.forEach { view ->
                DropdownMenuItem(
                    text = { Text(view.label, fontWeight = if (view == state.appsView) FontWeight.SemiBold else FontWeight.Normal) },
                    leadingIcon = { Icon(view.icon, null) },
                    onClick = {
                        open = false
                        controller.viewModel.setAppsView(view)
                    },
                )
            }
        }
    }
}

/** Lista de todos os apps no modo de exibição escolhido, com transição entre os modos. */
@Composable
fun AllAppsContent(
    apps: List<AppInfo>,
    view: AppsView,
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    labels: AppMenuLabels,
    onLaunched: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    gridState: LazyGridState? = null,
) {
    AnimatedContent(
        targetState = view,
        transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f)) togetherWith fadeOut(tween(120)) },
        label = "exibição",
        modifier = modifier,
    ) { mode ->
        // Durante a transição duas listas existem ao mesmo tempo; só a nova usa o estado externo.
        val own = rememberLazyGridState()
        val listState = if (mode == view && gridState != null) gridState else own
        when (mode) {
            AppsView.LIST, AppsView.GRID -> AlphabeticalApps(apps, mode == AppsView.GRID, state, controller, palette, labels, onLaunched, columns, listState)
            AppsView.CATEGORY -> CategoryApps(apps, state, controller, palette, labels, onLaunched)
        }
    }
}

private fun letterOf(app: AppInfo) = app.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#'

@Composable
private fun AlphabeticalApps(
    apps: List<AppInfo>,
    grid: Boolean,
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    labels: AppMenuLabels,
    onLaunched: () -> Unit,
    columns: Int,
    gridState: LazyGridState,
) {
    val grouped = remember(apps) { apps.groupBy(::letterOf) }
    var index = 0
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) columns else 1),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        grouped.forEach { (letter, group) ->
            item(key = "h-$letter", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    letter.toString(),
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            items(group, key = { it.key }) { app ->
                val i = index++
                if (grid) {
                    Box(Modifier.entrance(i), contentAlignment = Alignment.TopCenter) {
                        AppTile(
                            app = app, state = state, controller = controller, iconSize = 44.dp, showLabel = true,
                            labelStyle = palette.panelLabel, labels = labels, iconBackground = palette.iconBackground, onLaunched = onLaunched,
                        )
                    }
                } else {
                    AppListRow(app = app, state = state, controller = controller, labels = labels, onLaunched = onLaunched, modifier = Modifier.entrance(i)) {
                        Text(app.label, color = palette.panelText, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Exibição por categoria: cada cartão mostra os três apps principais em destaque e os demais
 * agrupados num mini-ícone; tocar no grupo abre a categoria inteira.
 */
@Composable
private fun CategoryApps(
    apps: List<AppInfo>,
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    labels: AppMenuLabels,
    onLaunched: () -> Unit,
) {
    val byCategory = remember(apps, state.recents) {
        val recentRank = state.recents.mapIndexed { i, r -> r.app.key to i }.toMap()
        AppCategory.entries.mapNotNull { c ->
            apps.filter { it.category == c }
                .sortedBy { recentRank[it.key] ?: Int.MAX_VALUE }
                .takeIf { it.isNotEmpty() }
                ?.let { c to it }
        }
    }
    var expanded by remember { mutableStateOf<Pair<AppCategory, List<AppInfo>>?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(byCategory.size) { i ->
            val (category, list) = byCategory[i]
            Column(
                Modifier
                    .entrance(i)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.tile)
                    .padding(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    list.take(2).forEach { CategoryIcon(it, controller, onLaunched) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    list.getOrNull(2)?.let { CategoryIcon(it, controller, onLaunched) } ?: Spacer(Modifier.size(46.dp))
                    when {
                        list.size > 4 -> MiniCluster(list.drop(3), palette) { expanded = category to list }
                        list.size == 4 -> CategoryIcon(list[3], controller, onLaunched)
                        else -> Spacer(Modifier.size(46.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    category.label,
                    color = palette.panelText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable { expanded = category to list },
                )
            }
        }
    }

    expanded?.let { (category, list) ->
        Dialog(onDismissRequest = { expanded = null }) {
            Surface(shape = RoundedCornerShape(20.dp), color = palette.panel.copy(alpha = 1f)) {
                @OptIn(ExperimentalLayoutApi::class)
                Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
                    Text(category.label, color = palette.panelText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(maxItemsInEachRow = 4, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        list.forEachIndexed { i, app ->
                            AppTile(
                                app = app, state = state, controller = controller, iconSize = 46.dp, showLabel = true,
                                labelStyle = palette.panelLabel, labels = labels, modifier = Modifier.entrance(i),
                                onLaunched = { expanded = null; onLaunched() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryIcon(app: AppInfo, controller: LauncherController, onLaunched: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Image(
        app.icon,
        contentDescription = app.label,
        modifier = Modifier
            .size(46.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interaction, indication = null) { controller.launch(app); onLaunched() },
    )
}

@Composable
private fun MiniCluster(apps: List<AppInfo>, palette: LauncherPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.tile)
            .clickable(onClick = onClick)
            .padding(5.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            apps.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { Image(it.icon, null, Modifier.size(16.dp).aspectRatio(1f)) }
                }
            }
        }
    }
}

/** Posição (contando os cabeçalhos) de cada letra na lista alfabética, para o índice A–Z. */
fun letterPositions(apps: List<AppInfo>): List<Pair<Char, Int>> {
    var position = 0
    return apps.groupBy(::letterOf).map { (letter, group) ->
        (letter to position).also { position += 1 + group.size }
    }
}
