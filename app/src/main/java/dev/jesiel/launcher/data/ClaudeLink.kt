package dev.jesiel.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

/**
 * Canal do ntfy.sh por onde os hooks do Claude Code avisam fim de build, de execução e quando
 * ele espera uma resposta. Os widgets HTML leem o canal pela variável `ntfy`.
 */
object ClaudeLink {
    private const val PREFS = "launcher_flags"
    private const val KEY = "ntfyTopic"
    private val topic = MutableStateFlow<String?>(null)

    fun topic(context: Context): StateFlow<String?> {
        if (topic.value == null) {
            topic.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        }
        @Suppress("UNCHECKED_CAST")
        return topic as StateFlow<String?>
    }

    fun setTopic(context: Context, value: String) {
        val clean = sanitize(value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, clean).apply()
        topic.value = clean
    }

    /** Só letras, números, - e _ (o que o ntfy aceita como nome de canal). */
    fun sanitize(value: String) = value.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)

    /** Manda uma mensagem de teste para o canal (em segundo plano). */
    fun sendTest(topic: String, done: (Boolean) -> Unit) {
        Thread {
            val ok = runCatching {
                val c = java.net.URL("https://ntfy.sh/" + topic).openConnection() as java.net.HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("Title", "execução concluída")
                c.setRequestProperty("Tags", "checkered_flag,stop,launcher")
                c.outputStream.use { it.write("Teste do Launcher: o widget está conectado".toByteArray()) }
                val code = c.responseCode
                c.disconnect()
                code == 200
            }.getOrDefault(false)
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    /** Nome difícil de adivinhar: quem souber o canal lê as mensagens. */
    fun randomTopic(): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        return "claude-" + (1..18).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }
}
