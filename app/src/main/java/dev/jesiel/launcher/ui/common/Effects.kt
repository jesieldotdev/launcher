package dev.jesiel.launcher.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.jesiel.launcher.LauncherController
import kotlinx.coroutines.delay

/** Entrada escalonada: o item sobe e aparece com um pequeno atraso conforme a posição. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 22L).coerceAtMost(330L))
        progress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 20.dp.toPx()
    }
}

/** Encolhe levemente enquanto pressionado e volta com mola. */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.88f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "toque")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Desfoca o que fica atrás de um painel aberto. O conteúdo desenhado pelo launcher é desfocado
 * aqui (Android 12+); o papel de parede do sistema, pela janela.
 */
@Composable
fun Modifier.backdrop(controller: LauncherController, active: Boolean, enabled: Boolean): Modifier {
    val on = active && enabled
    val radius by animateDpAsState(if (on) 28.dp else 0.dp, tween(260), label = "desfoque")
    LaunchedEffect(on) { controller.setWindowBlur(if (on) 70 else 0) }
    return if (radius > 0.dp) blur(radius) else this
}

/** Painéis translúcidos (efeitos de transparência) ou opacos. */
fun LauncherPalette.withTransparency(on: Boolean): LauncherPalette =
    if (on) copy(panel = panel.copy(alpha = if (dark) 0.80f else 0.76f), dock = dock)
    else copy(panel = panel.copy(alpha = 1f), dock = dock.copy(alpha = maxOf(dock.alpha, 0.94f)))
