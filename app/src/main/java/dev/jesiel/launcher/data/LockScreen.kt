package dev.jesiel.launcher.data

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estilo da tela de bloqueio do Launcher. */
enum class LockStyle(val label: String) { THEME("Do tema"), HTML("Widget HTML") }

data class LockSettings(val enabled: Boolean = false, val style: LockStyle = LockStyle.THEME, val htmlWidgetId: String? = null)

/**
 * Tela de bloqueio do Launcher. O Android não deixa um app substituir o bloqueio do sistema
 * (PIN, digital): esta tela fica por cima dele. Ao apagar a tela, o Launcher abre a
 * [dev.jesiel.launcher.LockActivity]; ao deslizar, o sistema pede o desbloqueio de verdade.
 */
object LockScreen {
    private const val PREFS = "launcher_flags"
    private val _settings = MutableStateFlow<LockSettings?>(null)
    private var receiver: BroadcastReceiver? = null

    fun settings(context: Context): StateFlow<LockSettings?> {
        if (_settings.value == null) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _settings.value = LockSettings(
                enabled = p.getBoolean("lockEnabled", false),
                style = runCatching { LockStyle.valueOf(p.getString("lockStyle", null) ?: "") }.getOrDefault(LockStyle.THEME),
                htmlWidgetId = p.getString("lockHtml", null),
            )
        }
        return _settings
    }

    fun update(context: Context, transform: (LockSettings) -> LockSettings) {
        val next = transform(settings(context).value ?: LockSettings())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("lockEnabled", next.enabled)
            .putString("lockStyle", next.style.name)
            .putString("lockHtml", next.htmlWidgetId)
            .apply()
        _settings.value = next
        sync(context.applicationContext)
    }

    /** Abrir telas ao apagar a tela exige "sobrepor a outros apps" (início em segundo plano). */
    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    fun overlayPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Liga ou desliga o receptor de "tela apagou" conforme a configuração. Chamado ao iniciar o app. */
    fun sync(context: Context) {
        val app = context.applicationContext
        val enabled = settings(app).value?.enabled == true
        if (enabled && receiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_SCREEN_OFF) show(c)
                }
            }
            ContextCompat.registerReceiver(app, r, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = r
        } else if (!enabled && receiver != null) {
            runCatching { app.unregisterReceiver(receiver) }
            receiver = null
        }
    }

    /** Abre a tela de bloqueio, a menos que haja uma ligação em andamento. */
    fun show(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio?.mode == AudioManager.MODE_IN_CALL || audio?.mode == AudioManager.MODE_IN_COMMUNICATION) return
        val intent = Intent(context, dev.jesiel.launcher.LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        runCatching { context.startActivity(intent) }
    }

    fun isLocked(context: Context) = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** Links que só fazem sentido depois de desbloquear (abrir apps, páginas, ajustes). */
    fun needsUnlock(url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "launcher") return true
        return when (uri.host) {
            "musica", "music" -> uri.lastPathSegment == null // abrir o app que toca
            "desbloquear", "unlock" -> false
            else -> true
        }
    }
}
