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
        compose.mainClock.advanceTimeBy(2_000)
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
