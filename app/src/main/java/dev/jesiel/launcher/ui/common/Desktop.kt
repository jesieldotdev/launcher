package dev.jesiel.launcher.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.LauncherViewModel
import dev.jesiel.launcher.data.CellSize
import dev.jesiel.launcher.data.DesktopGrid
import dev.jesiel.launcher.data.HomeItem
import dev.jesiel.launcher.data.LeftScreen
import dev.jesiel.launcher.data.Placement
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Algo que ocupa espaço na área de trabalho. */
private sealed interface DesktopEntry {
    val id: String

    data class Item(val item: HomeItem) : DesktopEntry {
        override val id: String = if (item is HomeItem.Folder) LauncherViewModel.FOLDER_PREFIX + item.id else item.id
    }

    data class Widget(val widgetId: Int) : DesktopEntry {
        override val id: String = "widget:$widgetId"
    }

    data class Builtin(val spec: BuiltinSpec) : DesktopEntry {
        override val id: String = "builtin:${spec.id}"
    }
}

/**
 * Área de trabalho compartilhada pelos temas: apps, pastas, widgets Android e componentes do
 * tema posicionados numa grade (com encaixe) ou livremente, em uma ou várias telas.
 * No modo de edição os itens podem ser arrastados, redimensionados e removidos.
 */
