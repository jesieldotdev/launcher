package dev.jesiel.launcher

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.graphics.asImageBitmap
import dev.jesiel.launcher.data.Placement
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GridRescaleTest {

    @Test
    fun itemsKeepTheirPlaceOnScreen() {
        val placements = mapOf(
            "app" to Placement(0, 2f, 4f, 1f, 1f),
            "widget" to Placement(0, 0f, 0f, 4f, 2f),
        )
        // Linhas de 100 dp para 50 dp: a linha 4 (400 dp) vira a linha 8 (os mesmos 400 dp).
        val half = rescalePlacements(placements, oldCell = 100, newCell = 50, oldColumns = 4, newColumns = 4, free = false)
        assertEquals(Placement(0, 2f, 8f, 1f, 1f), half["app"])
        // O widget mantém a altura na tela (2 linhas de 100 = 4 de 50); ícones mantêm 1×1.
        assertEquals(Placement(0, 0f, 0f, 4f, 4f), half["widget"])
        // 4 para 5 colunas: a coluna acompanha a proporção; o widget de largura total continua de largura total.
        val wider = rescalePlacements(placements, 100, 100, 4, 5, free = false)
        assertEquals(Placement(0, 3f, 4f, 1f, 1f), wider["app"])
        assertEquals(5f, wider.getValue("widget").w)
    }

    /** Arrastar o controle de altura para lá e para cá e voltar ao valor de antes devolve as mesmas posições. */
    @Test
    fun sliderBackAndForthDoesNotDrift() {
        val vm = LauncherViewModel(ApplicationProvider.getApplicationContext<Application>())
        shadowOf(Looper.getMainLooper()).idle()
        val theme = vm.state.value.themeId
        val original = mapOf("a" to Placement(0, 1f, 5f, 1f, 1f), "b" to Placement(0, 3f, 7f, 1f, 1f))
        vm.savePlacements(theme, original)
        listOf(130, 117, 90, 61, 75, 0).forEach { h -> vm.updateLayout { it.copy(cellHeightDp = h) } }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(original, vm.state.value.placements.filterKeys { it in original })
    }
}

@RunWith(RobolectricTestRunner::class)
class NewAppsTest {
    private fun app(label: String, pkg: String): dev.jesiel.launcher.data.AppInfo {
        val bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        return dev.jesiel.launcher.data.AppInfo(
            key = "$pkg/.Main#0", label = label, component = android.content.ComponentName(pkg, "$pkg.Main"),
            user = android.os.Process.myUserHandle(), icon = bitmap.asImageBitmap(),
        )
    }

    /** Um app que o launcher nunca viu vai para a tela 2; os que já existiam não mudam. */
    @Test
    fun newlyInstalledAppGoesToTheNextScreen() {
        val vm = LauncherViewModel(ApplicationProvider.getApplicationContext<Application>())
        shadowOf(Looper.getMainLooper()).idle()
        val old = app("Antigo", "com.antigo")
        vm.onAppsLoaded(listOf(old)) // primeira vez: só aprende
        val novo = app("Novo", "com.novo")
        vm.onAppsLoaded(listOf(old, novo))
        shadowOf(Looper.getMainLooper()).idle()
        val st = vm.state.value
        org.junit.Assert.assertTrue("o novo foi para a tela inicial", novo.key in st.favoriteKeys)
        org.junit.Assert.assertFalse("o antigo não", old.key in st.favoriteKeys)
        assertEquals(1, st.placements.getValue(novo.key).page)
        org.junit.Assert.assertTrue("várias telas ligadas", st.layout.multiPage && st.layout.pageCount >= 2)
        // Sumiu e voltou (perfil de trabalho pausado): não conta como novo de novo.
        vm.toggleFavorite(novo)
        vm.onAppsLoaded(listOf(old))
        vm.onAppsLoaded(listOf(old, novo))
        shadowOf(Looper.getMainLooper()).idle()
        org.junit.Assert.assertFalse(novo.key in vm.state.value.favoriteKeys)
    }
}
