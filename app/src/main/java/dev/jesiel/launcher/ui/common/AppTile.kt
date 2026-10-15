package dev.jesiel.launcher.ui.common

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.AppInfo

/** Textos do menu que dependem de como o tema chama o seu dock ("barra de tarefas", "dock"…). */
data class AppMenuLabels(
    val pinToDock: String = "Fixar no dock",
    val unpinFromDock: String = "Remover do dock",
)

/**
 * Ícone de app com rótulo e menu de toque longo. Comum a todos os temas; o visual do texto é
 * definido por quem chama.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppInfo,
    state: LauncherUiState,
    controller: LauncherController,
    modifier: Modifier = Modifier,
    iconSize: Dp = state.layout.iconSizeDp.dp,
    showLabel: Boolean = state.layout.showLabels,
    labelStyle: TextStyle = TextStyle(color = Color.White, fontSize = 12.sp),
    /** Linhas do nome (na tela inicial, escolhido nos ajustes). */
    labelLines: Int = 1,
    labels: AppMenuLabels = AppMenuLabels(),
    folderId: String? = null,
    iconBackground: Color? = null,
    /** Efeito de "encolher" ao tocar; em listas roláveis usa-se o ripple, mais leve. */
    pressEffect: Boolean = true,
    onLaunched: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var folderPickerOpen by remember { mutableStateOf(false) }
    // Guardado fora do estado do Compose: atualizar a cada quadro de rolagem recompunha o ícone.
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun bounds(): Rect? = coordinates[0]?.takeIf { it.isAttached }?.boundsInWindow()?.let {
        Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
    }
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val iconStyle = LocalIconStyle.current
    // Segurar e arrastar o ícone fecha o menu que o toque longo abriu.
    androidx.compose.runtime.LaunchedEffect(controller.desktopDragTick) { menuOpen = false }
    val dragHome = LocalDragToHome.current

    Box(modifier.then(if (dragHome) Modifier.dragToHome(app, controller) else Modifier)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .then(if (pressEffect) Modifier.pressScale(interaction) else Modifier)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = interaction,
                    indication = if (pressEffect) null else ripple(),
                    onClick = {
                        controller.launch(app, bounds())
                        onLaunched()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(vertical = 6.dp, horizontal = 2.dp),
        ) {
            val iconModifier = Modifier
                .size(iconSize)
                .onPlaced { coordinates[0] = it }
                .then(iconStyle.brackets?.let { Modifier.targetBrackets(it) } ?: Modifier)
            if (iconBackground != null) {
                Box(
                    iconModifier.clip(RoundedCornerShape(iconSize * 0.24f)).background(iconBackground).padding(iconSize * 0.14f),
                ) { Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.matchParentSize(), colorFilter = iconStyle.filter) }
            } else {
                Image(bitmap = app.icon, contentDescription = app.label, modifier = iconModifier, colorFilter = iconStyle.filter)
            }
            if (showLabel) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (iconStyle.uppercaseLabels) app.label.uppercase() else app.label,
                    style = labelStyle,
                    maxLines = labelLines.coerceIn(1, 3),
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(iconSize + 28.dp),
                )
            }
        }

        // O menu só é composto quando aberto: em listas longas isso deixa cada item mais leve.
        if (menuOpen) AppMenu(
            expanded = true,
            app = app,
            state = state,
            controller = controller,
            labels = labels,
            folderId = folderId,
            onDismiss = { menuOpen = false },
            onMoveToFolder = { folderPickerOpen = true },
        )
    }

    if (folderPickerOpen) {
        MoveToFolderDialog(
            app = app,
            folders = state.folders.filter { it.id != folderId },
            onDismiss = { folderPickerOpen = false },
            onPick = { id, name ->
                controller.viewModel.moveToFolder(app, id, name)
                folderPickerOpen = false
            },
        )
    }
}

