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
    var role by remember { mutableStateOf(controller.htmlEditingRole) }
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
                // Novo widget vai só para onde foi escolhido; um existente continua onde já estava.
                val saved = if (isNew) draft.copy(onDesktop = role == dev.jesiel.launcher.data.HtmlRole.DESKTOP)
                else if (role == dev.jesiel.launcher.data.HtmlRole.DESKTOP) draft.copy(onDesktop = true) else draft
                vm.saveHtmlWidget(saved)
                controller.htmlEditing = null
                when (role) {
                    dev.jesiel.launcher.data.HtmlRole.LOCK -> controller.useOnLockScreen(saved)
                    dev.jesiel.launcher.data.HtmlRole.APPS -> controller.useAsAppList(saved)
                    dev.jesiel.launcher.data.HtmlRole.DESKTOP -> controller.toast(if (isNew) "Widget adicionado à área de trabalho" else "Widget salvo")
                }
            }) { Text("Salvar") }
        }

        // Prévia sobre um fundo escuro, parecido com um papel de parede. Tela de bloqueio e
        // lista de apps aparecem no formato de um celular.
        if (role == dev.jesiel.launcher.data.HtmlRole.DESKTOP) {
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
        } else {
            HtmlPreview(
                draft, role, controller, palette,
                Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(if (fullscreen) 170.dp else 320.dp),
                onStatus = { status = it },
            )
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
            Text(if (isNew) "Usar em" else "Ao salvar, usar também em", fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dev.jesiel.launcher.data.HtmlRole.entries.forEach { r ->
                    val on = r == role
                    Text(
                        r.label,
                        fontSize = 13.sp,
                        color = if (on) colors.onPrimary else colors.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) colors.primary else colors.onSurface.copy(alpha = 0.08f))
                            .clickable { role = r }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            if (role == dev.jesiel.launcher.data.HtmlRole.APPS) {
                Text(
                    "Lista de apps: use launcher.appsLista (JSON) e ícones em <img src=\"launcher-icon://pacote\">. Ligue o modo Interativo.",
                    fontSize = 12.sp, color = Color.Gray,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Modelos", fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Modelos do mesmo uso primeiro.
                HtmlTemplates.all.sortedBy { if (it.role == role) 0 else 1 }.forEach { t ->
                    Chip(t.name) {
                        code.set(t.html)
                        interactive = t.html.contains("<script")
                        network = t.html.contains("fetch(") || t.html.contains("https://fonts.")
                        role = t.role
                    }
                }
                Chip("Importar .html") { controller.importHtmlFile?.invoke() }
            }
            Text("Opções", fontWeight = FontWeight.SemiBold, color = colors.onBackground, modifier = Modifier.padding(top = 4.dp))
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
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            CodeHeader(problem, fontSize, { fontSize = it }, fullscreen = false, onFullscreen = { fullscreen = true })
            CodeEditor(
                code,
                // Altura limitada com rolagem própria: o código longo não empurra o resto da tela.
                Modifier.padding(vertical = 6.dp).fillMaxWidth().heightIn(min = 220.dp, max = 380.dp).verticalScroll(rememberScrollState()),
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
            Text("Criar com outra IA", fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Text(
                "Copie o guia com todas as variáveis e regras, cole no ChatGPT, Claude ou Gemini, descreva o widget e cole a resposta no código.",
                fontSize = 12.sp,
                color = Color.Gray,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun copy(text: String, what: String) {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Guia de widget HTML", text))
                    controller.toast("$what copiado")
                }
                Chip("Copiar guia") { copy(dev.jesiel.launcher.data.HtmlWidgetGuide.build(), "Guia") }
                Chip("Copiar guia + código atual") {
                    copy(dev.jesiel.launcher.data.HtmlWidgetGuide.build(html, interactive, network), "Guia com o código")
                }
                Chip("Compartilhar…") {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, dev.jesiel.launcher.data.HtmlWidgetGuide.build(html.takeIf { !isNew }, interactive, network))
                    controller.start(android.content.Intent.createChooser(send, "Enviar guia para"))
                }
            }
            var englishNames by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            fun nameOf(key: String) = if (englishNames) HtmlVariables.english[key] ?: key else key
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Variáveis (toque para inserir no cursor)", fontWeight = FontWeight.SemiBold, color = colors.onBackground, modifier = Modifier.weight(1f))
                Chip(if (englishNames) "EN" else "PT") { englishNames = !englishNames }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HtmlVariables.all.forEach { (key, _) -> nameOf(key).let { n -> Chip("{{$n}}") { code.insert("{{$n}}") } } }
            }
            Text(
                HtmlVariables.all.joinToString("\n") { (k, d) -> "{{$k}} · {{${HtmlVariables.english[k] ?: k}}} — $d" } +
                    "\n\nCada variável tem um nome em português e um em inglês, com o mesmo valor." +
                    "\n\nTambém em CSS: var(--bateria) e, no modo interativo, em JS: launcher.hora ou function onLauncherData(d) { … }." +
                    "\nLinks: launcher://app/<pacote>, launcher://apps, launcher://busca, launcher://painel, launcher://ajustes, " +
                    "launcher://notificacoes ou https://…" +
                    "\nMúsica: launcher://musica/alternar, /tocar, /pausar, /proxima, /anterior; launcher://musica abre o app que está tocando." +
                    "\nEm inglês: launcher://music/toggle, /play, /pause, /next, /previous; launcher://search, panel, notifications, settings." +
                    "\nCapa no modo interativo: <img data-lsrc=\"musicaCapa\" src=\"{{musicaCapa}}\"> troca sozinha a cada faixa.",
                fontSize = 12.sp,
                color = Color.Gray,
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
