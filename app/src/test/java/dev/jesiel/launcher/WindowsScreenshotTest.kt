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
import androidx.compose.ui.test.hasSetTextAction
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.FolderData
import dev.jesiel.launcher.data.HomeItem
import dev.jesiel.launcher.data.RecentApp
import dev.jesiel.launcher.ui.settings.SettingsScreen
import dev.jesiel.launcher.ui.windows.WindowsLauncher
import dev.jesiel.launcher.widgets.WidgetHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Gera imagens das telas do tema Windows com dados de exemplo (veja docs/screenshots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class WindowsScreenshotTest {

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
        )
    }

    private fun sampleState(): LauncherUiState {
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

    @Test
    fun settings() {
        val controller = controller()
        compose.setContent { SettingsScreen(sampleState(), controller, onClose = {}) }
        compose.onRoot().captureRoboImage(shot("settings"))
    }
}
