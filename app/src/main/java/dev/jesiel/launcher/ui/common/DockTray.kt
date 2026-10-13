package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bandeja da dock / barra de tarefas: hora e, conforme os ajustes, data, Wi-Fi e bateria.
 * Tocar abre o painel de controle; o toque longo dá atalhos para ajustar ou ocultar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockTray(
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    vertical: Boolean = false,
) {
    val layout = state.layout
    if (!layout.showTray) return
    // Sem o fundo da dock, a bandeja usa a cor de texto sobre o papel de parede.
    val palette = palette.forDock(layout.dockBackground)
    val context = LocalContext.current
    val now = rememberNow()
    val battery = rememberBatteryState()
    val locale = remember { Locale.getDefault() }
    val time = remember(now) {
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(Date(now))
    }
    val date = remember(now) { SimpleDateFormat("dd/MM", locale).format(Date(now)) }
    var menu by remember { mutableStateOf(false) }
    val active = controller.controlCenterOpen
    val vm = controller.viewModel

    Box {
        val content: @Composable () -> Unit = {
            if (layout.trayShowStatus) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = palette.panelText, modifier = Modifier.size(16.dp))
                if (vertical) Spacer(Modifier.size(4.dp)) else Spacer(Modifier.width(4.dp))
                BatteryGlyph(battery?.percent ?: 0, battery?.charging == true, palette.panelText, 20.dp)
                if (vertical) Spacer(Modifier.size(6.dp)) else Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (vertical) Alignment.CenterHorizontally else Alignment.End) {
                Text(time, color = palette.panelText, fontSize = 12.sp, maxLines = 1)
                if (layout.trayShowDate) Text(date, color = palette.panelTextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
        val modifier = Modifier
            .semantics { contentDescription = "Configurações rápidas" }
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) palette.tile else Color.Transparent)
            .combinedClickable(onClick = { controller.controlCenterOpen = !active }, onLongClick = { menu = true })
            .padding(horizontal = 8.dp, vertical = 6.dp)
        if (vertical) {
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
        } else {
            Row(modifier, verticalAlignment = Alignment.CenterVertically) { content() }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(12.dp)) {
            DropdownMenuItem(
                text = { Text(if (layout.trayShowDate) "Mostrar só a hora" else "Mostrar hora e data") },
                onClick = { menu = false; vm.updateLayout { it.copy(trayShowDate = !it.trayShowDate) } },
            )
            DropdownMenuItem(
                text = { Text(if (layout.trayShowStatus) "Ocultar Wi-Fi e bateria" else "Mostrar Wi-Fi e bateria") },
                onClick = { menu = false; vm.updateLayout { it.copy(trayShowStatus = !it.trayShowStatus) } },
            )
            DropdownMenuItem(text = { Text("Ocultar hora e data") }, onClick = { menu = false; vm.updateLayout { it.copy(showTray = false) } })
            DropdownMenuItem(text = { Text("Configurações da dock") }, onClick = { menu = false; controller.settingsOpen = true })
        }
    }
}
