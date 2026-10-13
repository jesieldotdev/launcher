package dev.jesiel.launcher.ui.common

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

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

data class MemoryState(val usedPercent: Int, val storageFreeGb: Float, val storageTotalGb: Float)

/** Uso de memória e armazenamento, atualizado a cada 10 s. */
@Composable
fun rememberMemoryState(): MemoryState? {
    val context = LocalContext.current
    var state by remember { mutableStateOf<MemoryState?>(null) }
    LaunchedEffect(Unit) {
        val am = context.getSystemService(ActivityManager::class.java)
        while (true) {
            state = runCatching {
                val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                val stat = StatFs(Environment.getDataDirectory().path)
                val gb = 1_000_000_000f
                MemoryState(
                    usedPercent = if (mem.totalMem > 0) ((mem.totalMem - mem.availMem) * 100 / mem.totalMem).toInt() else 0,
                    storageFreeGb = stat.availableBytes / gb,
                    storageTotalGb = stat.totalBytes / gb,
                )
            }.getOrNull()
            delay(10_000)
        }
    }
    return state
}
