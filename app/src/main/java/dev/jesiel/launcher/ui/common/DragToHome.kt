package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.data.AppInfo
import kotlin.math.roundToInt

/** Verdadeiro dentro das listas de apps: lá, segurar e arrastar leva o app para a tela inicial. */
val LocalDragToHome = staticCompositionLocalOf { false }

/** App sendo levado da lista para a tela inicial, e onde o dedo está (na janela). */
data class HomeDrag(val app: AppInfo, val position: Offset)

/**
 * Segurar e arrastar (como na área de trabalho): a lista fecha, a tela inicial abre no modo de
 * edição e o app segue o dedo até ser solto. Quem acompanha o dedo depois disso é a raiz da
 * tela ([trackHomeDrag]), porque a lista sai da tela no meio do gesto.
 */
fun Modifier.dragToHome(app: AppInfo, controller: LauncherController): Modifier = composed {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { coords[0] = it }
        .holdToDrag(
            onStart = {
                val center = coords[0]?.takeIf { it.isAttached }?.boundsInWindow()?.center ?: Offset.Zero
                controller.startHomeDrag(app, center)
            },
            onDrag = {},
            onEnd = {},
            onCancel = {},
        )
}

/**
 * Na raiz da tela: acompanha o dedo enquanto um app vem da lista (na passagem inicial, sem
 * consumir nada) e solta na área de trabalho ao levantar o dedo.
 */
fun Modifier.trackHomeDrag(controller: LauncherController): Modifier = pointerInput(controller) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (controller.homeDrag == null) continue
            val change = event.changes.firstOrNull() ?: continue
            if (change.pressed) controller.moveHomeDrag(change.position) else controller.dropHomeDrag(change.position)
        }
    }
}

/** O ícone que segue o dedo. */
@Composable
fun HomeDragGhost(controller: LauncherController) {
    val drag = controller.homeDrag ?: return
    val half = with(LocalDensity.current) { 32.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((drag.position.x - half).roundToInt(), (drag.position.y - half * 1.6f).roundToInt()) }
            .size(64.dp)
            .graphicsLayer {
                scaleX = 1.1f
                scaleY = 1.1f
                shadowElevation = 16f
                alpha = 0.95f
            },
    ) {
        Image(drag.app.icon, contentDescription = null, modifier = Modifier.size(64.dp))
    }
}

/** Menu de um app aberto por um widget HTML: as mesmas ações de segurar um app no launcher. */
@Composable
fun HtmlAppMenu(app: AppInfo, state: dev.jesiel.launcher.LauncherUiState, controller: LauncherController) {
    val vm = controller.viewModel
    fun close() { controller.htmlAppMenu = null }
    val onHome = app.key in state.favoriteKeys
    val inDock = app.key in state.dockKeys
    androidx.compose.material3.AlertDialog(
        onDismissRequest = ::close,
        icon = { Image(app.icon, contentDescription = null, modifier = Modifier.size(48.dp)) },
        title = { androidx.compose.material3.Text(app.label) },
        text = {
            androidx.compose.foundation.layout.Column {
                @Composable
                fun item(label: String, action: () -> Unit) = androidx.compose.material3.TextButton(
                    onClick = { close(); action() },
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text(label, modifier = Modifier.fillMaxWidth()) }
                item("Abrir") { controller.launchPackage(app.packageName) }
                item(if (onHome) "Tirar da tela inicial" else "Pôr na tela inicial") {
                    if (onHome) vm.toggleFavorite(app) else vm.addToHome(app)
                    controller.toast(if (onHome) "${app.label} saiu da tela inicial" else "${app.label} na tela inicial")
                }
                item(if (inDock) "Remover da dock" else "Fixar na dock") { vm.toggleDock(app) }
                item("Informações do app") { controller.openAppInfo(app) }
                item("Desinstalar") { controller.uninstall(app) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = ::close) { androidx.compose.material3.Text("Fechar") } },
    )
}
