package dev.jesiel.launcher.ui.common

import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.jesiel.launcher.data.CellSize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Componente próprio de um tema (relógio, bateria, busca…) que o usuário pode mover,
 * redimensionar ou remover da área de trabalho.
 */
class BuiltinSpec(
    val id: String,
    val name: String,
    /** Tamanho inicial em células, dado o número de colunas da grade. */
    val defaultSize: (columns: Int) -> CellSize,
    val resizable: Boolean = true,
    val content: @Composable (Modifier) -> Unit,
)

/** Converte um tamanho em dp para sp ignorando a escala de fonte, para caber no cartão. */
@Composable
fun Dp.asFixedSp(): TextUnit = with(LocalDensity.current) { this@asFixedSp.toSp() }

@Composable
private fun cardModifier(palette: LauncherPalette, modifier: Modifier, onClick: () -> Unit) =
    modifier.glass(palette.card, palette.cardShape, palette.cardStroke).clickable(onClick = onClick)

private fun openClock(context: android.content.Context) {
    val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(alarms) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
fun ClockCard(palette: LauncherPalette, modifier: Modifier = Modifier, showCard: Boolean = true, align: Alignment.Horizontal = Alignment.Start) {
    val context = LocalContext.current
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    val time = remember(now) {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        SimpleDateFormat(pattern, locale).format(Date(now))
    }
    val date = remember(now) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
            .format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    }
    val base = if (showCard) cardModifier(palette, modifier, onClick = { openClock(context) }) else modifier.clickable { openClock(context) }
    val textColor = if (showCard) palette.cardText else palette.onWallpaper
    BoxWithConstraints(base.padding(14.dp), contentAlignment = Alignment.CenterStart) {
        val big = minOf(maxHeight * 0.52f, maxWidth * 0.3f)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
            Text(time, color = textColor, fontSize = big.asFixedSp(), fontWeight = FontWeight.Light, maxLines = 1)
            Text(date, color = textColor.copy(alpha = 0.85f), fontSize = (big * 0.3f).coerceAtLeast(11.dp).asFixedSp(), maxLines = 1)
        }
    }
}

