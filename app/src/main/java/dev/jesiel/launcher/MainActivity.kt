package dev.jesiel.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        widgets = WidgetHost(this)
        controller = LauncherController(this, viewModel, widgets)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val theme = ThemeRegistry.byId(state.themeId)

            // Sendo a tela inicial, o botão Voltar nunca fecha o launcher.
            BackHandler { }
            theme.Content(state, controller)

            if (controller.settingsOpen) {
                BackHandler { controller.settingsOpen = false }
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
