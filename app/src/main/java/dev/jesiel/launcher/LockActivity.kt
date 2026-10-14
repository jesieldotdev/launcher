package dev.jesiel.launcher

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesiel.launcher.data.LockScreen
import dev.jesiel.launcher.data.LockSettings
import dev.jesiel.launcher.theme.ThemeRegistry
import dev.jesiel.launcher.ui.common.LockScreenContent
import dev.jesiel.launcher.ui.common.ThemeRoot
import dev.jesiel.launcher.ui.common.isDark
import dev.jesiel.launcher.ui.common.withTransparency
import dev.jesiel.launcher.widgets.WidgetHost

/**
 * Tela de bloqueio do Launcher, mostrada por cima do bloqueio do sistema. Deslizar pede ao
 * sistema o desbloqueio (PIN, digital…); desbloquear direto pela digital também a fecha.
 */
class LockActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var controller: LauncherController

    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        // O botão Voltar não tira a tela de bloqueio.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = Unit })
        ContextCompat.registerReceiver(this, unlocked, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)

        controller = LauncherController(this, viewModel, WidgetHost(this))
        controller.linkInterceptor = { url ->
            when {
                url.startsWith("launcher://desbloquear") || url.startsWith("launcher://unlock") -> { unlock(); true }
                LockScreen.needsUnlock(url) -> {
                    // Abre o que foi pedido depois que o sistema liberar.
                    unlock { controller.linkInterceptor = null; controller.handleWidgetLink(url) }
                    true
                }
                else -> false
            }
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val lock by LockScreen.settings(this).collectAsState()
            val dark = isDark(state.colorMode)
            val theme = ThemeRegistry.byId(state.themeId)
            val palette = remember(theme, dark, state.transparency) { theme.palette(dark).withTransparency(state.transparency) }
            ThemeRoot(controller, palette) {
                LockScreenContent(
                    settings = lock ?: LockSettings(),
                    state = state,
                    controller = controller,
                    palette = palette,
                    onUnlock = { unlock() },
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }

    /** Pede ao sistema para desbloquear; sem bloqueio seguro, só fecha. */
    private fun unlock(after: (() -> Unit)? = null) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            after?.invoke()
            finish()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                after?.invoke()
                finish()
            }
        })
    }
}
