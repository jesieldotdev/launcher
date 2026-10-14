package dev.jesiel.launcher

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.jesiel.launcher.data.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun restoringBringsBackPositionsAndTheme() {
        val vm = LauncherViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.savePlacements("windows", mapOf("app/chrome" to Placement(0, 2f, 3f, 1f, 1f)))
        vm.setDockHtmlHeight(120)
        val saved = vm.backups.snapshot("teste")!!

        vm.setTheme("ios")
        vm.setDockHtmlHeight(60)
        vm.savePlacements("ios", mapOf("app/chrome" to Placement(0, 0f, 0f, 1f, 1f)))
        idle()
        assertEquals("ios", vm.state.value.themeId)
        // Trocar de tema já fez um backup automático.
        assertTrue(vm.backups.list().any { it.reason.contains("ios") })

        vm.backups.restore(saved)
        idle()
        assertEquals("windows", vm.state.value.themeId)
        assertEquals(Placement(0, 2f, 3f, 1f, 1f), vm.state.value.placements["app/chrome"])
        // Números (como a altura da dock em HTML) voltam como números.
        assertEquals(120, vm.state.value.dockHtmlHeight)
        // A restauração também guarda o estado anterior.
        assertTrue(vm.backups.list().any { it.reason == "Antes de restaurar" })
    }
}
