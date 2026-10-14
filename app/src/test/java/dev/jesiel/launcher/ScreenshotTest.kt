package dev.jesiel.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.hasSetTextAction
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import dev.jesiel.launcher.data.AppCategory
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.AppsView
import dev.jesiel.launcher.data.MenuIcon
import dev.jesiel.launcher.data.ThemeLayout
import dev.jesiel.launcher.data.ColorMode
import dev.jesiel.launcher.data.FolderData
import dev.jesiel.launcher.data.HomeItem
import dev.jesiel.launcher.data.RecentApp
import dev.jesiel.launcher.ui.settings.SettingsScreen
import dev.jesiel.launcher.ui.ios.IosLauncher
import dev.jesiel.launcher.ui.kde.KdeLauncher
import dev.jesiel.launcher.ui.retro.RetroLauncher
import dev.jesiel.launcher.ui.macos.MacLauncher
import dev.jesiel.launcher.ui.ubuntu.UbuntuLauncher
import dev.jesiel.launcher.ui.windows.WindowsLauncher
import dev.jesiel.launcher.widgets.WidgetHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Gera imagens das telas de cada tema com dados de exemplo (veja docs/screenshots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) = "${roborazziSystemPropertyOutputDirectory()}/$name.png"

    private val apps = listOf(
        "WhatsApp" to 0xFF25D366, "Chrome" to 0xFF4285F4, "Spotify" to 0xFF1DB954, "Câmera" to 0xFF5F6368,
        "Arquivos" to 0xFFF4B400, "Música" to 0xFFFA3E5B, "Fotos" to 0xFFEA4335, "Configurações" to 0xFF607D8B,
        "Play Store" to 0xFF01875F, "Gmail" to 0xFFD93025, "Calendário" to 0xFF1A73E8, "Notas" to 0xFF4C8BF5,
        "Relógio" to 0xFF37474F, "Contatos" to 0xFF1E88E5, "Telefone" to 0xFF1976D2, "Mensagens" to 0xFF2962FF,
    ).map { (label, color) -> fakeApp(label, color.toInt()) }

    private fun fakeApp(label: String, color: Int): AppInfo {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(8f, 8f, size - 8f, size - 8f), 48f, 48f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 96f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label.take(1), size / 2f, size / 2f + 34f, paint)
        return AppInfo(
            key = "fake/$label",
            label = label,
            component = ComponentName("fake.${label.lowercase()}", "Main"),
            user = Process.myUserHandle(),
            icon = bitmap.asImageBitmap(),
            category = when (label) {
                "WhatsApp", "Gmail", "Contatos", "Telefone", "Mensagens" -> AppCategory.COMMUNICATION
                "Spotify", "Câmera", "Música", "Fotos" -> AppCategory.MEDIA
                "Calendário", "Notas", "Arquivos" -> AppCategory.PRODUCTIVITY
                else -> AppCategory.TOOLS
            },
        )
    }

    private fun sampleState(dark: Boolean = false): LauncherUiState {
        val now = System.currentTimeMillis()
        val folder = HomeItem.Folder("f1", "Google", apps.slice(9..11))
        val home = apps.take(7).map(HomeItem::App) + folder
        return LauncherUiState(
            loaded = true,
            apps = apps.sortedBy { it.label },
            home = home,
            dock = listOf(apps[4], apps[1], apps[0], apps[2]),
            recents = listOf(
                RecentApp(apps[0], now - 4 * 60_000),
                RecentApp(apps[2], now - 2 * 3_600_000),
                RecentApp(apps[6], now - 5 * 3_600_000),
                RecentApp(apps[9], now - 26 * 3_600_000),
            ),
            folders = listOf(FolderData("f1", "Google", folder.apps.map { it.key })),
            favoriteKeys = apps.take(7).map { it.key }.toSet(),
            dockKeys = setOf(apps[4].key, apps[1].key, apps[0].key, apps[2].key),
            colorMode = if (dark) ColorMode.DARK else ColorMode.LIGHT,
        )
    }

    private fun controller(): LauncherController {
        val activity = compose.activity
        // Bateria de exemplo para o cartão da área de trabalho.
        activity.applicationContext.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 82)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING),
        )
        return LauncherController(activity, LauncherViewModel(activity.application), WidgetHost(activity))
    }

    @Test
    fun home() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.onRoot().captureRoboImage(shot("windows-home"))
    }

    @Test
    fun homeDark() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(dark = true), controller) }
        compose.onRoot().captureRoboImage(shot("windows-home-dark"))
    }

    @Test
    fun editMode() {
        val controller = controller()
        controller.editMode = true
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.onRoot().captureRoboImage(shot("windows-edit"))
    }

    @Test
    fun controlCenter() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(dark = true), controller) }
        compose.onNodeWithContentDescription("Configurações rápidas").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-control-center"))
    }

    @Test
    fun swipeDownOpensControlCenter() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.onRoot().performTouchInput { swipeDown(startY = centerY - 300f, endY = centerY + 300f, durationMillis = 120) }
        compose.waitForIdle()
        org.junit.Assert.assertTrue(controller.controlCenterOpen)
    }

    /** Desliza para cima e fotografa: a gaveta de apps tem que aparecer. */
    private fun swipeUpShot(name: String, content: @androidx.compose.runtime.Composable (LauncherUiState, LauncherController) -> Unit) {
        val controller = controller()
        compose.setContent { content(sampleState(dark = true), controller) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeUp(startY = centerY + 300f, endY = centerY - 300f, durationMillis = 120) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot(name))
    }

    @Test fun retroSwipeUp() = swipeUpShot("check-retro-swipe-up") { s, c -> RetroLauncher(s, c) }
    @Test fun iosSwipeUp() = swipeUpShot("check-ios-swipe-up") { s, c -> IosLauncher(s, c) }
    @Test fun kdeSwipeUp() = swipeUpShot("check-kde-swipe-up") { s, c -> KdeLauncher(s, c) }

    @Test
    fun startMenu() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.onNodeWithContentDescription("Iniciar").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-start"))
    }

    @Test
    fun allApps() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.onNodeWithContentDescription("Iniciar").performClick()
        compose.onNodeWithText("Todos os apps").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-all-apps"))
    }

    private fun allAppsShot(name: String, view: AppsView, dark: Boolean = false) {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(dark).copy(appsView = view), controller) }
        compose.onNodeWithContentDescription("Iniciar").performClick()
        compose.onNodeWithText("Todos os apps").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot(name))
    }

    @Test fun allAppsCategory() = allAppsShot("windows-all-apps-category", AppsView.CATEGORY, dark = true)
    @Test fun allAppsGrid() = allAppsShot("windows-all-apps-grid", AppsView.GRID)

    @Test
    fun startDarkMenuIcon() {
        val controller = controller()
        compose.setContent {
            WindowsLauncher(sampleState(dark = true).copy(layout = ThemeLayout(menuIcon = MenuIcon.ORB)), controller)
        }
        compose.onNodeWithContentDescription("Iniciar").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-start-dark"))
    }

    @Test
    fun feed() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(dark = true), controller) }
        compose.onRoot().performTouchInput { swipeRight() }
        // Espera as notícias reais (se houver rede); sem rede, a captura mostra o carregamento.
        runCatching {
            compose.waitUntil(20_000) {
                controller.viewModel.news.value[dev.jesiel.launcher.data.NewsTopic.FOR_YOU] .let { it != null && it !is dev.jesiel.launcher.data.NewsState.Loading }
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-feed"))
    }

    @Test
    fun longPressOnComponentShowsMenu() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.onNodeWithText("Bateria").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Remover").assertExists()
        compose.onRoot().captureRoboImage(shot("windows-component-menu"))
    }

    @Test
    fun editWithScreensAndNoTaskbar() {
        val controller = controller()
        controller.editMode = true
        compose.setContent {
            WindowsLauncher(
                sampleState().copy(layout = ThemeLayout(multiPage = true, pageCount = 2, freePlacement = true, showDock = false)),
                controller,
            )
        }
        compose.onRoot().captureRoboImage(shot("windows-edit-free"))
    }

    @Test
    fun startOpensAllApps() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(dark = true).copy(layout = ThemeLayout(menuOpensAllApps = true)), controller) }
        compose.onNodeWithContentDescription("Iniciar").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-start-all-apps"))
    }

    @Test
    fun trayLongPressHidesIt() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.onNodeWithContentDescription("Configurações rápidas").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Ocultar hora e data").assertExists()
    }

    @Test
    fun noTray() {
        val controller = controller()
        val state = sampleState().let { it.copy(layout = ThemeLayout(showTray = false, dockMaxApps = 8), dock = apps.take(7)) }
        compose.setContent { WindowsLauncher(state, controller) }
        compose.onRoot().captureRoboImage(shot("windows-no-tray"))
    }

    @Test
    fun allAppsWithoutHeader() {
        val controller = controller()
        compose.setContent {
            WindowsLauncher(sampleState(dark = true).copy(layout = ThemeLayout(menuOpensAllApps = true, showAppsHeader = false)), controller)
        }
        compose.onNodeWithContentDescription("Iniciar").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-all-apps-no-header"))
    }

    @Test
    fun gridDropSnapsBackToCell() {
        val controller = controller()
        controller.editMode = true
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        // Arrasta um pouco (menos de meia célula): na grade o item deve voltar para a célula.
        compose.onNodeWithText("Chrome").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, 30f))
            moveBy(androidx.compose.ui.geometry.Offset(20f, 10f))
            up()
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-grid-drop"))
    }

    @Test
    fun dragOntoAnotherPushesIt() {
        val controller = controller()
        controller.editMode = true
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        // Arrasta o Chrome sobre o Spotify e captura antes de soltar: o Spotify já saiu do caminho.
        compose.onNodeWithText("Chrome").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.6f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.5f, 0f))
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-collision"))
    }

    /** Fora do modo de edição: segurar o ícone e arrastar move ele (e afasta quem está no caminho). */
    @Test
    fun holdAndDragMovesIconWithoutEditMode() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        val before = controller.viewModel.state.value.placements.toMap()
        compose.onNodeWithText("Chrome").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.6f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.5f, 0f))
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        org.junit.Assert.assertFalse("não deve entrar no modo de edição", controller.editMode)
        compose.onRoot().captureRoboImage(shot("windows-hold-drag"))
        compose.onNodeWithText("Chrome").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val after = controller.viewModel.state.value.placements
        org.junit.Assert.assertTrue("a posição deve ter sido gravada", after != before)
    }

    /** Segurar o item arrastado na borda direita leva ele para a próxima tela. */
    @Test
    fun holdingAtTheEdgeMovesIconToNextScreen() {
        val controller = controller()
        val base = sampleState()
        val state = base.copy(layout = base.layout.copy(multiPage = true, pageCount = 2))
        compose.setContent { WindowsLauncher(state, controller) }
        compose.onNodeWithText("Chrome").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(androidx.compose.ui.geometry.Offset(width * 1.2f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(width * 1.4f, 0f))
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-edge-drag"))
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val pages = controller.viewModel.state.value.placements.values.map { it.page }
        org.junit.Assert.assertTrue("algum item deve ter ido para a tela 2: $pages", pages.any { it == 1 })
    }

    /** Arrastar rápido, sem segurar, não move ícone nenhum. */
    @Test
    fun quickSwipeDoesNotMoveIcons() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        val before = controller.viewModel.state.value.placements.toMap()
        compose.onNodeWithText("Chrome").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.6f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.5f, 0f))
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        org.junit.Assert.assertEquals(before, controller.viewModel.state.value.placements)
    }

    @Test
    fun windowAreaStopsAboveTheTaskbar() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.waitForIdle()
        val dock = controller.dockBounds!!
        val area = controller.windowArea()
        org.junit.Assert.assertTrue("janela $area deve terminar acima da barra $dock", area.bottom < dock.top)
        org.junit.Assert.assertTrue(area.height() > dock.height() * 5)
    }

    @Test
    fun noDockBackground() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState().copy(layout = ThemeLayout(dockBackground = false)), controller) }
        compose.onRoot().captureRoboImage(shot("windows-no-dock-background"))
    }

    @Test fun macNoDockBackground() = themeShotWith("macos-no-dock-background") { s, c -> MacLauncher(s.copy(layout = s.layout.copy(dockBackground = false)), c) }

    private fun themeShotWith(name: String, content: @androidx.compose.runtime.Composable (LauncherUiState, LauncherController) -> Unit) =
        themeShot(name, dark = false, content = content)

    @Test
    fun search() {
        val controller = controller()
        compose.setContent { WindowsLauncher(sampleState(), controller) }
        compose.onNodeWithContentDescription("Pesquisar").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("c")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("windows-search"))
    }

    private fun themeShot(name: String, dark: Boolean, open: String? = null, content: @androidx.compose.runtime.Composable (LauncherUiState, LauncherController) -> Unit) {
        val controller = controller()
        val state = sampleState(dark)
        compose.setContent { content(state, controller) }
        if (open != null) {
            compose.onNodeWithContentDescription(open).performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(shot(name))
    }

    @Test fun ubuntu() = themeShot("ubuntu-home", dark = true) { s, c -> UbuntuLauncher(s, c) }
    @Test fun ubuntuLight() = themeShot("ubuntu-home-light", dark = false) { s, c -> UbuntuLauncher(s, c) }
    @Test fun ubuntuApps() = themeShot("ubuntu-apps", dark = true, open = "Mostrar aplicativos") { s, c -> UbuntuLauncher(s, c) }
    private fun retroShot(name: String, open: String? = null, state: LauncherUiState = sampleState(dark = true)) {
        val controller = controller()
        compose.setContent { RetroLauncher(state, controller) }
        // Passa da sequência de inicialização.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        if (open != null) {
            compose.onNodeWithContentDescription(open).performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(shot(name))
    }

    /** Nomes acentuados intercalam as iniciais (E, É, E) na ordem do Collator; a lista não pode repetir a letra. */
    @Test fun retroAppsAccents() {
        val s = sampleState(dark = true)
        val extra = listOf("Edge", "Élan", "Email", "Ônibus", "Opera", "ônix").mapIndexed { i, l -> fakeApp(l, 0xFF336699.toInt() + i) }
        val collator = java.text.Collator.getInstance()
        val apps = (s.apps + extra).sortedWith { a, b -> collator.compare(a.label, b.label) }
        retroShot("check-retro-accents", open = "Programas", state = s.copy(apps = apps))
    }

    private fun editorShot(name: String, fullscreen: Boolean) {
        val controller = controller()
        val widget = dev.jesiel.launcher.data.HtmlWidget(id = "w1", name = "Música", html = dev.jesiel.launcher.data.HtmlTemplates.MUSIC.second, interactive = true)
        compose.setContent {
            androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                dev.jesiel.launcher.ui.common.HtmlWidgetEditor(widget, controller, dev.jesiel.launcher.ui.windows.windowsPalette(true), null) {}
            }
        }
        if (fullscreen) {
            compose.onNodeWithText("Tela cheia").performClick()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot(name))
    }

    @Test fun htmlEditor() = editorShot("html-editor", fullscreen = false)
    @Test fun htmlEditorFullscreen() = editorShot("html-editor-fullscreen", fullscreen = true)

    @Test fun retro() = retroShot("retro-home")
    @Test fun retroApps() = retroShot("retro-apps", open = "Programas")
    @Test fun retroAppsNoLetters() {
        val s = sampleState(dark = true)
        retroShot("retro-apps-no-letters", open = "Programas", state = s.copy(layout = s.layout.copy(showLetters = false)))
    }
    @Test fun kde() = themeShot("kde-home", dark = true) { s, c -> KdeLauncher(s, c) }
    @Test fun kdeLight() = themeShot("kde-home-light", dark = false) { s, c -> KdeLauncher(s, c) }
    @Test fun kdeApps() = themeShot("kde-apps", dark = true, open = "Lançador de aplicativos") { s, c -> KdeLauncher(s, c) }
    @Test fun macos() = themeShot("macos-home", dark = false) { s, c -> MacLauncher(s, c) }
    @Test fun macosDark() = themeShot("macos-home-dark", dark = true) { s, c -> MacLauncher(s, c) }
    @Test fun macosLaunchpad() = themeShot("macos-launchpad", dark = false, open = "Launchpad") { s, c -> MacLauncher(s, c) }
    @Test fun macosControl() = themeShot("macos-control-center", dark = false, open = "Central de Controle") { s, c -> MacLauncher(s, c) }
    @Test fun ios() = themeShot("ios-home", dark = false) { s, c -> IosLauncher(s, c) }
    @Test fun iosDark() = themeShot("ios-home-dark", dark = true) { s, c -> IosLauncher(s, c) }

    @Test
    fun iosLibrary() {
        val controller = controller()
        compose.setContent { IosLauncher(sampleState(), controller) }
        compose.onNodeWithText("Buscar").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(shot("ios-library"))
    }

    @Test
    fun settingsDark() {
        val controller = controller()
        compose.setContent { SettingsScreen(sampleState(dark = true), controller, onClose = {}) }
        compose.onRoot().captureRoboImage(shot("settings-dark"))
    }

    @Test
    fun settings() {
        val controller = controller()
        compose.setContent { SettingsScreen(sampleState(), controller, onClose = {}) }
        compose.onRoot().captureRoboImage(shot("settings"))
    }
}
