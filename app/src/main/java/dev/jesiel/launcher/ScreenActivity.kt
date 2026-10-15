package dev.jesiel.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesiel.launcher.ui.settings.SettingsScreen

/**
 * Ajustes e Estúdio HTML abertos como um app à parte: tarefa própria (aparece nos recentes),
 * o botão Home volta para a tela inicial e dá para alternar entre os dois como entre apps.
 */
class ScreenActivity : LauncherHostActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val studio = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_STUDIO
        if (studio) controller.htmlLibraryOpen = true

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Ajustes chama "fechar" antes de abrir outra coisa (Estúdio, editor, seletor de
            // widgets…): só sai se nada foi aberto em seguida.
            var closeRequests by remember { mutableIntStateOf(0) }
            LaunchedEffect(closeRequests) {
                if (closeRequests == 0) return@LaunchedEffect
                val busy = controller.htmlLibraryOpen || controller.htmlEditing != null || controller.widgetPickerOpen || controller.htmlTesting != null
                if (!busy) finish()
            }
            Box(Modifier.fillMaxSize()) {
                if (!studio) SettingsScreen(state, controller, onClose = { closeRequests++ })

                // Estúdio por cima dos Ajustes (ou sozinho, quando aberto direto).
                BackHandler(enabled = controller.htmlLibraryOpen && controller.htmlEditing == null && controller.htmlTesting == null) {
                    controller.htmlLibraryOpen = false
                }
                LaunchedEffect(controller.htmlLibraryOpen) { if (studio && !controller.htmlLibraryOpen) finish() }
                AnimatedVisibility(
                    visible = controller.htmlLibraryOpen,
                    enter = if (studio) fadeIn(tween(1)) else slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
                    exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200)),
                ) {
                    val palette = palette(state)
                    MaterialTheme(colorScheme = palette.colorScheme) { dev.jesiel.launcher.ui.common.HtmlStudio(controller, palette) }
                }
                EditorOverlays(state)
            }
        }
    }

    companion object {
        private const val EXTRA_SCREEN = "tela"
        const val SCREEN_SETTINGS = "ajustes"
        const val SCREEN_STUDIO = "estudio"

        fun intent(context: Context, screen: String): Intent =
            Intent(context, ScreenActivity::class.java)
                .putExtra(EXTRA_SCREEN, screen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
