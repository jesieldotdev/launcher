package dev.jesiel.launcher

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.jesiel.launcher.data.HtmlRole
import dev.jesiel.launcher.data.HtmlWidget
import dev.jesiel.launcher.data.LockScreen
import dev.jesiel.launcher.data.LockStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WidgetKindsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun eachWidgetOnlyWorksInItsOwnPlace() {
        // Widgets de antes dos tipos: um relógio usado como tela de bloqueio (e também na área de trabalho).
        val first = LauncherViewModel(app)
        idle()
        first.saveHtmlWidget(HtmlWidget("lock", "Relógio ISS", "<b>{{hora}}</b>", onDesktop = true))
        first.saveHtmlWidget(HtmlWidget("clock", "Relógio", "<b>{{hora}}</b>", onDesktop = true))
        LockScreen.update(app) { it.copy(enabled = true, style = LockStyle.HTML, htmlWidgetId = "lock") }
        idle()

        // Ao abrir de novo, cada um recebe o tipo do lugar onde está em uso.
        val vm = LauncherViewModel(app)
        idle()
        val lock = vm.state.value.htmlWidgets.first { it.id == "lock" }
        assertEquals(HtmlRole.LOCK, lock.kind)
        assertFalse("widget de bloqueio sai da área de trabalho", lock.onDesktop)
        assertEquals(HtmlRole.DESKTOP, vm.state.value.htmlWidgets.first { it.id == "clock" }.kind)

        // Um widget de área de trabalho não vira lista de apps nem dock…
        vm.setAppsHtml("clock")
        vm.setDockHtml("clock")
        idle()
        assertNull(vm.state.value.appsHtml)
        assertNull(vm.state.value.dockHtml)
        // …e um de bloqueio não vai para a área de trabalho.
        vm.setHtmlWidgetOnDesktop("lock", true)
        idle()
        assertFalse(vm.state.value.htmlWidgets.first { it.id == "lock" }.onDesktop)
    }
}
