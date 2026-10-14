package dev.jesiel.launcher

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.jesiel.launcher.data.HtmlVariables
import dev.jesiel.launcher.data.HtmlWidget
import dev.jesiel.launcher.data.WidgetStorage
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WidgetStorageTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun eachWidgetHasItsOwnStorage() {
        val a = WidgetStorage.Bridge(app, "a")
        val b = WidgetStorage.Bridge(app, "b")
        assertTrue(a.setItem("tarefas", "[\"Teste\"]"))
        a.setItem("pasta", "Todas")
        assertEquals("[\"Teste\"]", a.getItem("tarefas"))
        assertNull(b.getItem("tarefas"))
        assertEquals(listOf("pasta", "tarefas"), JSONArray(a.keys()).let { j -> (0 until j.length()).map(j::getString) })
        a.removeItem("pasta")
        assertNull(a.getItem("pasta"))
        // Limite por widget.
        assertFalse(a.setItem("grande", "x".repeat(WidgetStorage.MAX_PER_WIDGET)))
        WidgetStorage.copy(app, "a", "c")
        assertEquals("[\"Teste\"]", WidgetStorage.get(app, "c", "tarefas"))
        a.clear()
        assertTrue(WidgetStorage.entries(app, "a").isEmpty())
        assertEquals(1, WidgetStorage.entries(app, "c").size)
    }

    @Test
    fun storageGoesIntoBackupsAndIsDeletedWithTheWidget() {
        val vm = LauncherViewModel(app)
        vm.saveHtmlWidget(HtmlWidget("w1", "Tarefas", "<script></script>", interactive = true))
        WidgetStorage.set(app, "w1", "tarefas", "[1,2]")
        val saved = vm.backups.snapshot("teste")!!
        WidgetStorage.set(app, "w1", "tarefas", "[]")
        vm.backups.restore(saved)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("[1,2]", WidgetStorage.get(app, "w1", "tarefas"))
        vm.removeHtmlWidget("w1")
        assertNull(WidgetStorage.get(app, "w1", "tarefas"))
    }

    @Test
    fun storageScriptOnlyInInteractiveMode() {
        val interactive = HtmlVariables.render("<script>localStorage.setItem('a','1')</script>", emptyMap(), interactive = true)
        assertTrue(interactive.contains("__launcherStorage") && interactive.indexOf("__launcherStorage") < interactive.indexOf("localStorage.setItem('a'"))
        assertFalse(HtmlVariables.render("<p>x</p>", emptyMap(), interactive = false).contains("__launcherStorage"))
        // Para conferir no navegador.
        java.io.File("build/storage-sample.html").writeText(
            HtmlVariables.render(
                "<p id=o></p><script>var n = Number(localStorage.getItem('n') || 0) + 1; localStorage.setItem('n', n); localStorage.lista = JSON.stringify([1]);" +
                    "document.getElementById('o').textContent = n + '|' + localStorage.length + '|' + JSON.parse(localStorage.lista)[0];</script>",
                emptyMap(), interactive = true,
            ),
        )
    }
}
