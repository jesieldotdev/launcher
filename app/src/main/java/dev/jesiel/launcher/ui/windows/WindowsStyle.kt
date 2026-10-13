package dev.jesiel.launcher.ui.windows

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesiel.launcher.ui.common.AppMenuLabels
import dev.jesiel.launcher.ui.common.LauncherPalette

private val LogoTop = Color(0xFF39A6FF)
private val LogoBottom = Color(0xFF0B5CD5)

/** Paleta inspirada no Windows 11, nas variantes clara e escura. */
fun windowsPalette(dark: Boolean): LauncherPalette = if (dark) {
    LauncherPalette(
        dark = true,
        accent = Color(0xFF4CC2FF),
        onAccent = Color(0xFF00213A),
        card = Color(0x8C1C2433),
        cardStroke = Color(0x33FFFFFF),
        cardText = Color.White,
        cardTextSecondary = Color(0xCCFFFFFF),
        panel = Color(0xFC202226),
        panelStroke = Color(0x1FFFFFFF),
        panelText = Color(0xFFF3F3F3),
        panelTextSecondary = Color(0xFFB4B7BD),
        tile = Color(0x1AFFFFFF),
        field = Color(0xFF2C2E33),
        dock = Color(0xE61C1F24),
        dockStroke = Color(0x1FFFFFFF),
        folder = Color(0x40FFFFFF),
    )
} else {
    LauncherPalette(
        dark = false,
        accent = Color(0xFF0067C0),
        card = Color(0x803B7DDB),
        cardStroke = Color(0x55FFFFFF),
        cardText = Color.White,
        cardTextSecondary = Color(0xD9FFFFFF),
        panel = Color(0xFCEEF3FA),
        panelStroke = Color(0x66FFFFFF),
        panelText = Color(0xFF1B1B1F),
        panelTextSecondary = Color(0xFF5F6670),
        tile = Color(0x14000000),
        field = Color.White,
        dock = Color(0xD9EAF1FB),
        dockStroke = Color(0x66FFFFFF),
        folder = Color(0x55FFFFFF),
    )
}

/** Cor do cartão "Recente", mais opaco que os demais. */
fun recentCardColor(dark: Boolean) = if (dark) Color(0xD91E2230) else Color(0x8CE9F0FA)

val WindowsMenuLabels = AppMenuLabels(
    pinToDock = "Fixar na barra de tarefas",
    unpinFromDock = "Desafixar da barra de tarefas",
)

val WindowsShape = RoundedCornerShape(22.dp)

/** Logotipo de quatro quadrados, desenhado em vez de usar uma imagem de marca. */
@Composable
fun WindowsLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val gap = this.size.width * 0.08f
        val cell = (this.size.width - gap) / 2
        val brush = Brush.linearGradient(listOf(LogoTop, LogoBottom), Offset.Zero, Offset(0f, this.size.height))
        val radius = CornerRadius(cell * 0.12f)
        for (row in 0..1) for (col in 0..1) {
            drawRoundRect(brush, Offset(col * (cell + gap), row * (cell + gap)), Size(cell, cell), radius)
        }
    }
}

/** Papel de parede próprio do tema, inspirado no "bloom" azul do Windows 11. */
@Composable
fun WindowsBloomWallpaper(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val base = if (dark) listOf(Color(0xFF0A1022), Color(0xFF0B1B44), Color(0xFF071233))
        else listOf(Color(0xFFCFE0F6), Color(0xFF8DB4EA), Color(0xFF3F7BD6))
        drawRect(Brush.verticalGradient(base))
        val center = Offset(size.width * 0.58f, size.height * 0.62f)
        val petal = Size(size.width * 1.25f, size.width * 0.5f)
        val fade = if (dark) Color(0x00102A66) else Color(0x00C4E0FF)
        val petals = listOf(
            -70f to listOf(Color(0xFF0A3BB0), Color(0xFF2C7BEA), fade),
            -38f to listOf(Color(0xFF0B4DCC), Color(0xFF4D9BF5), fade),
            -8f to listOf(Color(0xFF0636A0), Color(0xFF1F6FE0), fade),
            22f to listOf(Color(0xFF0C57D8), Color(0xFF6AB0FA), fade),
            52f to listOf(Color(0xFF0842B8), Color(0xFF3488F0), fade),
            200f to listOf(Color(0xFF0A3BB0), Color(0xFF3D8CF2), fade),
            235f to listOf(Color(0xFF0B4DCC), Color(0xFF5AA4F7), fade),
        )
        petals.forEach { (angle, colors) ->
            rotate(angle, pivot = center) {
                val topLeft = Offset(center.x - petal.width * 0.08f, center.y - petal.height / 2)
                drawOval(
                    brush = Brush.horizontalGradient(colors, startX = topLeft.x, endX = topLeft.x + petal.width),
                    topLeft = topLeft,
                    size = petal,
                    alpha = if (dark) 0.7f else 0.85f,
                )
            }
        }
        drawCircle(
            Brush.radialGradient(
                listOf(if (dark) Color(0x664FA3FF) else Color(0x88FFFFFF), Color.Transparent),
                center = center,
                radius = size.width * 0.3f,
            ),
            radius = size.width * 0.3f,
            center = center,
        )
    }
}
