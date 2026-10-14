package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.data.ColorMode

/**
 * Cores e formas de um tema numa variante (clara ou escura). Os componentes compartilhados
 * (cartões, painel de controle, gaveta de apps) se desenham a partir daqui.
 */
data class LauncherPalette(
    val dark: Boolean,
    val accent: Color,
    val onAccent: Color = Color.White,
    /** Texto sobre o papel de parede (rótulos dos ícones). */
    val onWallpaper: Color = Color.White,
    /** Cartões sobre o papel de parede (relógio, bateria…). */
    val card: Color,
    val cardStroke: Color,
    val cardText: Color,
    val cardTextSecondary: Color,
    val cardShape: Shape = RoundedCornerShape(18.dp),
    /** Painéis opacos: menus, gaveta de apps, painel de controle. */
    val panel: Color,
    val panelStroke: Color,
    val panelText: Color,
    val panelTextSecondary: Color,
    val panelShape: Shape = RoundedCornerShape(24.dp),
    /** Botões do painel de controle desligados. */
    val tile: Color,
    /** Campos de texto (busca) dentro dos painéis. */
    val field: Color = tile,
    val dock: Color,
    val dockStroke: Color,
    val folder: Color,
    /** Fundo atrás de cada ícone (tema KDE), ou nulo. */
    val iconBackground: Color? = null,
    /** Fonte própria do tema (ex.: Orbitron no Retrô futurista); nula usa a do sistema. */
    val font: FontFamily? = null,
) {
    val wallpaperLabel: TextStyle
        get() = TextStyle(
            color = onWallpaper,
            fontSize = 12.sp,
            fontFamily = font,
            shadow = if (onWallpaper == Color.White) Shadow(Color(0x99000000), Offset(0f, 1f), 4f) else null,
        )

    val panelLabel: TextStyle get() = TextStyle(color = panelText, fontSize = 12.sp, fontFamily = font)

    val colorScheme: ColorScheme
        get() = if (dark) {
            darkColorScheme(
                primary = accent, onPrimary = onAccent, surface = Color(0xFF2B2B2F), onSurface = Color(0xFFEDEDF0),
                surfaceContainer = Color(0xFF2B2B2F), background = Color(0xFF1E1E22), onBackground = Color(0xFFEDEDF0),
                surfaceVariant = Color(0xFF3A3A40), onSurfaceVariant = Color(0xFFB8B8C0),
            )
        } else {
            lightColorScheme(
                primary = accent, onPrimary = onAccent, surface = Color(0xFFF7F8FA), onSurface = Color(0xFF1B1B1F),
                surfaceContainer = Color(0xFFF7F8FA), background = Color(0xFFF3F4F7), onBackground = Color(0xFF1B1B1F),
                surfaceVariant = Color(0xFFE5E7EC), onSurfaceVariant = Color(0xFF5F6670),
            )
        }
}

/** Paleta do tema em uso, para componentes profundos na árvore. */
val LocalPalette = staticCompositionLocalOf<LauncherPalette> { error("Paleta não definida") }

@Composable
fun isDark(mode: ColorMode): Boolean = when (mode) {
    ColorMode.SYSTEM -> isSystemInDarkTheme()
    ColorMode.LIGHT -> false
    ColorMode.DARK -> true
}

/** Superfície translúcida com borda fina, base de cartões e painéis. */
fun Modifier.glass(color: Color, shape: Shape, stroke: Color): Modifier =
    this.clip(shape).background(color).border(1.dp, stroke, shape)

/** Painel opaco do tema atual com o formato padrão. */
@Composable
fun Modifier.acrylicPanel(): Modifier {
    val p = LocalPalette.current
    return glass(p.panel, p.panelShape, p.panelStroke)
}
