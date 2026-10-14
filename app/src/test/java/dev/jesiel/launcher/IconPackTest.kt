package dev.jesiel.launcher

import dev.jesiel.launcher.data.IconPackLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserFactory

@RunWith(RobolectricTestRunner::class)
class IconPackTest {

    private fun parse(xml: String) = IconPackLoader.parse(
        XmlPullParserFactory.newInstance().newPullParser().apply { setInput(xml.reader()) },
    )

    @Test
    fun readsAppFilterItemsAndStyle() {
        val f = parse(
            """
            <resources>
              <iconback img1="back_a" img2="back_b"/>
              <iconmask img1="mask"/>
              <iconupon img1="upon"/>
              <scale factor="0.75"/>
              <item component="ComponentInfo{com.whatsapp/com.whatsapp.Main}" drawable="whatsapp"/>
              <item component="ComponentInfo{com.android.chrome/.Main}" drawable="chrome"/>
              <item component="ComponentInfo{com.whatsapp/com.whatsapp.Other}" drawable="whatsapp_alt"/>
              <item component=":LAUNCHER_ACTION_APP_DRAWER" drawable="drawer"/>
              <item component="ComponentInfo{com.semdesenho/.A}" drawable=""/>
            </resources>
            """.trimIndent(),
        )
        assertEquals("whatsapp", f.byComponent["com.whatsapp/com.whatsapp.Main"])
        assertEquals("whatsapp_alt", f.byComponent["com.whatsapp/com.whatsapp.Other"])
        // Atividade relativa (".Main") é expandida com o pacote.
        assertEquals("chrome", f.byComponent["com.android.chrome/com.android.chrome.Main"])
        // Por pacote vale a primeira entrada.
        assertEquals("whatsapp", f.byPackage["com.whatsapp"])
        assertNull(f.byPackage["com.semdesenho"])
        assertEquals(3, f.byComponent.size)
        assertEquals(listOf("back_a", "back_b"), f.backs)
        assertEquals("mask", f.mask)
        assertEquals("upon", f.upon)
        assertEquals(0.75f, f.scale)
    }
}
