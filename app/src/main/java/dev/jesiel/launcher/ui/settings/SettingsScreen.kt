package dev.jesiel.launcher.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.AppsView
import dev.jesiel.launcher.data.ColorMode
import dev.jesiel.launcher.data.LeftScreen
import dev.jesiel.launcher.data.MenuIcon
import dev.jesiel.launcher.data.SwipeAction
import dev.jesiel.launcher.theme.LauncherThemeSpec
import dev.jesiel.launcher.theme.ThemeRegistry
import dev.jesiel.launcher.ui.common.isDark
import kotlin.math.roundToInt

private val SettingsLight = lightColorScheme(
    primary = Color(0xFF0067C0),
    surface = Color(0xFFF6F8FB),
    background = Color(0xFFF6F8FB),
    surfaceContainerHighest = Color.White,
)

private val SettingsDark = darkColorScheme(
    primary = Color(0xFF4CC2FF),
    onPrimary = Color(0xFF00213A),
    surface = Color(0xFF16171A),
    background = Color(0xFF16171A),
    surfaceContainerHighest = Color(0xFF24262B),
)

@Composable
fun SettingsScreen(state: LauncherUiState, controller: LauncherController, onClose: () -> Unit) {
    val vm = controller.viewModel
    val layout = state.layout
    var isDefault by remember { mutableStateOf(controller.isDefaultLauncher()) }
    LifecycleResumeEffect(Unit) {
        isDefault = controller.isDefaultLauncher()
        onPauseOrDispose { }
    }

    MaterialTheme(colorScheme = if (isDark(state.colorMode)) SettingsDark else SettingsLight) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                    Text("Personalizar", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }

                if (!isDefault) {
                    Card {
                        Text("Usar como launcher padrão", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Defina este app como tela inicial para que o botão Home abra o launcher.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = controller::requestDefaultLauncher) { Text("Definir como padrão") }
                    }
                }

                SectionTitle("Tema")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ThemeRegistry.all.forEach { theme ->
                        ThemePreview(theme, selected = theme.id == state.themeId) { vm.setTheme(theme.id) }
                    }
                }
                Text(
                    "Favoritos, pastas, dock, widgets e modo de cor são mantidos ao trocar de tema. Grade, posições e telas valem só para o tema atual.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                SectionTitle("Aparência")
                Card {
                    Text("Modo de cor", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Choices(ColorMode.entries, state.colorMode, { it.label }) { vm.setColorMode(it) }
                    Spacer(Modifier.height(16.dp))
                    SwitchRow("Efeitos de transparência", state.transparency) { vm.setTransparency(it) }
                    Text(
                        "Menus e painéis translúcidos com o fundo desfocado (desfoque a partir do Android 12).",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                    Spacer(Modifier.height(12.dp))
                    SwitchRow("Papel de parede do tema", layout.useThemeWallpaper) { on ->
                        vm.updateLayout { it.copy(useThemeWallpaper = on) }
                    }
                    Text(
                        if (layout.useThemeWallpaper) "Desative para ver o papel de parede do sistema."
                        else "Mostrando o papel de parede do sistema.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                    val live = remember { controller.liveWallpaperName() }
                    if (live != null) {
                        Text(
                            "Papel de parede animado: $live. " + if (layout.useThemeWallpaper) "Desative o papel de parede do tema para vê-lo e tocar nele."
                            else "Toques em áreas vazias da tela são repassados a ele.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = controller::openWallpaperPicker) { Text("Escolher papel de parede do sistema") }

                    Spacer(Modifier.height(16.dp))
                    Text("Ícone do menu de apps", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Choices(MenuIcon.entries, layout.menuIcon, { it.label }) { icon ->
                        if (icon == MenuIcon.CUSTOM) controller.pickImage?.invoke()
                        else vm.updateLayout { it.copy(menuIcon = icon) }
                    }

                    // Os outros temas já abrem a gaveta com a lista completa.
                    if (state.themeId == "windows") {
                        Spacer(Modifier.height(16.dp))
                        Text("Iniciar abre em", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Choices(listOf(false, true), layout.menuOpensAllApps, { if (it) "Todos os apps (como no KDE)" else "Fixados" }) { all ->
                            vm.updateLayout { it.copy(menuOpensAllApps = all) }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SliderRow("Ícones na lista de apps", "${layout.drawerIconSizeDp} dp", layout.drawerIconSizeDp.toFloat(), 36f..72f, 8) { v ->
                        vm.updateLayout { it.copy(drawerIconSizeDp = v.roundToInt()) }
                    }
                    SliderRow("Colunas na lista de apps", "${layout.drawerColumns}", layout.drawerColumns.toFloat(), 3f..6f, 2) { v ->
                        vm.updateLayout { it.copy(drawerColumns = v.roundToInt()) }
                    }
                    SwitchRow("Mostrar título, exibição e filtros na lista de apps", layout.showAppsHeader) { on ->
                        vm.updateLayout { it.copy(showAppsHeader = on) }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Exibição de \"Todos os apps\"", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Choices(AppsView.entries, state.appsView, { it.label }) { vm.setAppsView(it) }

                    Spacer(Modifier.height(16.dp))
                    SliderRow("Ícones da área de trabalho", "${layout.iconSizeDp} dp", layout.iconSizeDp.toFloat(), 40f..72f, 7) { v ->
                        vm.updateLayout { it.copy(iconSizeDp = v.roundToInt()) }
                    }
                    SliderRow("Colunas da área de trabalho", "${layout.gridColumns}", layout.gridColumns.toFloat(), 3f..6f, 2) { v ->
                        vm.updateLayout { it.copy(gridColumns = v.roundToInt()) }
                    }
                    SwitchRow("Mostrar nomes dos apps", layout.showLabels) { on ->
                        vm.updateLayout { it.copy(showLabels = on) }
                    }
                }

                SectionTitle("Área de trabalho")
                Card {
                    Text("Posição dos itens", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Choices(listOf(false, true), layout.freePlacement, { if (it) "Livre" else "Grade" }) { free ->
                        vm.updateLayout { it.copy(freePlacement = free) }
                    }
                    Text(
                        if (layout.freePlacement) "Itens ficam exatamente onde você soltar, como na área de trabalho do Windows."
                        else "Itens se encaixam nas células da grade.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    SwitchRow("Várias telas (deslizar para o lado)", layout.multiPage) { on ->
                        vm.updateLayout { it.copy(multiPage = on, pageCount = maxOf(it.pageCount, if (on) 2 else 1)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onClose(); controller.editMode = true }) { Text("Organizar área de trabalho") }
                    Text(
                        "Arraste para mover, use o ponto no canto para redimensionar e toque em um item para movê-lo de tela ou removê-lo.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                SectionTitle(if (state.themeId == "windows") "Barra de tarefas" else "Dock")
                Card {
                    val isWindows = state.themeId == "windows"
                    SwitchRow(if (isWindows) "Mostrar barra de tarefas" else "Mostrar dock / painel", layout.showDock) { on ->
                        vm.updateLayout { it.copy(showDock = on) }
                    }
                    if (layout.showDock) {
                        SwitchRow("Fundo translúcido", layout.dockBackground) { on -> vm.updateLayout { it.copy(dockBackground = on) } }
                        SwitchRow("Mostrar hora", layout.showTray) { on -> vm.updateLayout { it.copy(showTray = on) } }
                        if (layout.showTray) {
                            SwitchRow("Mostrar data junto da hora", layout.trayShowDate) { on -> vm.updateLayout { it.copy(trayShowDate = on) } }
                            SwitchRow("Mostrar Wi-Fi e bateria", layout.trayShowStatus) { on -> vm.updateLayout { it.copy(trayShowStatus = on) } }
                        }
                        if (isWindows) {
                            SwitchRow("Botão de pesquisa", layout.dockShowSearch) { on -> vm.updateLayout { it.copy(dockShowSearch = on) } }
                            Spacer(Modifier.height(6.dp))
                            Text("Alinhamento", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Choices(listOf(true, false), layout.dockCentered, { if (it) "Centralizado" else "À esquerda" }) { centered ->
                                vm.updateLayout { it.copy(dockCentered = centered) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val iconValue = if (layout.dockIconSizeDp > 0) layout.dockIconSizeDp else if (isWindows) 32 else 46
                        SliderRow("Tamanho dos ícones", "$iconValue dp", iconValue.toFloat(), 24f..64f, 9) { v ->
                            vm.updateLayout { it.copy(dockIconSizeDp = v.roundToInt()) }
                        }
                        SliderRow("Máximo de apps", "${layout.dockMaxApps}", layout.dockMaxApps.toFloat(), 3f..8f, 4) { v ->
                            vm.updateLayout { it.copy(dockMaxApps = v.roundToInt()) }
                        }
                        Text(
                            "Apps na dock: " + state.dock.joinToString { it.label }.ifEmpty { "nenhum" } +
                                ". Toque e segure um app para fixar ou desafixar.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )
                        Spacer(Modifier.height(14.dp))
                        WindowModeSettings(state, controller)
                    } else {
                        Text(
                            "Sem a barra, deslize para cima para abrir os apps e para baixo para o painel de controle.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )
                    }
                }

                SectionTitle("Telas")
                Card {
                    if (state.themeId == "ubuntu" || state.themeId == "macos") {
                        SwitchRow("Mostrar barra superior", layout.showTopBar) { on -> vm.updateLayout { it.copy(showTopBar = on) } }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("Tela à esquerda", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Choices(LeftScreen.entries, state.leftScreen, { it.label }) { vm.setLeftScreen(it) }
                    Text(
                        "Deslize para a direita na primeira tela. O feed mostra notícias com imagens por assunto e abre as matérias " +
                            "dentro do launcher. O Discover do Google não pode ser embutido em outros launchers; a última opção abre o app do Google.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                SectionTitle("Gestos")
                Card {
                    Text("Deslizar para baixo na área de trabalho", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Choices(SwipeAction.entries, state.swipeDown, { it.label }) { vm.setSwipeDown(it) }
                    Text(
                        "Deslizar para cima abre o menu de apps.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    HyperOsGestures(controller)
                }

                SectionTitle("Importar")
                Card {
                    var importResult by remember { mutableStateOf<String?>(null) }
                    Text(
                        "Traz os apps, pastas e o dock da tela inicial do launcher do sistema. A organização atual deste tema é substituída.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { importResult = vm.importHome(mostUsed = false) }) { Text("Importar da tela inicial do sistema") }
                    OutlinedButton(onClick = {
                        if (vm.hasUsageAccess()) importResult = vm.importHome(mostUsed = true)
                        else {
                            importResult = "Ative o acesso ao uso para o Launcher e toque de novo."
                            controller.openUsageAccessSettings()
                        }
                    }) { Text("Importar apps mais usados") }
                    importResult?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
                }

                SectionTitle("Widgets")
                Card {
                    if (state.widgetIds.isEmpty()) {
                        Text("Nenhum widget na tela inicial.", fontSize = 13.sp, color = Color.Gray)
                    }
                    state.widgetIds.forEach { id ->
                        val label = remember(id) {
                            controller.widgets.info(id)?.let { info ->
                                runCatching { info.loadLabel(controller.widgets.packageManager) }.getOrNull()
                            } ?: "Widget"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, modifier = Modifier.weight(1f))
                            IconButton(onClick = { controller.removeWidget(id) }) { Icon(Icons.Default.Delete, "Remover") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { controller.widgetPickerOpen = true }) { Text("Adicionar widget") }
                }

                SectionTitle("Sistema")
                Card {
                    OutlinedButton(onClick = controller::requestDefaultLauncher) {
                        Text(if (isDefault) "Trocar launcher padrão" else "Definir como launcher padrão")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = controller::openSystemSettings) { Text("Configurações do Android") }
                }
            }
        }
    }
}

/**
 * Modo janela: os apps abertos pelo Launcher ficam numa janela livre do tamanho da tela menos a
 * barra, para que a barra nunca cubra a navegação do app. Depende do modo de janelas do sistema.
 */
@Composable
private fun WindowModeSettings(state: LauncherUiState, controller: LauncherController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var supported by remember { mutableStateOf(controller.freeformSupported()) }
    var granted by remember { mutableStateOf(controller.canWriteSecureSettings()) }
    LifecycleResumeEffect(Unit) {
        supported = controller.freeformSupported()
        granted = controller.canWriteSecureSettings()
        onPauseOrDispose { }
    }
    Text("Modo janela (experimental)", fontWeight = FontWeight.SemiBold)
    SwitchRow("Abrir apps acima da barra", state.windowMode) { on -> controller.viewModel.setWindowMode(on) }
    Text(
        "Os apps abertos pela barra, pelo menu ou pela área de trabalho ficam numa janela que ocupa a tela inteira menos a barra, " +
            "então a barra não cobre a navegação do app. Apps que já estavam abertos continuam em tela cheia até serem fechados " +
            "nos recentes. O HyperOS pode ignorar a janela em alguns apps.",
        fontSize = 12.sp,
        color = Color.Gray,
    )
    if (supported) {
        Text("Modo de janelas do sistema: ativo", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    } else if (granted) {
        Text("O modo de janelas do sistema está desligado.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        OutlinedButton(onClick = {
            if (controller.enableFreeform()) controller.toast("Ativado. Reinicie o celular para aplicar.")
            else controller.toast("Não foi possível ativar")
            supported = controller.freeformSupported()
        }) { Text("Ativar modo de janelas") }
    } else {
        val commands = "adb shell settings put global enable_freeform_support 1\n" +
            "adb shell settings put global force_resizable_activities 1"
        Text(
            "O modo de janelas do sistema está desligado. Ative em Opções do desenvolvedor (\"Ativar janelas de formato livre\" e " +
                "\"Forçar atividades a serem redimensionáveis\") e reinicie, ou rode pelo ADB:",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            commands,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(10.dp),
        )
        OutlinedButton(onClick = {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("comandos", commands))
            controller.toast("Comandos copiados")
        }) { Text("Copiar comandos") }
    }
}

/**
 * No HyperOS/MIUI os gestos de tela cheia ficam bloqueados com outro launcher. Com a permissão
 * WRITE_SECURE_SETTINGS (concedida uma vez pelo ADB), o Launcher consegue liberá-los sozinho.
 */
@Composable
private fun HyperOsGestures(controller: LauncherController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(controller.canWriteSecureSettings()) }
    var forced by remember { mutableStateOf(controller.hyperOsGesturesForced()) }
    LifecycleResumeEffect(Unit) {
        granted = controller.canWriteSecureSettings()
        forced = controller.hyperOsGesturesForced()
        onPauseOrDispose { }
    }
    val command = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    Text("Gestos de navegação do HyperOS / MIUI", fontWeight = FontWeight.SemiBold)
    if (granted) {
        SwitchRow("Liberar gestos de tela cheia", forced) { on ->
            if (controller.setHyperOsGestures(on)) forced = on
        }
        Text(
            "Depois de ligar, escolha \"Gestos\" em Configurações → Tela inicial → Navegação do sistema.",
            fontSize = 12.sp,
            color = Color.Gray,
        )
        OutlinedButton(onClick = { controller.start(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) }) { Text("Abrir configurações") }
    } else {
        Text(
            "O sistema só libera os gestos com o launcher de fábrica. Para o Launcher liberar sozinho, conceda uma vez esta permissão " +
                "pelo ADB (do computador, ou pelo próprio celular com Termux ou Shizuku):",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            command,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(10.dp),
        )
        OutlinedButton(onClick = {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("comando", command))
            controller.toast("Comando copiado")
        }) { Text("Copiar comando") }
    }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == selected
            Text(
                label(option),
                fontSize = 13.sp,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = Color.Gray, fontSize = 13.sp)
    }
    Slider(value = current, onValueChange = onChange, valueRange = range, steps = steps)
}

/** Miniatura do tema: gradiente com as cores dele e um esboço de dock. */
@Composable
private fun ThemePreview(theme: LauncherThemeSpec, selected: Boolean, onSelect: () -> Unit) {
    val border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color(0x22000000))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = theme.available, onClick = onSelect)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 100.dp, height = 170.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(theme.previewColors))
                .border(border, RoundedCornerShape(14.dp)),
        ) {
            Column(Modifier.align(Alignment.TopCenter).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x99FFFFFF))) }
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAAFFFFFF)),
            )
            if (!theme.available) {
                Text(
                    "Em breve",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(theme.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
        Text(theme.description, fontSize = 11.sp, color = Color.Gray, maxLines = 2, lineHeight = 13.sp)
    }
}
