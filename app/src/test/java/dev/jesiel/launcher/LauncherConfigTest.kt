package dev.jesiel.launcher

import android.app.Application
import android.os.Looper
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.LauncherConfig
import dev.jesiel.launcher.data.LauncherPrefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LauncherConfigTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val themes = listOf("windows", "ubuntu", "kde", "macos", "ios", "retro")
    private fun app(label: String, pkg: String) = AppInfo(
        key = "$pkg/.Main#0", label = label, component = android.content.ComponentName(pkg, "$pkg.Main"),
        user = android.os.Process.myUserHandle(),
        icon = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap(),
    )
    private val apps = listOf(app("WhatsApp", "com.whatsapp"), app("Chrome", "com.android.chrome"), app("Spotify", "com.spotify.music"))

    @Test
    fun exportThenApplyChangesNothing() {
        val d = LauncherPrefs(ctx).data.value.copy(dock = listOf(apps[0].key), homeOrder = listOf(apps[1].key, apps[2].key))
        val (back, warnings) = LauncherConfig.apply(d, LauncherConfig.export(d, apps), apps, themes)
        assertTrue(warnings.toString(), warnings.isEmpty())
        assertEquals(d.copy(layouts = back.layouts), back)
        assertEquals(d.layoutFor(d.themeId), back.layoutFor(d.themeId))
    }

    @Test
    fun partialJsonFromAnAi() {
        val d = LauncherPrefs(ctx).data.value
        val json = JSONObject(
            """
            {"tema":"ios","modoCor":"dark","layouts":{"ios":{"columns":5,"searchPill":false,"iconSize":500,"inventada":1}},
             "dock":["com.whatsapp","com.nao.instalado","com.spotify.music"],"xyz":1}
            """.trimIndent(),
        )
        val (out, warnings) = LauncherConfig.apply(d, json, apps, themes)
        assertEquals("ios", out.themeId)
        assertEquals(dev.jesiel.launcher.data.ColorMode.DARK, out.colorMode)
        val ios = out.layoutFor("ios")
        assertEquals(5, ios.gridColumns)
        assertFalse(ios.showSearchPill)
        assertEquals("ajustado ao limite", 96, ios.iconSizeDp)
        assertEquals(listOf(apps[0].key, apps[2].key), out.dock)
        // O que não veio no JSON fica como estava.
        assertEquals(d.transparency, out.transparency)
        assertEquals(d.layoutFor("windows"), out.layoutFor("windows"))
        val w = warnings.joinToString()
        assertTrue(w, "com.nao.instalado" in w && "xyz" in w && "inventada" in w && "iconSize" in w)
    }

    /** No app: aceita a resposta da IA com o bloco ```json e faz backup antes. */
    @Test
    fun viewModelAcceptsAnAiAnswer() {
        val vm = LauncherViewModel(ctx)
        shadowOf(Looper.getMainLooper()).idle()
        val answer = "Pronto! Aqui está:\n```json\n{\"layouts\":{\"${vm.state.value.themeId}\":{\"columns\":6}}}\n```\nAproveite."
        val result = vm.applyConfig(answer)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isSuccess)
        assertEquals(6, vm.state.value.layout.gridColumns)
        assertTrue(vm.backups.list().any { it.reason.contains("JSON") })
        assertTrue(vm.configGuide().contains("\"columns\""))
        assertTrue(vm.applyConfig("sem json aqui").isFailure)
    }
}
