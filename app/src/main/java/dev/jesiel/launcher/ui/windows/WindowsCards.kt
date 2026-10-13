package dev.jesiel.launcher.ui.windows

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(18.dp)

data class BatteryState(val percent: Int, val charging: Boolean)

@Composable
fun rememberBatteryState(): BatteryState? {
    val context = LocalContext.current
    var state by remember { mutableStateOf<BatteryState?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0 && scale > 0) {
                    state = BatteryState(
                        percent = level * 100 / scale,
                        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL,
                    )
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Hora atual, atualizada na virada de cada minuto. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    return now
}

@Composable
fun BatteryCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val battery = rememberBatteryState()
    Column(
        modifier
            .acrylic(WinColors.CardOnWallpaper, CardShape, WinColors.CardStroke)
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) } }
            .padding(16.dp),
    ) {
        Text("Bateria", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryGlyph(battery?.percent ?: 0, battery?.charging == true)
            Spacer(Modifier.width(10.dp))
            Text(
                battery?.let { "${it.percent}%" } ?: "--",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                battery == null -> ""
                battery.charging -> "Carregando"
                battery.percent <= 15 -> "Bateria fraca"
                else -> "Em uso"
            },
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BatteryGlyph(percent: Int, charging: Boolean) {
    val fill = when {
        charging -> Color(0xFF6CCB5F)
        percent <= 15 -> Color(0xFFFF6B6B)
        else -> Color.White
    }
    Canvas(Modifier.size(width = 30.dp, height = 16.dp)) {
        val body = Size(size.width - 4.dp.toPx(), size.height)
        drawRoundRect(Color.White, size = body, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        drawRoundRect(
            Color.White,
            topLeft = Offset(body.width + 1.dp.toPx(), size.height * 0.3f),
            size = Size(2.dp.toPx(), size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        val inset = 3.dp.toPx()
        drawRoundRect(
            fill,
            topLeft = Offset(inset, inset),
            size = Size((body.width - inset * 2) * percent / 100f, body.height - inset * 2),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

@Composable
fun ClockCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    val time = remember(now) {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        SimpleDateFormat(pattern, locale).format(Date(now))
    }
    val date = remember(now) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    }
    Column(
        modifier
            .acrylic(WinColors.CardOnWallpaper, CardShape, WinColors.CardStroke)
            .clickable {
                val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(alarms) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS)) } }
            }
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(time, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Light)
        Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
    }
}

