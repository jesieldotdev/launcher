package dev.jesiel.launcher.ui.common

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import dev.jesiel.launcher.widgets.WidgetHost

/** Mostra um widget Android já vinculado ao launcher. */
@Composable
fun AppWidget(id: Int, widgets: WidgetHost, modifier: Modifier = Modifier) {
    val info = remember(id) { widgets.info(id) } ?: return
    val density = LocalDensity.current
    val minHeight = with(density) { info.minHeight.toDp() }.coerceAtLeast(72.dp)
    AndroidView(
        factory = { ctx -> widgets.createView(ctx, id)!! },
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight),
    )
}

private class ProviderEntry(val info: AppWidgetProviderInfo, val label: String, val appLabel: String, val icon: Drawable?)

/** Lista os widgets instalados, agrupados pelo app que os fornece. */
@Composable
fun WidgetPickerDialog(widgets: WidgetHost, onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries = remember {
        val pm = context.packageManager
        val dpi = context.resources.displayMetrics.densityDpi
        widgets.providers().map { info ->
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            ProviderEntry(info, info.loadLabel(pm), appLabel, runCatching { info.loadIcon(context, dpi) }.getOrNull())
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar widget") },
        text = {
            if (entries.isEmpty()) {
                Text("Nenhum widget disponível neste aparelho.")
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(entries, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(entry.info) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            entry.icon?.let {
                                Image(it.toBitmap(96, 96).asImageBitmap(), null, Modifier.size(36.dp))
                            } ?: Spacer(Modifier.width(36.dp))
                            Column {
                                Text(entry.label, fontSize = 15.sp)
                                Text(entry.appLabel, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
