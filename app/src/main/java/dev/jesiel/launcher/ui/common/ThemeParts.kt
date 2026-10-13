package dev.jesiel.launcher.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.colorScheme) {
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
    val apps: @Composable () -> Unit = {
        state.dock.forEach { app ->
            AppTile(
                app = app,
                state = state,
                controller = controller,
                iconSize = iconSize,
                showLabel = false,
                labels = labels,
                iconBackground = palette.iconBackground,
                onLaunched = onLaunched,
            )
        }
    }
    val base = modifier.glass(palette.dock, shape, palette.dockStroke).padding(6.dp)
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
    Box(
        modifier
            .semantics { contentDescription = description }
            .padding(4.dp)
            .size(size)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f)) }
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