@Composable
fun DesktopCanvas(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    builtins: List<BuiltinSpec>,
    modifier: Modifier = Modifier,
    labels: AppMenuLabels = AppMenuLabels(),
    onSwipeUp: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
    val vm = controller.viewModel
    val layout = state.layout
    val edit = controller.editMode
    val density = LocalDensity.current
    var selected by remember { mutableStateOf<String?>(null) }
    if (!edit && selected != null) selected = null

    val entries = remember(builtins, state.hiddenBuiltins, state.home, state.widgetIds) {
        builtins.filter { it.id !in state.hiddenBuiltins }.map(DesktopEntry::Builtin) +
            state.home.map(DesktopEntry::Item) +
            state.widgetIds.map(DesktopEntry::Widget)
    }

    // Tela à esquerda (feed) vem antes das telas da área de trabalho.
    val feedOffset = if (state.leftScreen != LeftScreen.NONE) 1 else 0
    var onFeed by remember { mutableStateOf(false) }

    // Gestos verticais: disparam já durante o arrasto (sem esperar soltar o dedo), ou num
    // movimento rápido ao soltar.
    val swipeThreshold = with(density) { 56.dp.toPx() }
    val swipe = remember(state.swipeDown, onSwipeUp, onSearch, swipeThreshold) {
        object : NestedScrollConnection {
            var total = 0f
            var fired = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || controller.editMode || onFeed || fired) return Offset.Zero
                total += available.y
                when {
                    total < -swipeThreshold -> { fired = true; onSwipeUp() }
                    total > swipeThreshold -> { fired = true; controller.onSwipeDown(state.swipeDown, onSearch) }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!fired && !controller.editMode && !onFeed) {
                    if (available.y < -SWIPE_VELOCITY) onSwipeUp()
                    if (available.y > SWIPE_VELOCITY) controller.onSwipeDown(state.swipeDown, onSearch)
                }
                total = 0f
                fired = false
                return Velocity.Zero
            }
        }
    }

    BoxWithConstraints(modifier.nestedScroll(swipe)) {
        val columns = layout.gridColumns
        val indicatorSpace = 20.dp
        val cellW = maxWidth / columns
        val cellH = (layout.iconSizeDp + if (layout.showLabels) 40 else 16).dp
        val cellWpx = with(density) { cellW.toPx() }
        val cellHpx = with(density) { cellH.toPx() }
        val rows = DesktopGrid.rowsFor(with(density) { (maxHeight - indicatorSpace).toPx() }, cellHpx)
        val grid = DesktopGrid(columns, rows, layout.multiPage, layout.pageCount, free = layout.freePlacement)

        val sizes = entries.map { entry ->
            entry.id to when (entry) {
                is DesktopEntry.Item -> CellSize(1f, 1f)
                is DesktopEntry.Builtin -> entry.spec.defaultSize(columns)
                is DesktopEntry.Widget -> controller.widgets.info(entry.widgetId)?.let { info ->
                    CellSize(
                        ceil(info.minWidth / cellWpx).coerceIn(1f, columns.toFloat()),
                        ceil(info.minHeight / cellHpx).coerceAtLeast(1f),
                    )
                } ?: CellSize(columns.toFloat(), 2f)
            }
        }
        val resolved = remember(sizes, state.placements, columns, rows, layout.multiPage, layout.pageCount, layout.freePlacement) {
            grid.resolve(sizes, state.placements)
        }
        // Grava as posições calculadas (novos itens, ou encaixe ao voltar para a grade).
        LaunchedEffect(resolved) {
            val changed = resolved.filter { (id, p) -> state.placements[id] != p }
            if (changed.isNotEmpty()) vm.savePlacements(changed)
        }

        val desktopPages = if (layout.multiPage) maxOf(layout.pageCount, (resolved.values.maxOfOrNull { it.page } ?: 0) + 1) else 1
        // No modo de edição com várias telas, uma tela extra à direita permite criar outra.
        val addPage = if (edit && layout.multiPage) 1 else 0
        val totalPages = feedOffset + desktopPages + addPage
        val usePager = totalPages > 1
        val pager = rememberPagerState(initialPage = feedOffset) { totalPages }
        val scope = rememberCoroutineScope()
        val currentDesktopPage = (pager.currentPage - feedOffset).coerceIn(0, desktopPages - 1)
        onFeed = feedOffset == 1 && pager.currentPage == 0

        LaunchedEffect(pager, totalPages) {
            snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }
                .collect { controller.setWallpaperScroll(it, totalPages) }
        }
        // Botão Home volta para a primeira tela da área de trabalho.
        LaunchedEffect(controller.homePresses) { if (usePager && pager.currentPage != feedOffset) pager.animateScrollToPage(feedOffset) }
        // "App do Google": ao chegar na tela à esquerda, abre o Discover e volta.
        LaunchedEffect(pager, state.leftScreen) {
            if (state.leftScreen != LeftScreen.GOOGLE) return@LaunchedEffect
            snapshotFlow { pager.settledPage }.collect { page ->
                if (page == 0) {
                    controller.openGoogleApp()
                    pager.scrollToPage(1)
                }
            }
        }

        // Colisão: enquanto um item é arrastado, os que estão no caminho se afastam (prévia);
        // ao soltar, as posições da prévia são gravadas.
        var dragging by remember { mutableStateOf<String?>(null) }
        var preview by remember { mutableStateOf<Map<String, Placement>?>(null) }
        var lastTarget by remember { mutableStateOf<Placement?>(null) }

        fun previewMove(id: String, target: Placement?) {
            if (target == null) {
                dragging = null
                preview = null
                lastTarget = null
                return
            }
            dragging = id
            val key = if (layout.freePlacement) target else grid.clamp(target.snapped())
            if (key == lastTarget) return
            lastTarget = key
            preview = grid.pushApart(id, target, resolved)
        }

        fun move(id: String, target: Placement) {
            val result = grid.pushApart(id, target, resolved)
            vm.savePlacements(result.filter { (key, p) -> resolved[key] != p })
            dragging = null
            preview = null
            lastTarget = null
        }

        fun remove(id: String) {
            when (val entry = entries.firstOrNull { it.id == id }) {
                is DesktopEntry.Builtin -> vm.setBuiltinHidden(entry.spec.id, true)
                is DesktopEntry.Widget -> controller.removeWidget(entry.widgetId)
                is DesktopEntry.Item -> when (val item = entry.item) {
                    is HomeItem.App -> vm.toggleFavorite(item.app)
                    is HomeItem.Folder -> vm.deleteFolder(item.id)
                }
                null -> Unit
            }
            selected = null
        }

        fun addScreen() {
            if (!layout.multiPage) vm.updateLayout { it.copy(multiPage = true, pageCount = maxOf(2, it.pageCount)) }
            else vm.addPage()
            scope.launch { pager.animateScrollToPage(feedOffset + desktopPages) }
        }

        @Composable
        fun Page(page: Int) {
            fun shown(id: String): Placement? = if (id != dragging) preview?.get(id) ?: resolved[id] else resolved[id]
            val onPage = entries.filter { shown(it.id)?.page == page }
            val usedRows = onPage.maxOfOrNull { resolved.getValue(it.id).let { p -> p.y + p.h } } ?: 0f
            val pageRows = if (layout.multiPage) rows.toFloat() else maxOf(rows.toFloat(), usedRows + if (edit) 2 else 0)
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(maxOf(cellH * pageRows, this@BoxWithConstraints.maxHeight - indicatorSpace)),
                ) {
                    DesktopBackground(controller, edit, onTap = { selected = null }, onAddScreen = ::addScreen, modifier = Modifier.matchParentSize())
                    // Guias só aparecem no modo grade, deixando claro qual modo está ativo.
                    if (edit && !layout.freePlacement) GridGuides(columns, pageRows.toInt(), cellW, cellH, palette.onWallpaper)
                    // Sombra de onde o item arrastado vai cair.
                    val landing = dragging?.let { preview?.get(it) }?.takeIf { it.page == page }
                    if (landing != null) {
                        Box(
                            Modifier
                                .offset(cellW * landing.x, cellH * landing.y)
                                .size(cellW * landing.w, cellH * landing.h)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.accent.copy(alpha = 0.25f))
                                .border(2.dp, palette.accent.copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
                        )
                    }
                    onPage.forEach { entry ->
                        // Chave estável: o estado do arraste fica com o item certo mesmo se a lista mudar.
                        key(entry.id) {
                            val placement = shown(entry.id)!!
                            PlacedItem(
                                placement = placement,
                                cellW = cellW,
                                cellH = cellH,
                                edit = edit,
                                selected = selected == entry.id,
                                resizable = entry !is DesktopEntry.Item && (entry !is DesktopEntry.Builtin || entry.spec.resizable),
                                accent = palette.accent,
                                onSelect = { selected = entry.id },
                                onMove = { move(entry.id, it) },
                                onDragTarget = { previewMove(entry.id, it) },
                            ) {
                                EntryContent(entry, state, controller, palette, labels, onRemove = { remove(entry.id) })
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            if (usePager) {
                HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                    when {
                        index < feedOffset -> when (state.leftScreen) {
                            LeftScreen.FEED -> FeedScreen(controller, palette)
                            else -> Box(Modifier.fillMaxSize())
                        }
                        index - feedOffset >= desktopPages -> NewScreenPage(palette, onAdd = ::addScreen)
                        else -> Page(index - feedOffset)
                    }
                }
                PageDots(totalPages, pager.currentPage, feedOffset, palette.onWallpaper, Modifier.fillMaxWidth().height(indicatorSpace))
            } else {
                Box(Modifier.weight(1f)) { Page(0) }
            }
        }

        if (edit) {
            EditBar(
                state = state,
                controller = controller,
                palette = palette,
                builtins = builtins,
                selected = selected,
                currentPage = currentDesktopPage,
                pages = desktopPages,
                onMoveToPage = { id, page ->
                    resolved[id]?.let { p ->
                        if (page >= layout.pageCount) vm.addPage()
                        move(id, p.copy(page = page))
                        scope.launch { pager.animateScrollToPage(page + feedOffset) }
                    }
                },
                onAddScreen = ::addScreen,
                onRemove = ::remove,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            )
        }
    }
}

