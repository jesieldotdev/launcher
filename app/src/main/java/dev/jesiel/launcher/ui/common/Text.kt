package dev.jesiel.launcher.ui.common

import dev.jesiel.launcher.data.AppInfo
import java.text.Normalizer

/** Remove acentos e caixa para comparar textos na busca ("Câmera" casa com "camera"). */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Apps cujo nome contém [query], com os que começam pelo termo primeiro. */
fun List<AppInfo>.search(query: String): List<AppInfo> {
    val q = query.trim().normalizedForSearch()
    if (q.isEmpty()) return emptyList()
    return map { it to it.label.normalizedForSearch() }
        .filter { (_, label) -> label.contains(q) }
        .sortedBy { (_, label) ->
            when {
                label.startsWith(q) -> 0
                label.split(' ').any { it.startsWith(q) } -> 1
                else -> 2
            }
        }
        .map { it.first }
}

/** "agora", "há 5 min", "há 2 h", "ontem", "há 3 dias". */
fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - timestamp).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "há $minutes min"
        minutes < 24 * 60 -> "há ${minutes / 60} h"
        minutes < 48 * 60 -> "ontem"
        else -> "há ${minutes / (24 * 60)} dias"
    }
}
