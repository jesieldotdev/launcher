package dev.jesiel.launcher.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hora formatada conforme a preferência 12/24 h do aparelho, opcionalmente com data. */
@Composable
fun rememberTimeText(datePattern: String? = null): String {
    val context = LocalContext.current
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    return remember(now, datePattern) {
        val time = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        val pattern = datePattern?.let { android.text.format.DateFormat.getBestDateTimePattern(locale, it) + " " } .orEmpty() + time
        SimpleDateFormat(pattern, locale).format(Date(now))
    }
}
