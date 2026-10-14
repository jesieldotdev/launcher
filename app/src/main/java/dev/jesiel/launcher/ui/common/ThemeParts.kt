package dev.jesiel.launcher.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState

/** Base de um tema: paleta, cores do Material e o botão Voltar fechando painéis comuns. */
@Composable
fun ThemeRoot(controller: LauncherController, palette: LauncherPalette, content: @Composable BoxScope.() -> Unit) {
    BackHandler(enabled = controller.controlCenterOpen) { controller.controlCenterOpen = false }
    BackHandler(enabled = controller.editMode) { controller.editMode = false }
    androidx.compose.runtime.SideEffect { controller.palette = palette }
    CompositionLocalProvider(LocalPalette provides palette, LocalIconStyle provides palette.iconStyle) {
        val typography = remember(palette.font) { palette.font?.let(::typographyWith) ?: androidx.compose.material3.Typography() }
        MaterialTheme(colorScheme = palette.colorScheme, typography = typography) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

/** Dock com os apps fixados, horizontal ou vertical, com espaços para botões do tema. */
@Composable
fun AppDock(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    iconSize: Dp = 46.dp,
    shape: Shape = RoundedCornerShape(26.dp),
    labels: AppMenuLabels = AppMenuLabels(),
    onLaunched: () -> Unit = {},
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    // Dock em HTML (escolhida no Estúdio): substitui a do tema, inclusive os botões dele.
    state.dockHtml?.let { widget ->
        HtmlDock(widget, state, controller, palette, modifier, vertical)
        return
    }
    val size = if (state.layout.dockIconSizeDp > 0) state.layout.dockIconSizeDp.dp else iconSize
    // Segurar e arrastar reordena: a ordem muda ao vivo e é salva ao soltar.
    val keys = state.dock.map { it.key }
    var order by remember { mutableStateOf(keys) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var extent by remember { mutableFloatStateOf(0f) }
    if (dragging == null && order != keys) order = keys
    val byKey = state.dock.associateBy { it.key }
    val haptics = LocalHapticFeedback.current
    fun step(delta: Float) {
        val key = dragging ?: return
        offset += delta
        if (extent <= 0f) return
        val index = order.indexOf(key)
        val target = when {
            offset > extent / 2 && index < order.lastIndex -> index + 1
            offset < -extent / 2 && index > 0 -> index - 1
            else -> return
        }
        order = order.toMutableList().apply { add(target, removeAt(index)) }
        offset -= if (target > index) extent else -extent
    }
    val apps: @Composable () -> Unit = {
        order.mapNotNull(byKey::get).forEach { app ->
            key(app.key) {
                val lifted = dragging == app.key
                AppTile(
                    app = app,
                    state = state,
                    controller = controller,
                    modifier = Modifier
                        .onSizeChanged { extent = (if (vertical) it.height else it.width).toFloat() }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            if (lifted) {
                                if (vertical) translationY = offset else translationX = offset
                                scaleX = 1.12f
                                scaleY = 1.12f
                            }
                        }
                        .holdToDrag(
                            onStart = {
                                controller.desktopDragTick++
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = app.key
                                offset = 0f
                            },
                            onDrag = { step(if (vertical) it.y else it.x) },
                            onEnd = {
                                dragging = null
                                offset = 0f
                                if (order != keys) controller.viewModel.reorderDock(order)
                            },
                            onCancel = {
                                dragging = null
                                offset = 0f
                                order = keys
                            },
                        ),
                    iconSize = size,
                    showLabel = false,
                    labels = labels,
                    iconBackground = palette.iconBackground,
                    onLaunched = onLaunched,
                )
            }
        }
    }
    val base = modifier.reportDockBounds(controller).dockPanel(state.layout.dockBackground, palette, shape).padding(6.dp)
    if (vertical) {
        Column(base, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            leading()
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { apps() }
            trailing()
        }
    } else {
        Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
            leading()
            Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { apps() }
            trailing()
        }
    }
}

/**
 * Dock feita em HTML: ocupa a largura (ou, na vertical, a altura) que o tema dá à dock, com a
 * altura configurada nos ajustes. Os apps vêm de `launcher.appsDock`.
 */
@Composable
fun HtmlDock(
    widget: dev.jesiel.launcher.data.HtmlWidget,
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val thickness = state.dockHtmlHeight.dp
    val extra = remember(vertical) { mapOf("dockVertical" to if (vertical) "sim" else "") }
    HtmlWidgetView(
        widget,
        controller,
        palette,
        modifier
            .reportDockBounds(controller)
            .then(if (vertical) Modifier.width(thickness) else Modifier.fillMaxWidth().height(thickness)),
        extra = extra,
    )
}

/** Informa ao controlador onde a dock está, para o modo janela abrir os apps acima dela. */
@Composable
fun Modifier.reportDockBounds(controller: LauncherController): Modifier {
    androidx.compose.runtime.DisposableEffect(controller) { onDispose { controller.dockBounds = null } }
    return onGloballyPositioned {
        val r = it.boundsInWindow()
        controller.dockBounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
    }
}

/** Fundo da dock/barra, que pode ser ocultado (some com uma transição suave). */
@Composable
fun Modifier.dockPanel(visible: Boolean, palette: LauncherPalette, shape: Shape): Modifier {
    val color by animateColorAsState(if (visible) palette.dock else Color.Transparent, tween(250), label = "fundo da dock")
    val stroke by animateColorAsState(if (visible) palette.dockStroke else Color.Transparent, tween(250), label = "borda da dock")
    return glass(color, shape, stroke)
}

/** Sem o fundo da barra, textos e ícones dela passam a usar a cor sobre o papel de parede. */
fun LauncherPalette.forDock(background: Boolean): LauncherPalette =
    if (background) this else copy(panelText = onWallpaper, panelTextSecondary = onWallpaper.copy(alpha = 0.8f))

/** Botão de ícone usado em docks e barras (gaveta de apps, painel…). */
@Composable
fun BarButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    background: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .semantics { contentDescription = description }
            .padding(4.dp)
            .pressScale(interaction, 0.85f)
            .size(size)
            .clip(shape)
            .background(background)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f)) }
}

