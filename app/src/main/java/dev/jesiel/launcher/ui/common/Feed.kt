package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.data.NewsItem
import dev.jesiel.launcher.data.NewsState
import dev.jesiel.launcher.data.NewsTopic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tela à esquerda no estilo do Discover: saudação, busca, abas de assunto e cartões de notícia
 * com imagem. As matérias abrem no leitor interno do launcher.
 */
@Composable
fun FeedScreen(controller: LauncherController, palette: LauncherPalette, modifier: Modifier = Modifier) {
    val allNews by controller.viewModel.news.collectAsStateWithLifecycle()
    var topic by rememberSaveable { mutableStateOf(NewsTopic.FOR_YOU) }
    LaunchedEffect(topic) { controller.viewModel.refreshNews(topic) }
    val news = allNews[topic] ?: NewsState.Loading

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, style = palette.wallpaperLabel)
                    Text(date, fontSize = 15.sp, style = palette.wallpaperLabel)
                }
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Atualizar notícias",
                    tint = palette.onWallpaper,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { controller.viewModel.refreshNews(topic, force = true) }.padding(8.dp),
                )
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NewsTopic.entries) { t ->
                    val on = t == topic
                    Text(
                        t.label,
                        color = if (on) palette.onAccent else palette.cardText,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) palette.accent else palette.card)
                            .clickable { topic = t }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        when (val state = news) {
            NewsState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
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
            is NewsState.Loaded -> items(state.items.take(40), key = { it.link }) { item ->
                NewsCard(item, now, palette) { controller.openArticle(item.link) }
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, now: Long, palette: LauncherPalette, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(palette.card.copy(alpha = maxOf(palette.card.alpha, 0.85f)), RoundedCornerShape(20.dp), palette.cardStroke)
            .clickable(onClick = onClick),
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(palette.tile),
            )
        }
        Column(Modifier.padding(14.dp)) {
            Text(
                item.title,
                color = palette.cardText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(palette.accent))
                Spacer(Modifier.width(6.dp))
                Text(
                    listOfNotNull(item.source, item.publishedAt?.let { relativeTime(it, now) }).joinToString(" · "),
                    color = palette.cardTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
