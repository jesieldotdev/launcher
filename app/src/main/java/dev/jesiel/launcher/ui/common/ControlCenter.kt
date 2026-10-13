package dev.jesiel.launcher.ui.common

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.jesiel.launcher.LauncherController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Estado e ações dos controles rápidos. O Android não deixa apps comuns ligarem Wi-Fi ou
 * Bluetooth diretamente; nesses casos o botão abre o painel do sistema correspondente.
 */
class SystemControls(private val context: Context, private val controller: LauncherController) {
    var wifi by mutableStateOf(false); private set
    var bluetooth by mutableStateOf(false); private set
    var torch by mutableStateOf(false); private set
    var dnd by mutableStateOf(false); private set
    var autoRotate by mutableStateOf(false); private set
    var location by mutableStateOf(false); private set
    var brightness by mutableFloatStateOf(0.5f)
    var volume by mutableFloatStateOf(0.5f)

    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val camera = context.getSystemService(CameraManager::class.java)
    private val torchCamera: String? = runCatching {
        camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCamera) torch = enabled
        }
    }

    fun refresh() {
        wifi = runCatching { context.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)
        bluetooth = runCatching { context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)
        dnd = runCatching { notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL }.getOrDefault(false)
        autoRotate = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1 }.getOrDefault(false)
        location = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) context.getSystemService(LocationManager::class.java).isLocationEnabled else false
        }.getOrDefault(false)
        brightness = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
        volume = runCatching {
            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }.getOrDefault(0.5f)
    }

    fun toggleWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) controller.start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        else controller.start(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun toggleBluetooth() = controller.start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun toggleTorch() {
        val id = torchCamera ?: return controller.toast("Este aparelho não tem lanterna")
        runCatching { camera.setTorchMode(id, !torch) }.onFailure { controller.toast("Lanterna indisponível agora") }
    }

    fun toggleDnd() {
        if (!notifications.isNotificationPolicyAccessGranted) {
            controller.toast("Permita o acesso ao \"Não perturbe\" para o Launcher")
            controller.start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        notifications.setInterruptionFilter(
            if (dnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
        dnd = !dnd
    }

    fun toggleRotation() {
        if (!canWriteSettings()) return
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (autoRotate) 0 else 1)
        autoRotate = !autoRotate
    }

    fun openLocation() = controller.start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun openAirplane() = controller.start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    fun openMobileData() = controller.start(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))

    private var askedWrite = false

    fun setBrightnessLevel(value: Float) {
        brightness = value
        if (!Settings.System.canWrite(context)) {
            if (!askedWrite) {
                askedWrite = true
                canWriteSettings()
            }
            return
        }
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (value * 255).toInt().coerceIn(1, 255))
        }
    }

    fun setVolumeLevel(value: Float) {
        volume = value
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).toInt(), 0) }
    }

    private fun canWriteSettings(): Boolean {
        if (Settings.System.canWrite(context)) return true
        controller.toast("Permita que o Launcher altere configurações do sistema")
        controller.start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
        return false
    }
}

@Composable
fun rememberSystemControls(controller: LauncherController): SystemControls {
    val context = LocalContext.current
    val controls = remember { SystemControls(context, controller) }
    LifecycleResumeEffect(Unit) {
        controls.refresh()
        onPauseOrDispose { }
    }
    DisposableEffect(Unit) {
        val camera = context.getSystemService(CameraManager::class.java)
        runCatching { camera.registerTorchCallback(controls.torchCallback, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { camera.unregisterTorchCallback(controls.torchCallback) } }
    }
    return controls
}

/** Onde o painel aparece: no topo (deslizar para baixo) ou acima da barra de tarefas. */
enum class PanelPosition { TOP, BOTTOM_END }

/**
 * Painel de controle comum a todos os temas, com as cores de [palette]. Fica sobre a tela
 * inteira com um fundo escurecido; tocar fora fecha.
 */
@Composable
fun BoxScope.ControlCenter(
    controller: LauncherController,
    palette: LauncherPalette,
    position: PanelPosition = PanelPosition.TOP,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val visible = controller.controlCenterOpen
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x55000000))
                .clickable(remember { MutableInteractionSource() }, indication = null) { controller.controlCenterOpen = false },
        )
    }
    val fromTop = position == PanelPosition.TOP
    AnimatedVisibility(
        visible,
        enter = slideInVertically { if (fromTop) -it else it / 2 } + fadeIn(),
        exit = slideOutVertically { if (fromTop) -it else it / 2 } + fadeOut(),
        modifier = Modifier.align(if (fromTop) Alignment.TopCenter else Alignment.BottomEnd),
    ) {
        val controls = rememberSystemControls(controller)
        LaunchedEffect(Unit) { controls.refresh() }
        Box(
            Modifier
                .then(if (fromTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp + bottomInset)
                .widthIn(max = 460.dp),
        ) {
            ControlPanelContent(controls, controller, palette)
        }
    }
}