/** Botão do menu de apps com o ícone escolhido pelo usuário (ou o do tema). */
@Composable
fun MenuBarButton(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    description: String,
    defaultIcon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    size: Dp = 46.dp,
    background: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .semantics { contentDescription = description }
            .padding(4.dp)
            .pressScale(interaction, 0.85f)
            .size(size)
            .clip(shape)
            .background(if (state.layout.menuIcon == dev.jesiel.launcher.data.MenuIcon.DEFAULT) background else Color.Transparent)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MenuButtonIcon(state, controller, palette, size * 0.8f, tint) {
            Icon(defaultIcon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** Separador fino para docks. */
@Composable
fun DockDivider(color: Color, vertical: Boolean) {
    Box(
        Modifier
            .padding(if (vertical) 6.dp else 4.dp)
            .then(if (vertical) Modifier.width(28.dp).height(1.dp) else Modifier.width(1.dp).height(28.dp))
            .background(color),
    )
}

/** Tipografia do Material com a fonte do tema em todos os estilos. */
private fun typographyWith(font: androidx.compose.ui.text.font.FontFamily): androidx.compose.material3.Typography {
    val t = androidx.compose.material3.Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = font), displayMedium = t.displayMedium.copy(fontFamily = font),
        displaySmall = t.displaySmall.copy(fontFamily = font), headlineLarge = t.headlineLarge.copy(fontFamily = font),
        headlineMedium = t.headlineMedium.copy(fontFamily = font), headlineSmall = t.headlineSmall.copy(fontFamily = font),
        titleLarge = t.titleLarge.copy(fontFamily = font), titleMedium = t.titleMedium.copy(fontFamily = font),
        titleSmall = t.titleSmall.copy(fontFamily = font), bodyLarge = t.bodyLarge.copy(fontFamily = font),
        bodyMedium = t.bodyMedium.copy(fontFamily = font), bodySmall = t.bodySmall.copy(fontFamily = font),
        labelLarge = t.labelLarge.copy(fontFamily = font), labelMedium = t.labelMedium.copy(fontFamily = font),
        labelSmall = t.labelSmall.copy(fontFamily = font),
    )
}
