package dev.jesiel.launcher

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.test.core.app.ApplicationProvider
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.HomeImporter
import dev.jesiel.launcher.data.ImportedItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Simula a tabela "favorites" do launcher da Xiaomi. */
class FakeMiuiProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor =
        MatrixCursor(arrayOf("_id", "title", "intent", "container", "screen", "cellX", "cellY", "itemType")).apply {
            addRow(arrayOf(1, "Chrome", intent("com.android.chrome/.Main"), -100, 10, 0, 0, 0))
            addRow(arrayOf(2, "Social", null, -100, 10, 1, 0, 2))
            addRow(arrayOf(3, "WhatsApp", intent("com.whatsapp/.Main"), 2, 0, 0, 0, 0))
            addRow(arrayOf(4, "Spotify", intent("com.spotify.music/.Main"), -100, 11, 2, 1, 0))
            addRow(arrayOf(5, "Telefone", intent("com.android.dialer/.Main"), -101, 0, 0, 0, 0))
        }

    private fun intent(component: String) =
        "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=$component;end"

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeImporterTest {

    private fun app(component: String) = ComponentName.unflattenFromString(component)!!.let {
        AppInfo(component, it.packageName, it, Process.myUserHandle(), ImageBitmap(1, 1))
    }

    @Test
    fun importsDesktopFoldersAndDockFromSystemLauncher() {
        Robolectric.buildContentProvider(FakeMiuiProvider::class.java).create("com.miui.home.launcher.settings")
        val apps = listOf("com.android.chrome/.Main", "com.whatsapp/.Main", "com.spotify.music/.Main", "com.android.dialer/.Main").map(::app)
        val result = HomeImporter(ApplicationProvider.getApplicationContext()).importFromSystemLauncher(apps)!!

        assertEquals(3, result.home.size)
        assertEquals("com.android.chrome/.Main", (result.home[0] as ImportedItem.App).key)
        val folder = result.home[1] as ImportedItem.Folder
        assertEquals("Social", folder.name)
        assertEquals(listOf("com.whatsapp/.Main"), folder.keys)
        val spotify = result.home[2] as ImportedItem.App
        assertEquals(1, spotify.page)
        assertEquals(listOf("com.android.dialer/.Main"), result.dock)
    }
}
