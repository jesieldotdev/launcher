package dev.jesiel.launcher.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.LockSettings
import dev.jesiel.launcher.data.LockStyle
import dev.jesiel.launcher.data.MediaWatcher
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Conteúdo da tela de bloqueio do Launcher: do tema ou um widget HTML em tela cheia. */
@Composable
fun LockScreenContent(
    settings: LockSettings,
    state: LauncherUiState,
    controller: LauncherController,
    palette: LauncherPalette,
    /** Widget a mostrar no lugar do configurado (o testador usa o rascunho do editor). */
    widgetOverride: dev.jesiel.launcher.data.HtmlWidget? = null,
    onConsole: ((level: String, message: String, line: Int) -> Unit)? = null,
    storageId: String? = null,
    onUnlock: () -> Unit,
) {
    val widget = widgetOverride ?: if (settings.style == LockStyle.HTML) state.htmlWidgets.firstOrNull { it.id == settings.htmlWidgetId } else null
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    val threshold = with(density) { 110.dp.toPx() }
    // Deslizar para cima: o conteúdo sobe e some; passou do limite, desbloqueia.
    val swipe = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (-lift.value > threshold) onUnlock()
                scope.launch { lift.animateTo(0f) }
            },
            onDragCancel = { scope.launch { lift.animateTo(0f) } },
        ) { change, amount ->
            change.consume()
            scope.launch { lift.snapTo((lift.value + amount).coerceAtMost(0f)) }
        }
    }
    val progress = (-lift.value / threshold).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().background(Color(0x40000000))) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = lift.value * 0.6f
                    alpha = 1f - progress * 0.6f
                },
        ) {
            if (widget != null) {
                HtmlWidgetView(widget, controller, palette, Modifier.fillMaxSize(), onConsole = onConsole, storageId = storageId ?: widget.id)
            } else {
                Box(Modifier.fillMaxSize().then(swipe)) { ThemeLock(state, palette) }
            }
        }
        // Faixa de desbloqueio (no HTML é só aqui, para não brigar com os toques da página).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(swipe)
                .clickable(onClick = onUnlock)
                .navigationBarsPadding()
                .padding(top = 28.dp, bottom = 18.dp),
        ) {
            Text("︿", color = palette.onWallpaper.copy(alpha = 0.6f + 0.4f * progress), fontSize = 22.sp)
            Text("Deslize para desbloquear", color = palette.onWallpaper.copy(alpha = 0.8f), fontSize = 13.sp, style = palette.wallpaperLabel)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(120.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.onWallpaper.copy(alpha = 0.7f)))
        }
    }
}

/** Tela de bloqueio no estilo do tema: relógio grande, data, bateria, notificações e música. */
@Composable
private fun ThemeLock(state: LauncherUiState, palette: LauncherPalette) {
    val context = LocalContext.current
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    val battery = rememberBatteryState()
    val music by MediaWatcher.nowPlaying.collectAsState()
    val notifications by MediaWatcher.notifications.collectAsState()
    val time = remember(now) {
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(Date(now))
    }
    val date = remember(now) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(Date(now))
            .replaceFirstChar { it.titlecase(locale) }
    }
    // Temas com fonte própria (Retrô) usam a cor de destaque no relógio.
    val clockColor = if (palette.font != null) palette.accent else palette.onWallpaper
    val shadow = palette.wallpaperLabel.shadow

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Text(date, style = palette.wallpaperLabel.copy(fontSize = 18.sp))
        Text(
            time,
            color = clockColor,
            fontSize = 96.sp,
            fontWeight = if (palette.font != null) FontWeight.Black else FontWeight.Light,
            fontFamily = palette.font,
            style = androidx.compose.ui.text.TextStyle(shadow = shadow),
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            battery?.let {
                Text("${it.percent}%" + if (it.charging) " · Carregando" else "", style = palette.wallpaperLabel.copy(fontSize = 15.sp))
            }
            if (notifications > 0) {
                Text(
                    "$notifications notificaç" + if (notifications == 1) "ão" else "ões",
                    style = palette.wallpaperLabel.copy(fontSize = 15.sp),
                )
            }
        }
        val items by MediaWatcher.notificationItems.collectAsState()
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().glass(palette.card, palette.cardShape, palette.cardStroke).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.take(3).forEach { n ->
                    Text(
                        dev.jesiel.launcher.data.HtmlVariables.notificationLine(n),
                        color = palette.cardText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        music?.let { m -> MusicCard(m, palette) }
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun MusicCard(m: dev.jesiel.launcher.data.NowPlaying, palette: LauncherPalette) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(palette.card, palette.cardShape, palette.cardStroke)
            .padding(16.dp),
    ) {
        Text(m.appLabel, color = palette.cardTextSecondary, fontSize = 12.sp, maxLines = 1)
        Text(m.title, color = palette.cardText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (m.artist.isNotEmpty()) Text(m.artist, color = palette.cardTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (m.durationMs > 0) {
            val fraction = (m.positionNow().toFloat() / m.durationMs).coerceIn(0f, 1f)
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.cardTextSecondary.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(Brush.horizontalGradient(listOf(palette.accent, palette.accent))))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("⏮" to "anterior", (if (m.playing) "⏸" else "▶") to "alternar", "⏭" to "proxima").forEach { (label, action) ->
                Text(
                    label,
                    color = palette.cardText,
                    fontSize = 26.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { MediaWatcher.command(action) }.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
        }
    }
}
