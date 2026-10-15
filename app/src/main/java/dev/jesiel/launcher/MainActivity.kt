package dev.jesiel.launcher

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dev.jesiel.launcher.ui.common.HomeDragGhost
import dev.jesiel.launcher.ui.common.trackHomeDrag
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jesiel.launcher.ui.common.ArticleViewer
import dev.jesiel.launcher.ui.common.isDark
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesiel.launcher.theme.ThemeRegistry
import dev.jesiel.launcher.ui.common.WidgetPickerDialog
import dev.jesiel.launcher.ui.settings.SettingsScreen
import dev.jesiel.launcher.widgets.WidgetHost

class MainActivity : LauncherHostActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // A raiz acompanha o dedo quando um app é arrastado da lista para a tela inicial.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().trackHomeDrag(controller)) {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val theme = ThemeRegistry.byId(state.themeId)

            // Sendo a tela inicial, o botão Voltar nunca fecha o launcher.
            BackHandler { }
            // Troca de tema com transição suave.
            // O tema que está saindo é desenhado com o último estado dele (e não grava nada),
            // para não misturar as posições dos dois temas durante a transição.
            val lastStates = remember { mutableMapOf<String, LauncherUiState>() }
            lastStates[theme.id] = state
            Crossfade(targetState = theme, animationSpec = tween(450), label = "tema") { t ->
                t.Content(if (t.id == theme.id) state else lastStates[t.id] ?: state, controller)
            }

            // Leitor de notícias do feed, por cima do tema.
            AnimatedVisibility(
                visible = controller.articleUrl != null,
                enter = slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180)),
            ) {
                val url = remember { controller.articleUrl }
                MaterialTheme(colorScheme = if (isDark(state.colorMode)) darkColorScheme() else lightColorScheme()) {
                    url?.let { ArticleViewer(it, onClose = { controller.articleUrl = null }, onOpenExternal = controller::openUrl) }
                }
            }

            // Lista de apps em HTML, no lugar da gaveta do tema. Fica carregada na memória
            // (escondida e pausada quando fechada) para abrir na hora, sem recarregar a página.
            BackHandler(enabled = controller.htmlDrawerOpen) { controller.htmlDrawerOpen = false }
            state.appsHtml?.let { widget ->
                val open = controller.htmlDrawerOpen
                val shown by androidx.compose.animation.core.animateFloatAsState(if (open) 1f else 0f, tween(if (open) 220 else 160), label = "lista html")
                var opens by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                LaunchedEffect(open) { if (open) opens++ }
                val palette = controller.palette ?: dev.jesiel.launcher.ui.windows.windowsPalette(isDark(state.colorMode))
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        // Fechada: fora da tela (o mesmo tamanho, para não recarregar), sem pegar os toques do tema.
                        .then(if (shown <= 0.01f) Modifier.offset { androidx.compose.ui.unit.IntOffset(0, 100_000) } else Modifier)
                        .graphicsLayer {
                            alpha = shown
                            translationY = (1f - shown) * 60.dp.toPx()
                        },
                ) {
                    dev.jesiel.launcher.ui.common.HtmlAppList(widget, controller, palette, controller.htmlDrawerSearch, visible = shown > 0.01f, opens = opens)
                }
            }

            // Ajustes e Estúdio HTML abrem como um app à parte (tarefa própria, nos recentes).
            LaunchedEffect(controller.settingsOpen, controller.htmlLibraryOpen) {
                val screen = when {
                    controller.htmlLibraryOpen -> ScreenActivity.SCREEN_STUDIO
                    controller.settingsOpen -> ScreenActivity.SCREEN_SETTINGS
                    else -> return@LaunchedEffect
                }
                controller.settingsOpen = false
                controller.htmlLibraryOpen = false
                startActivity(ScreenActivity.intent(this@MainActivity, screen))
            }

            EditorOverlays(state)

            // Menu de um app pedido por widget HTML (launcher://menu/<pacote>), ex.: toque longo na lista em HTML.
            controller.htmlAppMenu?.let { app ->
                val palette = controller.palette ?: dev.jesiel.launcher.ui.windows.windowsPalette(isDark(state.colorMode))
                MaterialTheme(colorScheme = palette.colorScheme) { dev.jesiel.launcher.ui.common.HtmlAppMenu(app, state, controller) }
            }
            // O app que vem da lista de apps, seguindo o dedo, por cima de tudo.
            HomeDragGhost(controller)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Vindo dos Ajustes ("Organizar área de trabalho"): abre já no modo de edição.
        if (intent.getBooleanExtra(EXTRA_EDIT, false)) {
            controller.editMode = true
            return
        }
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            controller.onHomePressed()
        }
    }

    companion object {
        const val EXTRA_EDIT = "editar"
    }
}
