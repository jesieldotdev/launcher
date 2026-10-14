package dev.jesiel.launcher.ui.common

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.data.HtmlTemplates
import dev.jesiel.launcher.data.HtmlVariables
import dev.jesiel.launcher.data.HtmlWidget

/** Editor de widget HTML: código, modelos, variáveis e prévia ao vivo. */
@Composable
fun HtmlWidgetEditor(
    initial: HtmlWidget,
    controller: LauncherController,
    palette: LauncherPalette,
    importedHtml: String?,
    onImportedConsumed: () -> Unit,
) {
    val vm = controller.viewModel
    val isNew = vm.state.value.htmlWidgets.none { it.id == initial.id }
    var name by remember { mutableStateOf(initial.name) }
    val code = remember { CodeEditorState(initial.html) }
    val html = code.text
    var fullscreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var fontSize by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(12f) }
    var finding by remember { mutableStateOf(false) }
    var codeFocused by remember { mutableStateOf(false) }
    // Verificação de tags só depois de uma pausa na digitação.
    var problem by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(html) {
        kotlinx.coroutines.delay(500)
        problem = dev.jesiel.launcher.data.CodeTools.tagProblem(html)
    }
    androidx.activity.compose.BackHandler(enabled = fullscreen) { fullscreen = false }
    var interactive by remember { mutableStateOf(initial.interactive) }
    var network by remember { mutableStateOf(initial.network) }
    var software by remember { mutableStateOf(initial.software) }
    var status by remember { mutableStateOf("Carregando a prévia…") }
    androidx.compose.runtime.LaunchedEffect(importedHtml) {
        if (importedHtml != null) {
            code.set(importedHtml)
            onImportedConsumed()
        }
    }
    val draft = initial.copy(name = name.ifBlank { "Widget HTML" }, html = html, interactive = interactive, network = network, software = software)
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = { controller.htmlEditing = null }) { Icon(Icons.Default.Close, "Fechar", tint = colors.onBackground) }
            Text(if (isNew) "Novo widget HTML" else "Editar widget HTML", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = colors.onBackground)
            Button(onClick = {
                vm.saveHtmlWidget(draft)
                controller.htmlEditing = null
                controller.toast(if (isNew) "Widget adicionado à área de trabalho" else "Widget salvo")
            }) { Text("Salvar") }
        }

        // Prévia sobre um fundo escuro, parecido com um papel de parede.
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(if (fullscreen) 120.dp else 190.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3A5BA0))))
                .padding(8.dp),
        ) {
            HtmlWidgetView(draft, controller, palette, Modifier.fillMaxSize(), onStatus = { status = it })
        }
        val webViewVersion = remember {
            if (android.os.Build.VERSION.SDK_INT >= 26) android.webkit.WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } else null
        }
        Text(
            listOfNotNull(status, webViewVersion?.let { "WebView: $it" }).joinToString("\n"),
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (fullscreen) {
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                CodeHeader(problem, fontSize, { fontSize = it }, fullscreen = true, onFullscreen = { fullscreen = false })
                CodeEditor(
                    code,
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    fontSize = fontSize.sp,
                    onFocus = { codeFocused = it },
                )
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("Modelos", fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HtmlTemplates.all.forEach { (label, template) ->
                    Chip(label) {
                        code.set(template)
                        interactive = template.contains("<script")
                        network = template.contains("fetch(") || template.contains("https://fonts.")
                    }
                }
                Chip("Importar .html") { controller.importHtmlFile?.invoke() }
            }
            CodeHeader(problem, fontSize, { fontSize = it }, fullscreen = false, onFullscreen = { fullscreen = true })
            CodeEditor(
                code,
                Modifier.padding(vertical = 6.dp).fillMaxWidth().then(CodeEditorMinHeight),
                fontSize = fontSize.sp,
                onFocus = { codeFocused = it },
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            var access by remember { mutableStateOf(dev.jesiel.launcher.data.MediaWatcher.hasAccess(context)) }
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                access = dev.jesiel.launcher.data.MediaWatcher.hasAccess(context)
                onPauseOrDispose { }
            }
            if (!access && HtmlVariables.needsNotificationAccess(HtmlVariables.used(html))) {
                Column(
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(12.dp),
                ) {
                    val supported = remember { dev.jesiel.launcher.data.MediaWatcher.supported(context) }
                    Text(
                        if (supported) "Música e notificações precisam de permissão" else "Música e notificações indisponíveis na versão leve",
                        fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                    )
                    Text(
                        if (supported) "O Android só mostra o que está tocando para apps com acesso a notificações. Nada sai do aparelho."
                        else "Esta versão não tem o acesso a notificações (que o Play Protect bloqueia fora da Play Store). Instale a versão completa para usar essas variáveis.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                    if (supported) {
                        androidx.compose.material3.TextButton(onClick = { controller.start(dev.jesiel.launcher.data.MediaWatcher.accessIntent()) }) {
                            Text("Permitir acesso")
                        }
                    }
                }
            }
            Text("Variáveis (toque para inserir no cursor)", fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HtmlVariables.all.forEach { (key, _) -> Chip("{{$key}}") { code.insert("{{$key}}") } }
            }
            Text(
                HtmlVariables.all.joinToString("\n") { (k, d) -> "{{$k}} — $d" } +
                    "\n\nTambém em CSS: var(--bateria) e, no modo interativo, em JS: launcher.hora ou function onLauncherData(d) { … }." +
                    "\nLinks: launcher://app/<pacote>, launcher://apps, launcher://busca, launcher://painel, launcher://ajustes, " +
                    "launcher://notificacoes ou https://…" +
                    "\nMúsica: launcher://musica/alternar, /tocar, /pausar, /proxima, /anterior; launcher://musica abre o app que está tocando." +
                    "\nCapa no modo interativo: <img data-lsrc=\"musicaCapa\" src=\"{{musicaCapa}}\"> troca sozinha a cada faixa.",
                fontSize = 12.sp,
                color = Color.Gray,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Interativo (JavaScript)", color = colors.onBackground)
                    Text(
                        if (interactive) "Animações e scripts; recebe dados novos sem recarregar." else "Leve: sem scripts, redesenha só quando um valor muda.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                }
                Switch(interactive, { interactive = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Acesso à internet", color = colors.onBackground)
                    Text("Para imagens ou fontes online. Desligado economiza dados e bateria.", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(network, { network = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo de compatibilidade", color = colors.onBackground)
                    Text("Desenha por software. Use se o widget aparecer em branco no seu aparelho.", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(software, { software = it })
            }
            Text(
                "O widget fica pausado sempre que a tela inicial não está visível, então não gasta bateria enquanto você usa outros apps.",
                fontSize = 12.sp,
                color = colors.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!isNew) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    vm.removeHtmlWidget(initial.id)
                    controller.htmlEditing = null
                }) { Text("Excluir widget") }
            }
        }
        // Fixos acima do teclado enquanto se edita o código.
        if (finding) FindBar(code, onClose = { finding = false }, modifier = Modifier.fillMaxWidth().background(colors.surface))
        if (codeFocused || finding || fullscreen) {
            CodeToolbar(code, onFind = { finding = !finding }, modifier = Modifier.fillMaxWidth().background(colors.surface))
        }
    }
}

/** Título da área de código: aviso de tags, tamanho da fonte e tela cheia. */
@Composable
private fun CodeHeader(problem: String?, fontSize: Float, onFontSize: (Float) -> Unit, fullscreen: Boolean, onFullscreen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Código", fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Text(
                problem ?: "Tags em ordem",
                fontSize = 12.sp,
                color = if (problem != null) colors.error else Color.Gray,
                maxLines = 2,
            )
        }
        Chip("A−") { onFontSize((fontSize - 1f).coerceAtLeast(9f)) }
        Spacer(Modifier.width(6.dp))
        Chip("A+") { onFontSize((fontSize + 1f).coerceAtMost(20f)) }
        Spacer(Modifier.width(6.dp))
        Chip(if (fullscreen) "Sair da tela cheia" else "Tela cheia", onFullscreen)
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}


/** Biblioteca dos widgets HTML salvos: pôr ou tirar da área de trabalho, editar, duplicar, excluir. */
@Composable
fun HtmlWidgetLibrary(controller: LauncherController, palette: LauncherPalette) {
    val vm = controller.viewModel
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<HtmlWidget?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { controller.htmlLibraryOpen = false },
        title = { Text("Widgets HTML") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (state.htmlWidgets.isEmpty()) Text("Nenhum widget salvo ainda.", color = Color.Gray)
                state.htmlWidgets.forEach { w ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3A5BA0)))),
                        ) {
                            HtmlWidgetView(w, controller, palette, Modifier.fillMaxSize())
                            // Camada por cima: a prévia não recebe toques.
                            Box(Modifier.fillMaxSize().clickable { controller.htmlLibraryOpen = false; controller.htmlEditing = w })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name, fontWeight = FontWeight.SemiBold)
                                Text(if (w.onDesktop) "Na área de trabalho" else "Guardado", fontSize = 12.sp, color = Color.Gray)
                            }
                            androidx.compose.material3.TextButton(onClick = { vm.setHtmlWidgetOnDesktop(w.id, !w.onDesktop) }) {
                                Text(if (w.onDesktop) "Tirar" else "Adicionar")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.compose.material3.TextButton(onClick = { controller.htmlLibraryOpen = false; controller.htmlEditing = w }) { Text("Editar") }
                            androidx.compose.material3.TextButton(onClick = { vm.duplicateHtmlWidget(w.id) }) { Text("Duplicar") }
                            androidx.compose.material3.TextButton(onClick = { confirmDelete = w }) { Text("Excluir") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { controller.htmlLibraryOpen = false; controller.newHtmlWidget() }) { Text("Novo widget") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { controller.htmlLibraryOpen = false }) { Text("Fechar") } },
    )
    confirmDelete?.let { w ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir \"${w.name}\"?") },
            text = { Text("O widget sai da área de trabalho e da biblioteca. Um backup é feito antes.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.removeHtmlWidget(w.id); confirmDelete = null }) { Text("Excluir") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

