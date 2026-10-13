package dev.jesiel.launcher

import dev.jesiel.launcher.ui.common.normalizedForSearch
import dev.jesiel.launcher.ui.common.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TextTest {

    @Test
    fun searchIgnoresAccentsAndCase() {
        assertEquals("camera", "Câmera".normalizedForSearch())
        assertEquals("configuracoes", "Configurações".normalizedForSearch())
    }

    @Test
    fun relativeTimeInPortuguese() {
        val now = 10L * 24 * 3_600_000
        assertEquals("agora", relativeTime(now - 10_000, now))
        assertEquals("há 5 min", relativeTime(now - 5 * 60_000, now))
        assertEquals("há 2 h", relativeTime(now - 2 * 3_600_000, now))
        assertEquals("ontem", relativeTime(now - 30 * 3_600_000, now))
        assertEquals("há 3 dias", relativeTime(now - 3 * 24 * 3_600_000, now))
    }
}

class DesktopGridTest {

    private val grid = dev.jesiel.launcher.data.DesktopGrid(columns = 4, rows = 6, multiPage = false, pageCount = 1)

    @Test
    fun freeModePlacesItemsRightBelowFractionalComponents() {
        val free = dev.jesiel.launcher.data.DesktopGrid(columns = 4, rows = 6, multiPage = false, pageCount = 1, free = true)
        val placed = free.resolve(
            listOf(
                "clock" to dev.jesiel.launcher.data.CellSize(4f, 1.4f),
                "a" to dev.jesiel.launcher.data.CellSize(1f, 1f),
                "b" to dev.jesiel.launcher.data.CellSize(1f, 1f),
            ),
            emptyMap(),
        )
        assertEquals(1.4f, placed.getValue("a").y, 0.001f)
        assertEquals(1f, placed.getValue("b").x, 0.001f)
    }

    @Test
    fun gridModeKeepsEverythingOnWholeCells() {
        val placed = grid.resolve(
            listOf("clock" to dev.jesiel.launcher.data.CellSize(4f, 1.4f), "a" to dev.jesiel.launcher.data.CellSize(1f, 1f)),
            mapOf("b" to dev.jesiel.launcher.data.Placement(0, 0.3f, 2.6f, 1f, 1f)),
        )
        assertEquals(2f, placed.getValue("clock").h, 0.001f)
        assertEquals(2f, placed.getValue("a").y, 0.001f)
        val snapped = grid.resolve(listOf("b" to dev.jesiel.launcher.data.CellSize(1f, 1f)), mapOf("b" to dev.jesiel.launcher.data.Placement(0, 0.3f, 2.6f, 1f, 1f)))
        assertEquals(dev.jesiel.launcher.data.Placement(0, 0f, 3f, 1f, 1f), snapped.getValue("b"))
    }

    @Test
    fun dropOnOccupiedCellMovesToNearestFreeCell() {
        val all = mapOf(
            "a" to dev.jesiel.launcher.data.Placement(0, 0f, 0f, 1f, 1f),
            "b" to dev.jesiel.launcher.data.Placement(0, 1f, 0f, 1f, 1f),
        )
        val moved = grid.nearestFree(dev.jesiel.launcher.data.Placement(0, 0.2f, 0.1f, 1f, 1f), "b", all)
        assertEquals(0, moved.page)
        assert(!moved.overlaps(all.getValue("a")))
    }

    @Test
    fun multiPageOverflowGoesToNextPage() {
        val paged = dev.jesiel.launcher.data.DesktopGrid(columns = 2, rows = 1, multiPage = true, pageCount = 1)
        val placed = paged.resolve((1..3).map { "i$it" to dev.jesiel.launcher.data.CellSize(1f, 1f) }, emptyMap())
        assertEquals(1, placed.getValue("i3").page)
    }
}

class CollisionTest {

    private fun p(x: Float, y: Float, w: Float = 1f, h: Float = 1f) = dev.jesiel.launcher.data.Placement(0, x, y, w, h)

    @Test
    fun gridDropPushesOccupantToNearestCell() {
        val grid = dev.jesiel.launcher.data.DesktopGrid(columns = 4, rows = 6, multiPage = true, pageCount = 1)
        val all = mapOf("a" to p(0f, 0f), "b" to p(1f, 0f))
        val result = grid.pushApart("b", p(0.1f, 0.2f), all)
        assertEquals(p(0f, 0f), result.getValue("b"))
        val a = result.getValue("a")
        assert(!a.overlaps(result.getValue("b")))
        assertEquals(1f, kotlin.math.abs(a.x - 0f) + kotlin.math.abs(a.y - 0f), 0.001f)
    }

    @Test
    fun freeDropNeverLeavesOverlaps() {
        val grid = dev.jesiel.launcher.data.DesktopGrid(columns = 4, rows = 6, multiPage = true, pageCount = 1, free = true)
        val all = mapOf("a" to p(1f, 1f), "b" to p(2f, 1f), "w" to p(0f, 3f, 4f, 2f))
        val result = grid.pushApart("w", p(0.5f, 0.6f, 4f, 2f), all)
        val items = result.values.toList()
        for (i in items.indices) for (j in i + 1 until items.size) assert(!items[i].overlaps(items[j])) { "${items[i]} x ${items[j]}" }
    }

    @Test
    fun resizingPushesNeighbours() {
        val grid = dev.jesiel.launcher.data.DesktopGrid(columns = 4, rows = 6, multiPage = true, pageCount = 1)
        val all = mapOf("clock" to p(0f, 0f, 2f, 1f), "a" to p(2f, 0f), "b" to p(3f, 0f))
        val result = grid.pushApart("clock", p(0f, 0f, 4f, 1f), all)
        assert(!result.getValue("a").overlaps(result.getValue("clock")))
        assert(!result.getValue("b").overlaps(result.getValue("clock")))
        assert(!result.getValue("a").overlaps(result.getValue("b")))
    }
}
