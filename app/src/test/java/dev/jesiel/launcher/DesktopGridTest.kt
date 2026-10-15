
class PushDownTest {
    /** Soltar um widget sobre ícones: os de baixo descem juntos, na mesma coluna e ordem. */
    @org.junit.Test
    fun iconsUnderADroppedWidgetMoveDown() {
        val grid = dev.jesiel.launcher.data.DesktopGrid(columns = 4, rows = 8, multiPage = false, pageCount = 1, pushDown = true)
        fun at(x: Int, y: Int, w: Int = 1, h: Int = 1) = dev.jesiel.launcher.data.Placement(0, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
        val all = mapOf("a" to at(0, 2), "b" to at(1, 2), "c" to at(0, 3), "longe" to at(3, 0))
        val result = grid.pushApart("w", at(0, 2, 4, 2), all)
        org.junit.Assert.assertEquals(at(0, 4), result["a"])
        org.junit.Assert.assertEquals(at(1, 4), result["b"])
        org.junit.Assert.assertEquals("desce mais um para não bater no que desceu", at(0, 5), result["c"])
        org.junit.Assert.assertEquals("quem não estava no caminho não se mexe", at(3, 0), result["longe"])
        org.junit.Assert.assertEquals(at(0, 2, 4, 2), result["w"])
    }
}
