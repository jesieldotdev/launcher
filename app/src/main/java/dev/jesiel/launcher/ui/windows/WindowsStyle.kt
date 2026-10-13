package dev.jesiel.launcher.ui.windows

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.ui.common.AppMenuLabels

object WinColors {
    val Accent = Color(0xFF0067C0)
    val AccentBright = Color(0xFF3B8EEA)
    val LogoTop = Color(0xFF39A6FF)
    val LogoBottom = Color(0xFF0B5CD5)
    val Text = Color(0xFF1B1B1F)
    val TextSecondary = Color(0xFF5F6670)
    val Panel = Color(0xFCEEF3FA)
    val PanelStroke = Color(0x66FFFFFF)
    val Taskbar = Color(0xD9EAF1FB)
    val CardOnWallpaper = Color(0x332A6FD8)
    val CardStroke = Color(0x55FFFFFF)
    val Hover = Color(0x14000000)
    val Divider = Color(0x1F000000)
}

val WindowsColorScheme = lightColorScheme(
    primary = WinColors.Accent,
    onPrimary = Color.White,
    secondary = WinColors.AccentBright,
    surface = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFF7F9FC),
    onSurface = WinColors.Text,
    surfaceVariant = Color(0xFFE6ECF4),
    onSurfaceVariant = WinColors.TextSecondary,
    background = Color(0xFFF3F6FB),
    onBackground = WinColors.Text,
)

val WindowsMenuLabels = AppMenuLabels(
    pinToDock = "Fixar na barra de tarefas",
    unpinFromDock = "Desafixar da barra de tarefas",
)

/** Rótulo branco com sombra, legível sobre o papel de parede. */
val WallpaperLabel = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    shadow = Shadow(Color(0x99000000), Offset(0f, 1f), 4f),
)

val PanelLabel = TextStyle(color = WinColors.Text, fontSize = 12.sp)

/** Superfície translúcida no estilo "Mica/Acrylic" do Windows 11. */
fun Modifier.acrylic(color: Color, shape: Shape, stroke: Color = WinColors.PanelStroke): Modifier =
    this.clip(shape).background(color).border(1.dp, stroke, shape)

/** Logotipo de quatro quadrados, desenhado em vez de usar uma imagem de marca. */
@Composable
fun WindowsLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val gap = this.size.width * 0.08f
        val cell = (this.size.width - gap) / 2
        val brush = Brush.linearGradient(listOf(WinColors.LogoTop, WinColors.LogoBottom), Offset.Zero, Offset(0f, this.size.height))
        val radius = CornerRadius(cell * 0.12f)
        for (row in 0..1) for (col in 0..1) {
            drawRoundRect(brush, Offset(col * (cell + gap), row * (cell + gap)), Size(cell, cell), radius)
        }
    }
}

/** Papel de parede próprio do tema, inspirado no "bloom" azul do Windows 11. */
@Composable
fun WindowsBloomWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFCFE0F6), Color(0xFF8DB4EA), Color(0xFF3F7BD6))))
        val center = Offset(size.width * 0.58f, size.height * 0.62f)
        val petal = Size(size.width * 1.25f, size.width * 0.5f)
        val petals = listOf(
            -70f to listOf(Color(0xFF0A3BB0), Color(0xFF2C7BEA), Color(0x00A9D2FF)),
            -38f to listOf(Color(0xFF0B4DCC), Color(0xFF4D9BF5), Color(0x00CFE4FF)),
            -8f to listOf(Color(0xFF0636A0), Color(0xFF1F6FE0), Color(0x0090C2FF)),
            22f to listOf(Color(0xFF0C57D8), Color(0xFF6AB0FA), Color(0x00E3F0FF)),
            52f to listOf(Color(0xFF0842B8), Color(0xFF3488F0), Color(0x00B8DAFF)),
            200f to listOf(Color(0xFF0A3BB0), Color(0xFF3D8CF2), Color(0x00C4E0FF)),
            235f to listOf(Color(0xFF0B4DCC), Color(0xFF5AA4F7), Color(0x00D8EAFF)),
        )
        petals.forEach { (angle, colors) ->
            rotate(angle, pivot = center) {
                val topLeft = Offset(center.x - petal.width * 0.08f, center.y - petal.height / 2)
                drawOval(
                    brush = Brush.horizontalGradient(colors, startX = topLeft.x, endX = topLeft.x + petal.width),
                    topLeft = topLeft,
                    size = petal,
                    alpha = 0.85f,
                )
            }
        }
        drawCircle(
            Brush.radialGradient(listOf(Color(0x88FFFFFF), Color.Transparent), center = center, radius = size.width * 0.3f),
            radius = size.width * 0.3f,
            center = center,
        )
    }
}