/** Tela vazia à direita no modo de edição: toque para criar uma nova tela. */
@Composable
private fun NewScreenPage(palette: LauncherPalette, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, palette.onWallpaper.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .background(palette.onWallpaper.copy(alpha = 0.08f))
                .clickable(onClick = onAdd)
                .padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            Text("+", color = palette.onWallpaper, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text("Nova tela", color = palette.onWallpaper, fontSize = 15.sp)
        }
    }
}

private const val SWIPE_VELOCITY = 1200f

/** Fundo da tela: toque longo abre o menu da área de trabalho; um toque desfaz a seleção. */
@Composable
private fun DesktopBackground(controller: LauncherController, edit: Boolean, onTap: () -> Unit, onAddScreen: () -> Unit, modifier: Modifier) {
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }
    val origin = remember { arrayOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(
        modifier.onGloballyPositioned { origin[0] = it.positionInWindow() }.pointerInput(edit) {
            detectTapGestures(
                onTap = { pos ->
                    onTap()
                    // Toques em área vazia vão para o papel de parede animado.
                    if (!edit) controller.sendWallpaperTap(origin[0].x + pos.x, origin[0].y + pos.y)
                },
                onLongPress = { pos -> if (!edit) menuAt = with(density) { DpOffset(pos.x.toDp(), pos.y.toDp()) } },
            )
        },
    ) {
        DesktopMenu(menuAt, onDismiss = { menuAt = null }, controller = controller, onAddScreen = onAddScreen)
    }
}