@Composable
private fun ControlPanelContent(controls: SystemControls, controller: LauncherController, palette: LauncherPalette) {
    val now = rememberNow()
    val battery = rememberBatteryState()
    val locale = remember { Locale.getDefault() }
    val date = remember(now) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
            .format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(palette.panel, palette.panelShape, palette.panelStroke)
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Painel de controle", color = palette.panelText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(date, color = palette.panelTextSecondary, fontSize = 12.sp)
            }
            battery?.let {
                BatteryGlyph(it.percent, it.charging, palette.panelText, 24.dp)
                Spacer(Modifier.width(6.dp))
                Text("${it.percent}%", color = palette.panelText, fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
            }
            RoundButton(Icons.Default.Notifications, "Notificações", palette) {
                controller.controlCenterOpen = false
                controller.expandNotifications()
            }
            Spacer(Modifier.width(6.dp))
            RoundButton(Icons.Default.Settings, "Configurações", palette) {
                controller.controlCenterOpen = false
                controller.openSystemSettings()
            }
        }
        Spacer(Modifier.height(14.dp))
        val tiles = listOf(
            Tile(Icons.Default.Wifi, "Wi-Fi", controls.wifi, controls::toggleWifi),
            Tile(Icons.Default.Bluetooth, "Bluetooth", controls.bluetooth, controls::toggleBluetooth),
            Tile(Icons.Default.FlashlightOn, "Lanterna", controls.torch, controls::toggleTorch),
            Tile(Icons.Default.DoNotDisturbOn, "Não perturbe", controls.dnd, controls::toggleDnd),
            Tile(Icons.Default.ScreenRotation, "Rotação", controls.autoRotate, controls::toggleRotation),
            Tile(Icons.Default.LocationOn, "Localização", controls.location, controls::openLocation),
            Tile(Icons.Default.AirplanemodeActive, "Modo avião", false, controls::openAirplane),
            Tile(Icons.Default.SignalCellularAlt, "Dados", false, controls::openMobileData),
        )
        tiles.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { TileButton(it, palette, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        LevelSlider(Icons.Default.BrightnessMedium, "Brilho", controls.brightness, palette, controls::setBrightnessLevel)
        LevelSlider(Icons.AutoMirrored.Filled.VolumeUp, "Volume", controls.volume, palette, controls::setVolumeLevel)
    }
}

private class Tile(val icon: ImageVector, val label: String, val on: Boolean, val onClick: () -> Unit)

@Composable
private fun TileButton(tile: Tile, palette: LauncherPalette, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (tile.on) palette.accent else palette.tile)
                .clickable(onClick = tile.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tile.icon, contentDescription = tile.label, tint = if (tile.on) palette.onAccent else palette.panelText)
        }
        Spacer(Modifier.height(4.dp))
        Text(tile.label, color = palette.panelText, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, palette: LauncherPalette, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(palette.tile).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = palette.panelText, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun LevelSlider(icon: ImageVector, label: String, value: Float, palette: LauncherPalette, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = palette.panelText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.tile,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}
