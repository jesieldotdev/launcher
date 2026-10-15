package dev.jesiel.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.jesiel.launcher.ui.common.LauncherPalette
import dev.jesiel.launcher.ui.common.WidgetPickerDialog
import dev.jesiel.launcher.ui.common.isDark
import dev.jesiel.launcher.widgets.WidgetHost

/**
 * Base das telas do launcher (tela inicial e Ajustes/Estúdio): estado compartilhado, controlador,
 * seletores de arquivo/imagem, backup e widgets do sistema, e as janelas do editor HTML.
 */
abstract class LauncherHostActivity : ComponentActivity() {

    protected val viewModel: LauncherViewModel get() = launcherViewModel
    protected lateinit var widgets: WidgetHost
    protected lateinit var controller: LauncherController
    protected var importedHtml by mutableStateOf<String?>(null)

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
    }

    /** Paleta do tema em uso (a tela inicial a define; as outras telas usam a última). */
    @Composable
    protected fun palette(state: LauncherUiState): LauncherPalette =
        controller.palette ?: dev.jesiel.launcher.ui.windows.windowsPalette(isDark(state.colorMode))

    /** Editor, testador e seletor de widgets: abrem por cima de qualquer tela do launcher. */
    @Composable
    protected fun EditorOverlays(state: LauncherUiState) {
        BackHandler(enabled = controller.htmlEditing != null) { controller.htmlEditing = null }
        AnimatedVisibility(
            visible = controller.htmlEditing != null,
            enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(160)),
        ) {
            val editing = remember { controller.htmlEditing }
            val palette = palette(state)
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

        BackHandler(enabled = controller.htmlTesting != null) { controller.htmlTesting = null }
        AnimatedVisibility(visible = controller.htmlTesting != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            val testing = remember { controller.htmlTesting }
            testing?.let { dev.jesiel.launcher.ui.common.HtmlTester(it, controller.htmlTestingRole, controller, palette(state)) }
        }

        if (controller.widgetPickerOpen) {
            WidgetPickerDialog(widgets = widgets, onPick = controller::addWidget, onDismiss = { controller.widgetPickerOpen = false })
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

    // Os fluxos de widget do AppWidgetHost ainda usam startActivityForResult.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        controller.onActivityResult(requestCode, resultCode)
    }
}