@Composable
private fun DesktopMenu(at: DpOffset?, onDismiss: () -> Unit, controller: LauncherController, onAddScreen: () -> Unit) {
    Box(Modifier.offset(at?.x ?: 0.dp, at?.y ?: 0.dp)) {
        DropdownMenu(expanded = at != null, onDismissRequest = onDismiss, shape = RoundedCornerShape(12.dp)) {
            DropdownMenuItem(text = { Text("Organizar área de trabalho") }, onClick = { onDismiss(); controller.editMode = true })
            DropdownMenuItem(text = { Text("Adicionar widget") }, onClick = { onDismiss(); controller.widgetPickerOpen = true })
            DropdownMenuItem(text = { Text("Adicionar tela") }, onClick = { onDismiss(); onAddScreen() })
            val dockLayout = controller.viewModel.state.value.layout
            val showDock = dockLayout.showDock
            if (showDock) {
                DropdownMenuItem(
                    text = { Text(if (dockLayout.dockBackground) "Ocultar fundo da dock" else "Mostrar fundo da dock") },
                    onClick = { onDismiss(); controller.viewModel.updateLayout { it.copy(dockBackground = !dockLayout.dockBackground) } },
                )
            }
            DropdownMenuItem(
                text = { Text(if (showDock) "Ocultar barra / dock" else "Mostrar barra / dock") },
                onClick = { onDismiss(); controller.viewModel.updateLayout { it.copy(showDock = !showDock) } },
            )
            DropdownMenuItem(text = { Text("Papel de parede") }, onClick = { onDismiss(); controller.openWallpaperPicker() })
            DropdownMenuItem(text = { Text("Personalizar launcher") }, onClick = { onDismiss(); controller.settingsOpen = true })
        }
    }
}

@Composable
private fun GridGuides(columns: Int, rows: Int, cellW: Dp, cellH: Dp, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val w = cellW.toPx()
        val h = cellH.toPx()
        val inset = 4.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        for (r in 0 until rows) for (c in 0 until columns) {
            drawRoundRect(
                color.copy(alpha = 0.25f),
                topLeft = Offset(c * w + inset, r * h + inset),
                size = Size(w - inset * 2, h - inset * 2),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(1.dp.toPx(), pathEffect = dash),
            )
        }
    }
}

