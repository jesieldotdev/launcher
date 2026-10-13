package dev.jesiel.launcher.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Posição de um item na área de trabalho, em células da grade (frações no modo livre). */
data class Placement(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float) {
    fun overlaps(o: Placement): Boolean =
        page == o.page && x < o.x + o.w - EPS && o.x < x + w - EPS && y < o.y + o.h - EPS && o.y < y + h - EPS

    fun snapped(): Placement = copy(
        x = x.roundToInt().toFloat(),
        y = y.roundToInt().toFloat(),
        w = max(1, w.roundToInt()).toFloat(),
        h = max(1, h.roundToInt()).toFloat(),
    )

    private companion object {
        const val EPS = 0.01f
    }
}

/** Tamanho padrão de um item, em células. */
data class CellSize(val w: Float, val h: Float)

/**
 * Regras de posicionamento: completa posições que faltam, mantém itens dentro da grade e
 * procura o espaço livre mais próximo quando um item é solto sobre outro.
 */
class DesktopGrid(
    val columns: Int,
    /** Linhas visíveis por página. */
    val rows: Int,
    val multiPage: Boolean,
    val pageCount: Int,
) {
    /** Página única: ela cresce para baixo com rolagem; várias páginas: tamanho fixo. */
    private val rowLimit: Int get() = if (multiPage) rows else Int.MAX_VALUE / 4

    fun clamp(p: Placement): Placement {
        val w = p.w.coerceIn(0.5f, columns.toFloat())
        val h = p.h.coerceIn(0.5f, if (multiPage) rows.toFloat() else 100f)
        return p.copy(
            page = if (multiPage) p.page.coerceAtLeast(0) else 0,
            w = w,
            h = h,
            x = p.x.coerceIn(0f, columns - w),
            y = p.y.coerceIn(0f, if (multiPage) rows - h else Float.MAX_VALUE / 4),
        )
    }

    /**
     * Devolve a posição de todos os [items] (id → tamanho padrão), reaproveitando [saved] e
     * encaixando os que ainda não têm posição no primeiro espaço livre.
     */
    fun resolve(items: List<Pair<String, CellSize>>, saved: Map<String, Placement>): Map<String, Placement> {
        val result = LinkedHashMap<String, Placement>()
        val missing = mutableListOf<Pair<String, CellSize>>()
        for ((id, size) in items) {
            val p = saved[id]
            if (p == null || (!multiPage && p.page > 0)) missing += id to size else result[id] = clamp(p)
        }
        for ((id, size) in missing) {
            result[id] = firstFree(size, result.values)
        }
        return result
    }

    private fun firstFree(size: CellSize, taken: Collection<Placement>): Placement {
        val w = size.w.coerceAtMost(columns.toFloat())
        val h = size.h
        var page = 0
        while (true) {
            val onPage = taken.filter { it.page == page }
            // Além das linhas inteiras, tenta logo abaixo de cada item, para não deixar vãos
            // quando há componentes com altura fracionária.
            val maxRow = if (multiPage) rows else (onPage.maxOfOrNull { ceil(it.y + it.h).toInt() } ?: 0) + 2
            val ys = ((0..maxRow).map { it.toFloat() } + onPage.map { it.y + it.h }).distinct().sorted()
            for (y in ys) {
                if (y + h > rowLimit + EPSILON) continue
                var x = 0f
                while (x + w <= columns + EPSILON) {
                    val candidate = Placement(page, x, y, w, h)
                    if (onPage.none { it.overlaps(candidate) }) return candidate
                    x += 1f
                }
            }
            if (!multiPage) return Placement(0, 0f, maxRow.toFloat(), w, h)
            page++
            if (page > pageCount + 20) return Placement(0, 0f, 0f, w, h)
        }
    }

    /** Espaço livre mais próximo de [target] (no modo grade), ignorando o próprio item [selfId]. */
    fun nearestFree(target: Placement, selfId: String, all: Map<String, Placement>): Placement {
        val others = all.filterKeys { it != selfId }.values
        val snapped = clamp(target.snapped())
        if (others.none { it.overlaps(snapped) }) return snapped
        val maxY = if (multiPage) rows - snapped.h.toInt() else (others.maxOfOrNull { (it.y + it.h).toInt() } ?: 0) + 1
        var best: Placement? = null
        var bestDist = Float.MAX_VALUE
        for (y in 0..max(0, maxY)) for (x in 0..(columns - snapped.w.toInt())) {
            val c = snapped.copy(x = x.toFloat(), y = y.toFloat())
            if (others.any { it.overlaps(c) }) continue
            val d = abs(c.x - target.x) + abs(c.y - target.y)
            if (d < bestDist) {
                bestDist = d
                best = c
            }
        }
        return best ?: all[selfId] ?: snapped
    }

    companion object {
        private const val EPSILON = 0.001f

        fun rowsFor(heightPx: Float, cellHeightPx: Float): Int = max(3, floor(heightPx / cellHeightPx).toInt())
    }
}
