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