@Composable
private fun PlacedItem(
    placement: Placement,
    cellW: Dp,
    cellH: Dp,
    edit: Boolean,
    selected: Boolean,
    resizable: Boolean,
    accent: Color,
    onSelect: () -> Unit,
    onMove: (Placement) -> Unit,
    onDragTarget: (Placement?) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val cellWpx = with(density) { cellW.toPx() }
    val cellHpx = with(density) { cellH.toPx() }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var resize by remember { mutableStateOf(Offset.Zero) }
    val current by rememberUpdatedState(placement)
    val scope = rememberCoroutineScope()

    // Ao soltar, o item desliza da posição solta até a posição final.
    val ax = remember { Animatable(placement.x) }
    val ay = remember { Animatable(placement.y) }
    LaunchedEffect(placement.x, placement.y) {
        launch { ax.animateTo(placement.x, spring(dampingRatio = 0.75f, stiffness = 400f)) }
        launch { ay.animateTo(placement.y, spring(dampingRatio = 0.75f, stiffness = 400f)) }
    }

    // No modo de edição os itens "tremem" levemente, como no iOS.
    val wiggle = if (edit && drag == Offset.Zero && resize == Offset.Zero) {
        val transition = rememberInfiniteTransition(label = "tremor")
        val phase = remember { (0..140).random() }
        transition.animateFloat(
            initialValue = -1.1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(150, delayMillis = 0, easing = LinearEasing), RepeatMode.Reverse, StartOffset(phase)),
            label = "tremor",
        ).value
    } else 0f
    val lift by animateFloatAsState(if (drag != Offset.Zero) 1.06f else 1f, label = "erguer")

    val widthPx = (placement.w * cellWpx + resize.x).coerceAtLeast(cellWpx * 0.5f)
    val heightPx = (placement.h * cellHpx + resize.y).coerceAtLeast(cellHpx * 0.5f)

    Box(
        Modifier
            .offset { IntOffset((ax.value * cellWpx + drag.x).roundToInt(), (ay.value * cellHpx + drag.y).roundToInt()) }
            .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            .graphicsLayer {
                rotationZ = wiggle
                scaleX = lift
                scaleY = lift
            },
    ) {
        content()
        if (edit) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = if (selected) 0.12f else 0.04f), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onSelect() }) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDragEnd = {
                                val p = current
                                val dropX = ax.value + drag.x / cellWpx
                                val dropY = ay.value + drag.y / cellHpx
                                scope.launch {
                                    ax.snapTo(dropX)
                                    ay.snapTo(dropY)
                                    drag = Offset.Zero
                                    onMove(p.copy(x = dropX, y = dropY))
                                    // Se a posição final for a mesma de antes, nada muda no estado e o
                                    // item ficaria onde foi solto; então ele sempre desliza até a posição
                                    // atual (se ela mudar, o LaunchedEffect acima assume a animação).
                                    launch { ax.animateTo(current.x, spring(dampingRatio = 0.75f, stiffness = 400f)) }
                                    launch { ay.animateTo(current.y, spring(dampingRatio = 0.75f, stiffness = 400f)) }
                                }
                            },
                            onDragCancel = {
                                drag = Offset.Zero
                                onDragTarget(null)
                            },
                        ) { change, amount ->
                            change.consume()
                            drag += amount
                            onDragTarget(current.copy(x = ax.value + drag.x / cellWpx, y = ay.value + drag.y / cellHpx))
                        }
                    },
            )
            if (resizable) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onSelect() },
                                onDragEnd = {
                                    val p = current
                                    onMove(p.copy(w = p.w + resize.x / cellWpx, h = p.h + resize.y / cellHpx))
                                    resize = Offset.Zero
                                },
                                onDragCancel = {
                                    resize = Offset.Zero
                                    onDragTarget(null)
                                },
                            ) { change, amount ->
                                change.consume()
                                resize += amount
                                val p = current
                                onDragTarget(
                                    p.copy(
                                        w = (p.w + resize.x / cellWpx).coerceAtLeast(0.5f),
                                        h = (p.h + resize.y / cellHpx).coerceAtLeast(0.5f),
                                    ),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(accent).border(2.dp, Color.White, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun EntryContent(
    entry: DesktopEntry,
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    labels: AppMenuLabels,
    onRemove: () -> Unit,
) {
    when (entry) {
        is DesktopEntry.Builtin -> ComponentWithMenu(entry.spec.name, controller, onRemove) {
            entry.spec.content(Modifier.fillMaxSize().padding(5.dp))
        }
        is DesktopEntry.Widget -> ComponentWithMenu(
            "Widget",
            controller,
            onRemove,
            onConfigure = controller.widgets.info(entry.widgetId)?.configure?.let { { controller.reconfigureWidget(entry.widgetId) } },
        ) {
            AppWidget(
                id = entry.widgetId,
                widgets = controller.widgets,
                modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(16.dp)),
            )
        }
        is DesktopEntry.Item -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val item = entry.item) {
                is HomeItem.App -> AppTile(
                    app = item.app,
                    state = state,
                    controller = controller,
                    labelStyle = palette.wallpaperLabel,
                    labels = labels,
                    iconBackground = palette.iconBackground,
                )
                is HomeItem.Folder -> FolderTile(
                    folder = item,
                    state = state,
                    controller = controller,
                    background = palette.folder,
                    labelStyle = palette.wallpaperLabel,
                    panelColor = palette.panel,
                    panelTextColor = palette.panelText,
                    labels = labels,
                )
            }
        }
    }
}

