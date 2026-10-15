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
    /** Posição livre; na grade, posições e tamanhos são sempre células inteiras. */
    val free: Boolean = false,
    /** Itens no caminho descem (em cascata, mantendo a coluna) em vez de irem para qualquer vão. */
    val pushDown: Boolean = false,
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
     * encaixando os que ainda não têm posição no primeiro espaço livre. Na grade, posições
     * fracionárias (vindas do modo livre) são encaixadas na célula livre mais próxima.
     */
    fun resolve(items: List<Pair<String, CellSize>>, saved: Map<String, Placement>): Map<String, Placement> {
        val result = LinkedHashMap<String, Placement>()
        val missing = mutableListOf<Pair<String, CellSize>>()
        for ((id, size) in items) {
            val p = saved[id]
            when {
                p == null || (!multiPage && p.page > 0) -> missing += id to size
                free -> result[id] = clamp(p)
                else -> result[id] = nearestFreeAmong(p, result.values)
            }
        }
        for ((id, size) in missing) {
            // Na grade, componentes com tamanho fracionário ocupam a célula inteira seguinte.
            val s = if (free) size else CellSize(max(1, ceil(size.w - 0.1f).toInt()).toFloat(), max(1, ceil(size.h - 0.1f).toInt()).toFloat())
            result[id] = firstFree(s, result.values)
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
            val integerRows = (0..maxRow).map { it.toFloat() }
            val ys = if (free) (integerRows + onPage.map { it.y + it.h }).distinct().sorted() else integerRows
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
    fun nearestFree(target: Placement, selfId: String, all: Map<String, Placement>): Placement =
        nearestFreeAmong(target, all.filterKeys { it != selfId }.values, fallback = all[selfId])

    private fun nearestFreeAmong(target: Placement, others: Collection<Placement>, fallback: Placement? = null): Placement {
        val snapped = clamp(target.snapped())
        if (others.none { it.overlaps(snapped) }) return snapped
        val maxY = if (multiPage) rows - snapped.h.toInt() else (others.maxOfOrNull { ceil(it.y + it.h).toInt() } ?: 0) + 1
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
        return best ?: fallback ?: firstFree(CellSize(snapped.w, snapped.h), others)
    }

    /**
     * Colisão: põe [selfId] em [target] e afasta os itens que ficariam por baixo dele para o
     * espaço livre mais próximo de onde estavam. Devolve as novas posições de todos os itens.
     */
    fun pushApart(selfId: String, target: Placement, all: Map<String, Placement>): Map<String, Placement> {
        val placed = clamp(if (free) target else target.snapped())
        if (pushDown) return pushDownFrom(selfId, placed, all)
        val result = LinkedHashMap(all)
        result[selfId] = placed
        val hit = all.filter { (id, p) -> id != selfId && p.overlaps(placed) }
            .entries.sortedBy { (_, p) -> abs(p.x - placed.x) + abs(p.y - placed.y) }
        for ((id, p) in hit) {
            val obstacles = result.filterKeys { it != id }.values
            result[id] = closestFreeSpot(p, obstacles)
        }
        return result
    }

    /**
     * Empurrar para baixo: o item solto fica onde foi solto; o que estiver embaixo dele desce para
     * logo abaixo, e assim por diante (cascata). A ordem e as colunas se mantêm. Numa tela de
     * tamanho fixo, o que passar do fim vai para o espaço livre mais próximo.
     */
    private fun pushDownFrom(selfId: String, placed: Placement, all: Map<String, Placement>): Map<String, Placement> {
        val result = LinkedHashMap(all)
        result[selfId] = placed
        // Quem já foi posicionado nesta rodada não se mexe mais (evita ciclos).
        val settled = mutableSetOf(selfId)
        var guard = 0
        while (guard++ < 1000) {
            val fixed = result.filterKeys { it in settled }
            // O próximo a descer: o mais alto entre os que batem num item já posicionado.
            val hit = result.entries
                .filter { (id, p) -> id !in settled && fixed.values.any { it.overlaps(p) } }
                .minByOrNull { it.value.y + it.value.x / 100f } ?: break
            val p = hit.value
            val blockers = fixed.values.filter { it.overlaps(p) }
            var moved = p.copy(y = blockers.maxOf { it.y + it.h })
            // Desceu e bateu em outro já posicionado: continua descendo.
            while (fixed.values.any { it.overlaps(moved) }) moved = moved.copy(y = fixed.values.filter { it.overlaps(moved) }.maxOf { it.y + it.h })
            if (multiPage && moved.y + moved.h > rows + EPSILON) {
                moved = closestFreeSpot(p, result.filterKeys { it != hit.key }.values)
            }
            result[hit.key] = moved
            settled += hit.key
        }
        return result
    }

    /** Posição livre mais próxima de [p] (passos de célula na grade, de 1/4 no modo livre). */
    private fun closestFreeSpot(p: Placement, obstacles: Collection<Placement>): Placement {
        val step = if (free) 0.25f else 1f
        val onPage = obstacles.filter { it.page == p.page }
        val maxY = if (multiPage) rows - p.h else (onPage.maxOfOrNull { it.y + it.h } ?: 0f) + p.h
        var best: Placement? = null
        var bestDist = Float.MAX_VALUE
        var y = 0f
        while (y <= maxY + EPSILON) {
            var x = 0f
            while (x + p.w <= columns + EPSILON) {
                val c = p.copy(x = x, y = y)
                if (onPage.none { it.overlaps(c) }) {
                    val d = abs(c.x - p.x) + abs(c.y - p.y) * 1.2f
                    if (d < bestDist) {
                        bestDist = d
                        best = c
                    }
                }
                x += step
            }
            y += step
        }
        return best ?: firstFree(CellSize(p.w, p.h), obstacles)
    }

    companion object {
        private const val EPSILON = 0.001f

        fun rowsFor(heightPx: Float, cellHeightPx: Float): Int = max(3, floor(heightPx / cellHeightPx).toInt())
    }
}