@Composable
fun BatteryCard(palette: LauncherPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val battery = rememberBatteryState()
    BoxWithConstraints(
        cardModifier(palette, modifier) {
            runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }.padding(14.dp),
    ) {
        val big = minOf(maxHeight * 0.34f, maxWidth * 0.24f)
        val small = (big * 0.4f).coerceAtLeast(11.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("Bateria", color = palette.cardText, fontSize = small.asFixedSp(), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryGlyph(battery?.percent ?: 0, battery?.charging == true, palette.cardText, big * 0.9f)
                Spacer(Modifier.width(8.dp))
                Text(
                    battery?.let { "${it.percent}%" } ?: "--",
                    color = palette.cardText,
                    fontSize = big.asFixedSp(),
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                )
            }
            Text(
                when {
                    battery == null -> ""
                    battery.charging -> "Carregando"
                    battery.percent <= 15 -> "Bateria fraca"
                    else -> "Em uso"
                },
                color = palette.cardTextSecondary,
                fontSize = small.asFixedSp(),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun BatteryGlyph(percent: Int, charging: Boolean, color: Color, width: Dp = 30.dp) {
    val fill = when {
        charging -> Color(0xFF4CC264)
        percent <= 15 -> Color(0xFFFF5B5B)
        else -> color
    }
    Canvas(Modifier.size(width = width, height = width * 0.52f)) {
        val body = Size(size.width - size.width * 0.12f, size.height)
        val r = CornerRadius(size.height * 0.25f)
        drawRoundRect(color, size = body, cornerRadius = r, style = Stroke(size.height * 0.1f))
        drawRoundRect(
            color,
            topLeft = Offset(body.width + size.width * 0.03f, size.height * 0.3f),
            size = Size(size.width * 0.07f, size.height * 0.4f),
            cornerRadius = CornerRadius(2f),
        )
        val inset = size.height * 0.2f
        drawRoundRect(
            fill,
            topLeft = Offset(inset, inset),
            size = Size((body.width - inset * 2) * percent / 100f, body.height - inset * 2),
            cornerRadius = CornerRadius(size.height * 0.12f),
        )
    }
}

/** Calendário do mês com o dia de hoje destacado. */
@Composable
fun CalendarCard(palette: LauncherPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val title = remember(now) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale)
            .format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    }
    val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = (first.get(Calendar.DAY_OF_WEEK) - first.firstDayOfWeek + 7) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val headers = remember {
        val fmt = SimpleDateFormat("EEEEE", locale)
        (0 until 7).map { i ->
            val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, (first.firstDayOfWeek - 1 + i) % 7 + 1) }
            fmt.format(c.time).uppercase(locale)
        }
    }
    val rows = (offset + days + 6) / 7
    Column(
        cardModifier(palette, modifier) {
            val calendarApp = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(calendarApp) }
        }.padding(12.dp),
    ) {
        Text(title, color = palette.cardText, fontWeight = FontWeight.SemiBold, fontSize = 14.dp.asFixedSp(), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val cellW = maxWidth / 7
            val cellH = maxHeight / (rows + 1)
            val cell = minOf(cellW, cellH)
            val font = (cell * 0.5f).coerceIn(7.dp, 15.dp).asFixedSp()
            Column {
                Row {
                    headers.forEach {
                        Box(Modifier.size(cellW, cellH), contentAlignment = Alignment.Center) {
                            Text(it.take(1), color = palette.cardTextSecondary, fontSize = font, maxLines = 1)
                        }
                    }
                }
                (0 until rows).forEach { r ->
                    Row {
                        (0 until 7).forEach { c ->
                            val day = r * 7 + c - offset + 1
                            Box(Modifier.size(cellW, cellH), contentAlignment = Alignment.Center) {
                                if (day in 1..days) {
                                    val isToday = day == today
                                    Box(
                                        Modifier.size(cell * 0.9f).clip(CircleShape).background(if (isToday) palette.accent else Color.Transparent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(day.toString(), color = if (isToday) palette.onAccent else palette.cardText, fontSize = font, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dia da semana em destaque e o próximo alarme (estilo iOS). */
@Composable
fun DayCard(palette: LauncherPalette, modifier: Modifier = Modifier, highlight: Color = Color(0xFFFF3B30)) {
    val context = LocalContext.current
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    val weekday = remember(now) { SimpleDateFormat("EEEE", locale).format(Date(now)).replaceFirstChar { it.titlecase(locale) } }
    val day = remember(now) { SimpleDateFormat("d", locale).format(Date(now)) }
    val alarm = remember(now) {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let {
            val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a"
            SimpleDateFormat(pattern, locale).format(Date(it))
        }
    }
    BoxWithConstraints(cardModifier(palette, modifier) { openClock(context) }.padding(14.dp)) {
        val big = minOf(maxHeight * 0.27f, maxWidth * 0.3f)
        Column {
            Text(weekday, color = highlight, fontWeight = FontWeight.SemiBold, fontSize = (big * 0.36f).coerceAtLeast(11.dp).asFixedSp(), maxLines = 1)
            Text(day, color = palette.cardText, fontSize = big.asFixedSp(), fontWeight = FontWeight.Normal, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(palette.accent))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Próximo alarme", color = palette.cardText, fontSize = (big * 0.3f).coerceAtLeast(10.dp).asFixedSp(), maxLines = 1)
                    Text(alarm ?: "Nenhum", color = palette.cardTextSecondary, fontSize = (big * 0.27f).coerceAtLeast(10.dp).asFixedSp(), maxLines = 1)
                }
            }
        }
    }
}

/** Bateria, memória e armazenamento em anéis (estilo monitor do KDE). */
@Composable
fun SystemMonitorCard(palette: LauncherPalette, modifier: Modifier = Modifier) {
    val battery = rememberBatteryState()
    val memory = rememberMemoryState()
    val context = LocalContext.current
    BoxWithConstraints(
        cardModifier(palette, modifier) {
            runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }.padding(10.dp),
    ) {
        val ring = minOf(maxWidth / 3 - 16.dp, maxHeight * 0.55f)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Gauge(palette, ring, (battery?.percent ?: 0) / 100f, "${battery?.percent ?: "--"}%", "Bateria")
            Gauge(palette, ring, (memory?.usedPercent ?: 0) / 100f, "${memory?.usedPercent ?: "--"}%", "Memória")
            val free = memory?.let { it.storageFreeGb } ?: 0f
            val total = memory?.storageTotalGb ?: 1f
            Gauge(palette, ring, 1f - free / total, memory?.let { "%.0f GB".format(it.storageFreeGb) } ?: "--", "Livre")
        }
    }
}

@Composable
private fun Gauge(palette: LauncherPalette, size: Dp, fraction: Float, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = this.size.width * 0.1f
                drawArc(palette.cardTextSecondary.copy(alpha = 0.25f), 0f, 360f, false, style = Stroke(stroke))
                drawArc(palette.accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(value, color = palette.cardText, fontSize = (size * 0.22f).coerceAtLeast(9.dp).asFixedSp(), maxLines = 1)
        }
        Text(label, color = palette.cardTextSecondary, fontSize = (size * 0.2f).coerceIn(9.dp, 13.dp).asFixedSp(), maxLines = 1)
    }
}

/** Barra de pesquisa que abre a busca do tema. */
@Composable
fun SearchBar(
    palette: LauncherPalette,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = palette.panel,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .glass(background, RoundedCornerShape(50), palette.panelStroke)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        } else {
            Icon(Icons.Default.Search, contentDescription = null, tint = palette.panelText)
            Spacer(Modifier.width(12.dp))
        }
        Text(placeholder, color = palette.panelTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (leading != null) Icon(Icons.Default.Search, contentDescription = null, tint = palette.panelText)
    }
}
