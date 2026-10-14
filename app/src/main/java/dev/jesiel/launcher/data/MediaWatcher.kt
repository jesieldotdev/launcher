package dev.jesiel.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

/** O que está tocando agora (de qualquer app que publique uma sessão de mídia). */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val appLabel: String,
    val packageName: String,
    val playing: Boolean,
    /** Posição no instante [positionAt] (relógio [SystemClock.elapsedRealtime]). */
    val positionMs: Long,
    val positionAt: Long,
    val speed: Float,
    val durationMs: Long,
    /** Capa reduzida como data URI (pronta para `<img src>` e `url()`), ou vazia. */
    val artDataUri: String,
) {
    /** Posição estimada agora, contando o tempo desde a última atualização do app. */
    fun positionNow(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing) positionMs + ((nowElapsed - positionAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Música atual e quantidade de notificações, lidas pelo [MediaListenerService]. O Android só
 * entrega as sessões de mídia a apps com "acesso a notificações", concedido pelo usuário.
 */
object MediaWatcher {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying
    private val _notifications = MutableStateFlow(0)
    val notifications: StateFlow<Int> = _notifications

    private var controller: MediaController? = null
    private var artKey: String? = null
    private var artUri = ""

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    /** Falso na versão leve, que sai sem o serviço (e sem a permissão que o Play Protect barra). */
    fun supported(context: Context): Boolean = runCatching {
        context.packageManager.getServiceInfo(ComponentName(context, MediaListenerService::class.java), 0)
    }.isSuccess

    fun hasAccess(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) == true

    fun accessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Tocar/pausar, próxima, anterior. Retorna falso se não há o que controlar. */
    fun command(action: String): Boolean {
        val c = controller?.transportControls ?: return false
        when (action) {
            "tocar", "play" -> c.play()
            "pausar", "pause" -> c.pause()
            "alternar", "toggle" -> if (_nowPlaying.value?.playing == true) c.pause() else c.play()
            "proxima", "next" -> c.skipToNext()
            "anterior", "prev", "previous" -> c.skipToPrevious()
            else -> return false
        }
        return true
    }

    /** Pacote do app que está tocando, para abri-lo ao tocar na capa. */
    val playingPackage: String? get() = controller?.packageName

    internal fun onSessions(context: Context, sessions: List<MediaController>?) {
        // Prefere a sessão que está tocando; se nenhuma, a primeira (a mais recente).
        val chosen = sessions?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions?.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        appLabel = chosen?.packageName?.let { pkg ->
            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }.orEmpty()
        publish()
    }

    private var appLabel = ""

    internal fun onNotifications(count: Int) {
        _notifications.value = count
    }

    internal fun clear() {
        controller?.unregisterCallback(callback)
        controller = null
        _nowPlaying.value = null
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val state = c?.playbackState
        if (c == null || meta == null) {
            _nowPlaying.value = null
            return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val key = "${c.packageName}|$title|$artist|$album"
        if (key != artKey) {
            artKey = key
            val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            artUri = art?.let(::toDataUri).orEmpty()
        }
        _nowPlaying.value = NowPlaying(
            title = title,
            artist = artist,
            album = album,
            appLabel = appLabel,
            packageName = c.packageName,
            playing = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
            positionMs = state?.position ?: 0L,
            positionAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            artDataUri = artUri,
        )
    }

    /** Capa reduzida a 192 px em JPEG: leve para passar ao widget a cada troca de faixa. */
    private fun toDataUri(bitmap: Bitmap): String = runCatching {
        val max = 192
        val scale = minOf(1f, max.toFloat() / maxOf(bitmap.width, bitmap.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 82, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrDefault("")

    /** mm:ss (ou h:mm:ss). */
    fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}

/**
 * Recebe as notificações do sistema só para saber o que está tocando e quantas notificações há.
 * Nada é guardado nem enviado para fora do aparelho.
 */
class MediaListenerService : NotificationListenerService() {
    private var manager: MediaSessionManager? = null
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { MediaWatcher.onSessions(this, it) }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        val component = ComponentName(this, MediaListenerService::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, component)
            MediaWatcher.onSessions(this, m.getActiveSessions(component))
        }
        countNotifications()
    }

    override fun onListenerDisconnected() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        MediaWatcher.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = countNotifications()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = countNotifications()

    /** Só as que o usuário pode dispensar (sem "tocando agora", downloads em andamento etc.). */
    private fun countNotifications() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        MediaWatcher.onNotifications(list.count { it.isClearable && !it.isOngoing && it.packageName != packageName })
    }
}
