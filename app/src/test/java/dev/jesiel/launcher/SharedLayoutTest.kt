package dev.jesiel.launcher

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.jesiel.launcher.data.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SharedLayoutTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun positionsSurviveThemeChanges() {
        val vm = LauncherViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.setTheme("windows")
        vm.savePlacements("windows", mapOf("app/chrome" to Placement(0, 2f, 3f, 1f, 1f), "builtin:clock" to Placement(0, 0f, 0f, 2f, 2f)))

        vm.setTheme("kde")
        idle()
        assertEquals(Placement(0, 2f, 3f, 1f, 1f), vm.state.value.placements["app/chrome"])
        // Componentes próprios de cada tema não passam para o outro.
        assertNull(vm.state.value.placements["builtin:clock"])

        // O tema que está saindo não grava no tema novo.
        vm.savePlacements("windows", mapOf("app/chrome" to Placement(0, 0f, 0f, 1f, 1f)))
        idle()
        assertEquals(Placement(0, 2f, 3f, 1f, 1f), vm.state.value.placements["app/chrome"])

        vm.savePlacements("kde", mapOf("app/chrome" to Placement(0, 1f, 1f, 1f, 1f)))
        vm.setTheme("windows")
        idle()
        assertEquals(Placement(0, 1f, 1f, 1f, 1f), vm.state.value.placements["app/chrome"])
        assertEquals(Placement(0, 0f, 0f, 2f, 2f), vm.state.value.placements["builtin:clock"])
    }
}
