package dev.jesiel.launcher.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.MenuIcon

/**
 * Ícone do botão que abre o menu de apps, conforme a escolha do usuário em Personalizar.
 * [default] desenha o ícone original do tema.
 */
@Composable
fun MenuButtonIcon(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    size: Dp,
    tint: Color = palette.panelText,
    default: @Composable () -> Unit,
) {
    when (state.layout.menuIcon) {
        MenuIcon.DEFAULT -> default()
        MenuIcon.FLAT -> Canvas(Modifier.size(size * 0.85f)) {
            val gap = this.size.width * 0.1f
            val cell = (this.size.width - gap) / 2
            for (r in 0..1) for (c in 0..1) {
                drawRect(palette.accent, Offset(c * (cell + gap), r * (cell + gap)), Size(cell, cell))
            }
        }
        MenuIcon.ORB -> Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(palette.accent.copy(alpha = 0.9f), palette.accent, Color(0xFF0A2A55)),
                            center = Offset(this.size.width * 0.4f, this.size.height * 0.35f),
                        ),
                    )
                    drawCircle(
                        Brush.verticalGradient(listOf(Color(0x88FFFFFF), Color.Transparent), endY = this.size.height * 0.55f),
                        radius = this.size.width * 0.42f,
                        center = Offset(this.size.width / 2, this.size.height * 0.36f),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.46f)) {
                val gap = this.size.width * 0.1f
                val cell = (this.size.width - gap) / 2
                for (r in 0..1) for (c in 0..1) {
                    drawRoundRect(Color.White, Offset(c * (cell + gap), r * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.2f))
                }
            }
        }
        MenuIcon.GRID -> Icon(Icons.Default.Apps, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        MenuIcon.CUSTOM -> {
            val file = controller.viewModel.menuIconFile(state.themeId)
            val bitmap = remember(file.path, state.menuIconVersion) {
                runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) {
                Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.25f)))
            } else {
                default()
            }
        }
    }
}

