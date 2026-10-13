package dev.jesiel.launcher

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

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var widgets: WidgetHost
    private lateinit var controller: LauncherController
    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { viewModel.backups.exportTo(uri) }
            .onSuccess { controller.toast("Backup exportado") }
            .onFailure { controller.toast("Não foi possível exportar") }
    }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { viewModel.backups.importFrom(uri) }
            .onSuccess { controller.afterRestore() }
            .onFailure { controller.toast(it.message ?: "Arquivo inválido") }
    }
    private var importedHtml by mutableStateOf<String?>(null)
    private val importHtml = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }.onSuccess { importedHtml = it }.onFailure { controller.toast("Não foi possível ler o arquivo") }
    }
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setCustomMenuIcon(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        widgets = WidgetHost(this)
        controller = LauncherController(this, viewModel, widgets)
        controller.pickImage = { pickImage.launch(arrayOf("image/*")) }
        controller.exportBackupFile = {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            exportBackup.launch("launcher-backup-$date.json")
        }
        controller.importHtmlFile = { importHtml.launch(arrayOf("text/html", "text/plain", "*/*")) }
        controller.importBackupFile = { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }

        setContent {
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

            if (controller.htmlLibraryOpen) {
                val palette = controller.palette ?: dev.jesiel.launcher.ui.windows.windowsPalette(isDark(state.colorMode))
                MaterialTheme(colorScheme = palette.colorScheme) { dev.jesiel.launcher.ui.common.HtmlWidgetLibrary(controller, palette) }
            }

            // Editor de widgets HTML.
            BackHandler(enabled = controller.htmlEditing != null) { controller.htmlEditing = null }
            AnimatedVisibility(
                visible = controller.htmlEditing != null,
                enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(160)),
            ) {
                val editing = remember { controller.htmlEditing }
                val palette = controller.palette ?: dev.jesiel.launcher.ui.windows.windowsPalette(isDark(state.colorMode))
                MaterialTheme(colorScheme = palette.colorScheme) {
                    editing?.let {
                        dev.jesiel.launcher.ui.common.HtmlWidgetEditor(
                            initial = it,
                            controller = controller,
                            palette = palette,
                            importedHtml = importedHtml,
                            onImportedConsumed = { importedHtml = null },
                        )
                    }
                }
            }

            BackHandler(enabled = controller.settingsOpen) { controller.settingsOpen = false }
            AnimatedVisibility(
                visible = controller.settingsOpen,
                enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
                exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200)),
            ) {
                SettingsScreen(state, controller, onClose = { controller.settingsOpen = false })
            }
            if (controller.widgetPickerOpen) {
                WidgetPickerDialog(
                    widgets = widgets,
                    onPick = controller::addWidget,
                    onDismiss = { controller.widgetPickerOpen = false },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgets.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            controller.onHomePressed()
        }
    }

    // Os fluxos de widget do AppWidgetHost ainda usam startActivityForResult.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        controller.onActivityResult(requestCode, resultCode)
    }
}
