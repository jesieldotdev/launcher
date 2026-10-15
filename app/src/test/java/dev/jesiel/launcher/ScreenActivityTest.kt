package dev.jesiel.launcher

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.jesiel.launcher.data.HtmlRole
import dev.jesiel.launcher.data.HtmlVariables
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<LauncherApp>()

    /** Ajustes e Estúdio abrem como tela própria (app à parte), com o mesmo estado da tela inicial. */
    @Test
    fun settingsAndStudioOpenAsTheirOwnScreen() {
        ActivityScenario.launch<ScreenActivity>(ScreenActivity.intent(app, ScreenActivity.SCREEN_SETTINGS)).use { s ->
            compose.onNodeWithText("Personalizar").assertExists()
            s.onActivity { a -> assertSame("mesmo estado do app inteiro", app.launcherViewModel, a.launcherViewModel) }
        }
        ActivityScenario.launch<ScreenActivity>(ScreenActivity.intent(app, ScreenActivity.SCREEN_STUDIO)).use {
            compose.onNodeWithText("Estúdio HTML").assertExists()
        }
    }

    /** A lista de apps em HTML sempre recebe os apps (mesmo sem citar launcher.appsLista no código). */
    @Test
    fun appListAlwaysGetsTheApps() {
        val defaults = HtmlVariables.defaultsFor(HtmlRole.APPS)
        assertTrue(defaults.containsAll(listOf("appsLista", "menuColunas", "menuIcone", "aberturas", "modoBusca")))
        assertTrue("appsDock" in HtmlVariables.defaultsFor(HtmlRole.DOCK))
        assertTrue(HtmlVariables.defaultsFor(HtmlRole.DESKTOP).isEmpty())
    }
}
