package dev.jesiel.launcher.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.ui.windows.WindowsLauncher

/**
 * Um tema do launcher. Além das cores, cada tema define a própria forma de navegar:
 * onde fica o dock, como o menu de apps é apresentado e como os widgets são dispostos.
 */
interface LauncherThemeSpec {
    val id: String
    val name: String
    val description: String
    /** Cores usadas na prévia do seletor de temas. */
    val previewColors: List<Color>
    val available: Boolean get() = true

    @Composable
    fun Content(state: LauncherUiState, controller: LauncherController)
}

/** Tema ainda não implementado: aparece no seletor apenas como prévia. */
private class UpcomingTheme(
    override val id: String,
    override val name: String,
    override val description: String,
    override val previewColors: List<Color>,
) : LauncherThemeSpec {
    override val available = false

    @Composable
    override fun Content(state: LauncherUiState, controller: LauncherController) = Unit
}

object WindowsThemeSpec : LauncherThemeSpec {
    override val id = "windows"
    override val name = "Windows"
    override val description = "Menu Iniciar, apps fixados e atalhos recentes"
    override val previewColors = listOf(Color(0xFF0B5CD5), Color(0xFF5AA0F2), Color(0xFFDCE8F8))

    @Composable
    override fun Content(state: LauncherUiState, controller: LauncherController) = WindowsLauncher(state, controller)
}

object ThemeRegistry {
    val all: List<LauncherThemeSpec> = listOf(
        WindowsThemeSpec,
        UpcomingTheme(
            "ubuntu", "Ubuntu", "Dock lateral, favoritos e grade de apps",
            listOf(Color(0xFF2C001E), Color(0xFF77216F), Color(0xFFE95420)),
        ),
        UpcomingTheme(
            "kde", "KDE Plasma", "Widgets, painel flutuante e apps por categoria",
            listOf(Color(0xFF0D1B2A), Color(0xFF1D99F3), Color(0xFF3DAEE9)),
        ),
        UpcomingTheme(
            "macos", "macOS", "Dock, busca rápida e widgets de agenda",
            listOf(Color(0xFF3A6073), Color(0xFFE5A15B), Color(0xFFF2D7B6)),
        ),
        UpcomingTheme(
            "ios", "iOS", "Grade de apps, widgets grandes e dock inferior",
            listOf(Color(0xFFB8C6FF), Color(0xFFF6C1D9), Color(0xFFFFE4C4)),
        ),
    )

    fun byId(id: String): LauncherThemeSpec = all.firstOrNull { it.id == id && it.available } ?: WindowsThemeSpec
}
