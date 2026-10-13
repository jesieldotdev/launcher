package dev.jesiel.launcher.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/** Um backup salvo no aparelho. */
data class BackupEntry(val file: File, val createdAt: Long, val reason: String)

/**
 * Backups da organização do launcher: automáticos (antes de mudanças grandes e uma vez por dia,
 * guardados no próprio app) e manuais (exportados para um arquivo escolhido pelo usuário).
 */
class Backups(private val context: Context, private val prefs: LauncherPrefs) {

    private val dir = File(context.filesDir, "backups").apply { mkdirs() }

    fun list(): List<BackupEntry> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { file ->
            runCatching {
                val json = JSONObject(file.readText())
                BackupEntry(file, json.optLong("createdAt", file.lastModified()), json.optString("reason", "Backup"))
            }.getOrNull()
        }
        .sortedByDescending { it.createdAt }

    /** Cria um backup automático e mantém só os [KEEP] mais recentes. */
    fun snapshot(reason: String): BackupEntry? = runCatching {
        val json = prefs.exportJson().put("reason", reason)
        val createdAt = json.getLong("createdAt")
        val file = File(dir, "backup-$createdAt.json")
        file.writeText(json.toString())
        list().drop(KEEP).forEach { it.file.delete() }
        BackupEntry(file, createdAt, reason)
    }.getOrNull()

    /** Backup diário: só se o último tiver mais de 24 horas. */
    fun dailySnapshot() {
        val last = list().firstOrNull()?.createdAt ?: 0L
        if (System.currentTimeMillis() - last > 24 * 3_600_000L) snapshot("Backup diário")
    }

    fun restore(entry: BackupEntry) = restore(JSONObject(entry.file.readText()))

    fun restore(json: JSONObject) {
        require(json.optString("app") == "dev.jesiel.launcher" && json.has("prefs")) { "Arquivo não é um backup do Launcher" }
        // Guarda o estado atual antes, para poder desfazer a restauração.
        snapshot("Antes de restaurar")
        prefs.importJson(json)
    }

    fun exportTo(uri: Uri) {
        val json = prefs.exportJson().put("reason", "Exportado")
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toString(2).toByteArray()) }
    }

    fun importFrom(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        restore(JSONObject(text))
    }

    private companion object {
        const val KEEP = 15
    }
}