/**
 * Widgets e componentes do tema com menu de toque longo (remover, organizar, configurar).
 * O toque longo é detectado antes do conteúdo, porque widgets Android consomem os toques.
 */
@Composable
private fun ComponentWithMenu(
    name: String,
    controller: LauncherController,
    onRemove: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }
    val density = LocalDensity.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxSize()
            .longPressFirst(enabled = !controller.editMode) { pos ->
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                menuAt = with(density) { DpOffset(pos.x.toDp(), pos.y.toDp()) }
            },
    ) {
        content()
        Box(Modifier.offset(menuAt?.x ?: 0.dp, menuAt?.y ?: 0.dp)) {
            DropdownMenu(expanded = menuAt != null, onDismissRequest = { menuAt = null }, shape = RoundedCornerShape(12.dp)) {
                Text(name, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                DropdownMenuItem(text = { Text("Remover") }, onClick = { menuAt = null; onRemove() })
                DropdownMenuItem(text = { Text("Mover ou redimensionar") }, onClick = { menuAt = null; controller.editMode = true })
                if (onConfigure != null) DropdownMenuItem(text = { Text("Configurar widget") }, onClick = { menuAt = null; onConfigure() })
            }
        }
    }
}

/** Detecta toque longo na passagem inicial (antes dos filhos) e, se disparar, consome o gesto. */
private fun Modifier.longPressFirst(enabled: Boolean, onLongPress: (Offset) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                true
            }
            if (released == null) {
                onLongPress(down.position)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }

@Composable
private fun PageDots(count: Int, current: Int, feedOffset: Int, color: Color, modifier: Modifier) {
    if (count <= 1) return Box(modifier)
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == current
            val size by animateDpAsState(if (active) 8.dp else 6.dp, label = "ponto")
            val alpha by animateFloatAsState(if (active) 0.95f else 0.45f, label = "ponto")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (i < feedOffset) size * 1.6f else size, height = size)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun EditBar(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    builtins: List<BuiltinSpec>,
    selected: String?,
    currentPage: Int,
    pages: Int,
    onMoveToPage: (String, Int) -> Unit,
    onAddScreen: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = controller.viewModel
    val layout = state.layout
    var addOpen by remember { mutableStateOf(false) }
    val selectedPage = selected?.let { state.placements[it]?.page } ?: currentPage

    Row(
        modifier
            .padding(horizontal = 8.dp)
            .glass(palette.panel, RoundedCornerShape(22.dp), palette.panelStroke)
            .horizontalScroll(rememberScrollState())
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill("Concluído", palette, filled = true) { controller.editMode = false }
        if (selected != null) {
            if (layout.multiPage) {
                if (selectedPage > 0) Pill("◀ Tela", palette) { onMoveToPage(selected, selectedPage - 1) }
                Pill("Tela ▶", palette) { onMoveToPage(selected, selectedPage + 1) }
            }
            Pill("Remover", palette) { onRemove(selected) }
        } else {
            // Um único botão mostra o modo atual; tocar alterna entre grade e livre.
            Pill(if (layout.freePlacement) "Posição: Livre" else "Posição: Grade ▦", palette) {
                vm.updateLayout { it.copy(freePlacement = !it.freePlacement) }
                controller.toast(if (layout.freePlacement) "Grade: os itens se encaixam nas células" else "Livre: os itens ficam onde você soltar")
            }
            Box {
                Pill("+ Adicionar", palette) { addOpen = true }
                DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }, shape = RoundedCornerShape(12.dp)) {
                    DropdownMenuItem(text = { Text("Widget Android…") }, onClick = { addOpen = false; controller.widgetPickerOpen = true })
                    builtins.filter { it.id in state.hiddenBuiltins }.forEach { spec ->
                        DropdownMenuItem(text = { Text(spec.name) }, onClick = { addOpen = false; vm.setBuiltinHidden(spec.id, false) })
                    }
                }
            }
            Pill("+ Tela", palette, onClick = onAddScreen)
            if (layout.multiPage && pages > 1) Pill("Remover tela", palette) { vm.removePage(currentPage) }
        }
    }
}

@Composable
private fun Pill(text: String, palette: LauncherPalette, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (filled) palette.onAccent else palette.panelText,
        fontSize = 13.sp,
        fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) palette.accent else palette.panelText.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
