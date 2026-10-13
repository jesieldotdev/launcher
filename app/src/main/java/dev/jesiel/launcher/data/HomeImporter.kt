package dev.jesiel.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process

/** Organização lida de outro launcher, já convertida para os apps instalados. */
data class ImportedHome(
    val source: String,
    /** Itens da área de trabalho na ordem de leitura: chave do app ou id de pasta importada. */
    val home: List<ImportedItem>,
    val dock: List<String>,
)

sealed interface ImportedItem {
    val page: Int
    val x: Int
    val y: Int

    data class App(val key: String, override val page: Int, override val x: Int, override val y: Int) : ImportedItem
    data class Folder(val name: String, val keys: List<String>, override val page: Int, override val x: Int, override val y: Int) : ImportedItem
}

/**
 * Importa a tela inicial do launcher do sistema. Launchers baseados no Launcher3 (e o da
 * Xiaomi) expõem a tabela "favorites" por um provedor de conteúdo; alguns fabricantes bloqueiam
 * esse acesso, e então resta importar os apps mais usados.
 */
class HomeImporter(private val context: Context) {

    private val sources = listOf(
        "com.miui.home.launcher.settings" to "Launcher do sistema (Xiaomi)",
        "com.android.launcher3.settings" to "Launcher3",
        "com.google.android.apps.nexuslauncher.settings" to "Pixel Launcher",
        "com.android.launcher2.settings" to "Launcher",
    )

    fun importFromSystemLauncher(apps: List<AppInfo>): ImportedHome? {
        for ((authority, name) in sources) {
            val result = runCatching { read(authority, name, apps) }.getOrNull()
            if (result != null && (result.home.isNotEmpty() || result.dock.isNotEmpty())) return result
        }
        return null
    }

    private fun read(authority: String, name: String, apps: List<AppInfo>): ImportedHome? {
        val uri = Uri.parse("content://$authority/favorites")
        val rows = mutableListOf<Row>()
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            fun col(n: String) = c.getColumnIndex(n)
            val id = col("_id"); val title = col("title"); val intent = col("intent"); val container = col("container")
            val screen = col("screen"); val cellX = col("cellX"); val cellY = col("cellY"); val type = col("itemType")
            while (c.moveToNext()) {
                rows += Row(
                    id = c.getLong(id),
                    title = if (title >= 0) c.getString(title) else null,
                    intent = if (intent >= 0) c.getString(intent) else null,
                    container = c.getLong(container),
                    screen = if (screen >= 0) c.getLong(screen) else 0,
                    x = if (cellX >= 0) c.getInt(cellX) else 0,
                    y = if (cellY >= 0) c.getInt(cellY) else 0,
                    type = c.getInt(type),
                )
            }
        } ?: return null

        val byComponent = apps.associateBy { it.component.flattenToShortString() }
        val byPackage = apps.groupBy { it.packageName }
        fun keyOf(row: Row): String? {
            val component = row.intent?.let { runCatching { Intent.parseUri(it, 0) }.getOrNull() }?.let { it.component ?: it.`package`?.let { p -> android.content.ComponentName(p, "") } }
                ?: return null
            return byComponent[component.flattenToShortString()]?.key ?: byPackage[component.packageName]?.firstOrNull()?.key
        }

        val desktopScreens = rows.filter { it.container == DESKTOP }.map { it.screen }.distinct().sorted()
        val home = rows.filter { it.container == DESKTOP && (it.type in APP_TYPES || it.type == TYPE_FOLDER) }
            .sortedWith(compareBy({ desktopScreens.indexOf(it.screen) }, { it.y }, { it.x }))
            .mapNotNull { row ->
                val page = desktopScreens.indexOf(row.screen).coerceAtLeast(0)
                if (row.type == TYPE_FOLDER) {
                    val keys = rows.filter { it.container == row.id && it.type in APP_TYPES }
                        .sortedWith(compareBy({ it.y }, { it.x }))
                        .mapNotNull(::keyOf)
                        .distinct()
                    if (keys.isEmpty()) null else ImportedItem.Folder(row.title?.ifBlank { null } ?: "Pasta", keys, page, row.x, row.y)
                } else {
                    keyOf(row)?.let { ImportedItem.App(it, page, row.x, row.y) }
                }
            }
        val dock = rows.filter { it.container == HOTSEAT && it.type in APP_TYPES }
            .sortedWith(compareBy({ it.screen }, { it.x }))
            .mapNotNull(::keyOf)
            .distinct()
        return ImportedHome(name, home.distinctBy { if (it is ImportedItem.App) it.key else "f${it.page}-${it.x}-${it.y}" }, dock)
    }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Apps mais usados nos últimos 30 dias, para montar favoritos e dock. */
    fun importMostUsed(apps: List<AppInfo>): ImportedHome? {
        if (!hasUsageAccess()) return null
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val stats = usage.queryAndAggregateUsageStats(now - 30L * 24 * 3_600_000, now)
        val own = context.packageName
        val ranked = apps.filter { it.packageName != own && (stats[it.packageName]?.totalTimeInForeground ?: 0) > 0 }
            .sortedByDescending { stats[it.packageName]?.totalTimeInForeground ?: 0 }
            .distinctBy { it.packageName }
        if (ranked.isEmpty()) return null
        val dock = ranked.take(4).map { it.key }
        val home = ranked.drop(4).take(16).mapIndexed { i, app -> ImportedItem.App(app.key, 0, i % 4, i / 4) }
        return ImportedHome("Apps mais usados", home, dock)
    }

    private data class Row(
        val id: Long,
        val title: String?,
        val intent: String?,
        val container: Long,
        val screen: Long,
        val x: Int,
        val y: Int,
        val type: Int,
    )

    private companion object {
        const val DESKTOP = -100L
        const val HOTSEAT = -101L
        const val TYPE_FOLDER = 2
        val APP_TYPES = setOf(0, 1, 6)
    }
}
