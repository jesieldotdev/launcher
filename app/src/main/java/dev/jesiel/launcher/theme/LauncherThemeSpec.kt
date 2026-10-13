package dev.jesiel.launcher.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.ui.ios.IosLauncher
import dev.jesiel.launcher.ui.kde.KdeLauncher
import dev.jesiel.launcher.ui.macos.MacLauncher
import dev.jesiel.launcher.ui.ubuntu.UbuntuLauncher
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

object WindowsThemeSpec : LauncherThemeSpec {
    override val id = "windows"
    override val name = "Windows"
    override val description = "Menu Iniciar, apps fixados e atalhos recentes"
    override val previewColors = listOf(Color(0xFF0B5CD5), Color(0xFF5AA0F2), Color(0xFFDCE8F8))

    @Composable
    override fun Content(state: LauncherUiState, controller: LauncherController) = WindowsLauncher(state, controller)
}

object UbuntuThemeSpec : LauncherThemeSpec {
    override val id = "ubuntu"
    override val name = "Ubuntu"
    override val description = "Dock lateral, Atividades e grade de apps"
    override val previewColors = listOf(Color(0xFF2C001E), Color(0xFF77216F), Color(0xFFE95420))

    @Composable
    override fun Content(state: LauncherUiState, controller: LauncherController) = UbuntuLauncher(state, controller)
}

object KdeThemeSpec : LauncherThemeSpec {
    override val id = "kde"
    override val name = "KDE Plasma"
    override val description = "Widgets do sistema, painel flutuante e apps por categoria"
    override val previewColors = listOf(Color(0xFF0D1B2A), Color(0xFF1D99F3), Color(0xFF3DAEE9))

    @Composable
    override fun Content(state: LauncherUiState, controller: LauncherController) = KdeLauncher(state, controller)
}

object MacThemeSpec : LauncherThemeSpec {
    override val id = "macos"
    override val name = "macOS"
    override val description = "Barra de menus, Dock e Launchpad"
    override val previewColors = listOf(Color(0xFF6A7BA8), Color(0xFFF0A585), Color(0xFF3E6188))

    @Composable
    override fun Content(state: LauncherUiState, controller: LauncherController) = MacLauncher(state, controller)
}

object IosThemeSpec : LauncherThemeSpec {
    override val id = "ios"
    override val name = "iOS"
    override val description = "Widgets grandes, páginas e Biblioteca de Apps"
    override val previewColors = listOf(Color(0xFFB8C6FF), Color(0xFFF6C1D9), Color(0xFFFFE4C4))

    @Composable
    override fun Content(state: LauncherUiState, controller: LauncherController) = IosLauncher(state, controller)
}

object ThemeRegistry {
    val all: List<LauncherThemeSpec> = listOf(WindowsThemeSpec, UbuntuThemeSpec, KdeThemeSpec, MacThemeSpec, IosThemeSpec)

    fun byId(id: String): LauncherThemeSpec = all.firstOrNull { it.id == id && it.available } ?: WindowsThemeSpec
}
