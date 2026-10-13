package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.data.NewsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tela à esquerda: saudação, busca, atalho para o Discover do Google e as principais notícias
 * do Google Notícias.
 */
@Composable
fun FeedScreen(controller: LauncherController, palette: LauncherPalette, modifier: Modifier = Modifier) {
    val news by controller.viewModel.news.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { controller.viewModel.refreshNews() }
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    val greeting = remember(now) {
        when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Bom dia"
            in 12..17 -> "Boa tarde"
            else -> "Boa noite"
        }
    }
    val date = remember(now) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
            .format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                Text(greeting, color = palette.onWallpaper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, style = palette.wallpaperLabel)
                Text(date, color = palette.onWallpaper, fontSize = 15.sp, style = palette.wallpaperLabel)
            }
        }
        item {
            SearchBar(
                palette = palette,
                placeholder = "Pesquisar no Google",
                onClick = { controller.webSearch("") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeedButton(Icons.Default.TravelExplore, "Discover", palette, Modifier.weight(1f)) { controller.openGoogleApp() }
                FeedButton(Icons.Default.Newspaper, "Google Notícias", palette, Modifier.weight(1f)) { controller.openUrl("https://news.google.com") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) {
                Text("Principais notícias", color = palette.onWallpaper, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, style = palette.wallpaperLabel, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Atualizar notícias",
                    tint = palette.onWallpaper,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).clickable { controller.viewModel.refreshNews(force = true) }.padding(6.dp),
                )
            }
        }
        when (val state = news) {
            NewsState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }
            is NewsState.Failed -> item {
                Text(
                    "${state.message} Verifique a conexão e toque em atualizar.",
                    color = palette.cardText,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().glass(palette.card, palette.cardShape, palette.cardStroke).padding(16.dp),
                )
            }
            is NewsState.Loaded -> items(state.items.take(30), key = { it.link }) { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(palette.card, palette.cardShape, palette.cardStroke)
                        .clickable { controller.openUrl(item.link) }
                        .padding(14.dp),
                ) {
                    Text(item.title, color = palette.cardText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(item.source.ifBlank { null }, item.publishedAt?.let { relativeTime(it, now) }).joinToString(" · "),
                        color = palette.cardTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, palette: LauncherPalette, modifier: Modifier, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .glass(palette.card, RoundedCornerShape(16.dp), palette.cardStroke)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = palette.accent)
        Spacer(Modifier.width(10.dp))
        Text(label, color = palette.cardText, fontSize = 14.sp, maxLines = 1)
    }
}

/** Fundo atrás do feed, para dar contraste sobre o papel de parede. */
fun Modifier.feedScrim(palette: LauncherPalette): Modifier =
    background(if (palette.dark) androidx.compose.ui.graphics.Color(0x66000000) else androidx.compose.ui.graphics.Color(0x33000000))