@Composable
private fun AppMenu(
    expanded: Boolean,
    app: AppInfo,
    state: LauncherUiState,
    controller: LauncherController,
    labels: AppMenuLabels,
    folderId: String?,
    onDismiss: () -> Unit,
    onMoveToFolder: () -> Unit,
) {
    val vm = controller.viewModel
    val isFavorite = app.key in state.favoriteKeys
    val inDock = app.key in state.dockKeys
    val looseFavorite = state.home.any { it.id == app.key }

    @Composable
    fun Item(text: String, action: () -> Unit) =
        DropdownMenuItem(text = { Text(text) }, onClick = { onDismiss(); action() })

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(12.dp)) {
        Text(
            app.label,
            style = TextStyle(fontSize = 13.sp, color = Color.Gray),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider()
        Item(if (isFavorite) "Desafixar da tela inicial" else "Fixar na tela inicial") { vm.toggleFavorite(app) }
        Item(if (inDock) labels.unpinFromDock else labels.pinToDock) { vm.toggleDock(app) }
        Item("Mover para pasta…", onMoveToFolder)
        if (folderId != null) Item("Tirar da pasta") { vm.removeFromFolder(app, folderId) }
        if (looseFavorite) {
            Item("Mover para trás") { vm.moveFavorite(app, -1) }
            Item("Mover para frente") { vm.moveFavorite(app, 1) }
        }
        if (looseFavorite) Item("Organizar área de trabalho") { controller.editMode = true }
        HorizontalDivider()
        Item("Informações do app") { controller.openAppInfo(app) }
        Item("Desinstalar") { controller.uninstall(app) }
    }
}

/**
 * Variante em linha (ícone + textos) para listas, como "Todos os apps" e resultados de busca.
 * Usa o mesmo menu de toque longo de [AppTile].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListRow(
    app: AppInfo,
    state: LauncherUiState,
    controller: LauncherController,
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp,
    labels: AppMenuLabels = AppMenuLabels(),
    onLaunched: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var folderPickerOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    androidx.compose.runtime.LaunchedEffect(controller.desktopDragTick) { menuOpen = false }
    val dragHome = LocalDragToHome.current

    Box(modifier.then(if (dragHome) Modifier.dragToHome(app, controller) else Modifier)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = {
                        controller.launch(app)
                        onLaunched()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(iconSize), colorFilter = LocalIconStyle.current.filter)
            content()
        }
        // O menu só é composto quando aberto: em listas longas isso deixa cada item mais leve.
        if (menuOpen) AppMenu(
            expanded = true,
            app = app,
            state = state,
            controller = controller,
            labels = labels,
            folderId = null,
            onDismiss = { menuOpen = false },
            onMoveToFolder = { folderPickerOpen = true },
        )
    }

    if (folderPickerOpen) {
        MoveToFolderDialog(
            app = app,
            folders = state.folders,
            onDismiss = { folderPickerOpen = false },
            onPick = { id, name ->
                controller.viewModel.moveToFolder(app, id, name)
                folderPickerOpen = false
            },
        )
    }
}

/** Cantoneiras de mira em volta do ícone (tema Retrô futurista). */
fun Modifier.targetBrackets(color: Color): Modifier = this.drawWithContent {
    drawContent()
    val pad = size.minDimension * 0.1f
    val len = size.minDimension * 0.22f
    val w = size.minDimension * 0.045f
    val l = -pad
    val t = -pad
    val r = size.width + pad
    val b = size.height + pad
    listOf(
        Offset(l, t) to Offset(1f, 1f), Offset(r, t) to Offset(-1f, 1f),
        Offset(l, b) to Offset(1f, -1f), Offset(r, b) to Offset(-1f, -1f),
    ).forEach { (p, d) ->
        drawLine(color, p, Offset(p.x + len * d.x, p.y), w)
        drawLine(color, p, Offset(p.x, p.y + len * d.y), w)
    }
}
