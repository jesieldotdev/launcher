package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.data.HtmlRole
import dev.jesiel.launcher.data.HtmlStore
import dev.jesiel.launcher.data.StoreWidget
import dev.jesiel.launcher.data.HtmlTemplate
import dev.jesiel.launcher.data.HtmlTemplates
import dev.jesiel.launcher.data.HtmlWidget
import dev.jesiel.launcher.data.HtmlWidgetGuide
import dev.jesiel.launcher.data.LockScreen
import dev.jesiel.launcher.data.LockStyle

/** Tamanho de tela usado nas prévias de tela cheia (bloqueio e lista de apps). */
private val PHONE_W = 360.dp
private val PHONE_H = 760.dp

/** Fundo das prévias, parecido com um papel de parede. */
private val PreviewBackground = Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3A5BA0)))

/**
 * Lista de apps em HTML, no lugar da gaveta do tema: o widget escolhido em tela cheia.
 * Voltar, Home ou `launcher://fechar` fecham; abrir um app também.
 */
@Composable
fun HtmlAppList(widget: HtmlWidget, controller: LauncherController, palette: LauncherPalette, search: Boolean) {
    Box(Modifier.fillMaxSize().imePadding()) {
        HtmlWidgetView(
            widget,
            controller,
            palette,
            Modifier.fillMaxSize(),
            extra = remember(search) { mapOf("modoBusca" to if (search) "sim" else "") },
        )
    }
}

/**
 * Estúdio HTML: todos os widgets HTML num só lugar, com prévia, onde cada um é usado
 * (área de trabalho, tela de bloqueio, lista de apps), modelos e o guia para outras IAs.
 */
@Composable
fun HtmlStudio(controller: LauncherController, palette: LauncherPalette) {
    val vm = controller.viewModel
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lock by LockScreen.settings(context).collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf<HtmlWidget?>(null) }
    var publishing by remember { mutableStateOf<HtmlWidget?>(null) }
    // Loja: catálogo baixado quando a aba abre (e ao tocar em Atualizar).
    var store by remember { mutableStateOf<List<StoreWidget>?>(null) }
    var storeError by remember { mutableStateOf<String?>(null) }
    var storeLoading by remember { mutableStateOf(false) }
    var storeReload by remember { mutableStateOf(0) }
    var justDownloaded by remember { mutableStateOf<Pair<HtmlWidget, HtmlRole>?>(null) }
    androidx.compose.runtime.LaunchedEffect(tab == 2, storeReload) {
        if (tab != 2) return@LaunchedEffect
        storeLoading = true
        HtmlStore.fetch(context)
            .onSuccess { store = it.items; storeError = if (it.offline) "sem conexão" else null }
            .onFailure { storeError = it.message ?: "Sem conexão" }
        storeLoading = false
    }
    var choosing by remember { mutableStateOf<HtmlRole?>(null) }
    val colors = MaterialTheme.colorScheme
    val lockWidget = lock?.takeIf { it.style == LockStyle.HTML }?.htmlWidgetId?.let { id -> state.htmlWidgets.firstOrNull { it.id == id } }

    fun rolesOf(w: HtmlWidget): List<HtmlRole> = buildList {
        if (w.onDesktop) add(HtmlRole.DESKTOP)
        if (lockWidget?.id == w.id && lock?.enabled == true) add(HtmlRole.LOCK)
        if (state.appsHtml?.id == w.id) add(HtmlRole.APPS)
        if (state.dockHtml?.id == w.id) add(HtmlRole.DOCK)
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = { controller.htmlLibraryOpen = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = colors.onBackground)
                }
                Column(Modifier.weight(1f)) {
                    Text("Estúdio HTML", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    Text("Widgets, tela de bloqueio e lista de apps", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }

            // Onde o HTML está em uso agora (em duas linhas): tocar troca o escolhido.
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UsageCard(
                    "Área de trabalho",
                    state.htmlWidgets.count { it.onDesktop }.let { if (it == 0) "Nenhum widget" else if (it == 1) "1 widget" else "$it widgets" },
                    active = state.htmlWidgets.any { it.onDesktop },
                ) { tab = 0 }
                UsageCard(
                    "Tela de bloqueio",
                    when {
                        lock?.enabled != true -> "Desligada"
                        lockWidget != null -> lockWidget.name
                        else -> "Do tema"
                    },
                    active = lock?.enabled == true && lockWidget != null,
                ) { choosing = HtmlRole.LOCK }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UsageCard("Lista de apps", state.appsHtml?.name ?: "Do tema", active = state.appsHtml != null) { choosing = HtmlRole.APPS }
                UsageCard("Dock", state.dockHtml?.name ?: "Do tema", active = state.dockHtml != null) { choosing = HtmlRole.DOCK }
            }

            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Meus widgets (${state.htmlWidgets.size})", "Modelos", "Loja").forEachIndexed { i, label ->
                    StudioChip(label, selected = tab == i) { tab = i }
                }
            }

            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 0) {
                    if (state.htmlWidgets.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Text(
                                "Nenhum widget ainda. Toque em Novo ou escolha um modelo.",
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(state.htmlWidgets, key = { it.id }) { w ->
                        val roles = rolesOf(w)
                        WidgetCard(
                            widget = w,
                            role = roles.firstOrNull { it != HtmlRole.DESKTOP } ?: HtmlRole.DESKTOP,
                            roles = roles,
                            controller = controller,
                            palette = palette,
                            onOpen = { controller.editHtmlWidget(w) },
                        ) { close ->
                            DropdownMenuItem(text = { Text("Editar") }, onClick = { close(); controller.editHtmlWidget(w) })
                            DropdownMenuItem(text = { Text("Testar") }, onClick = { close(); controller.testHtml(w) })
                            DropdownMenuItem(
                                text = { Text(if (w.onDesktop) "Tirar da área de trabalho" else "Pôr na área de trabalho") },
                                onClick = { close(); vm.setHtmlWidgetOnDesktop(w.id, !w.onDesktop) },
                            )
                            if (HtmlRole.LOCK in roles) {
                                DropdownMenuItem(text = { Text("Abrir a tela de bloqueio de verdade") }, onClick = { close(); LockScreen.show(context) })
                            } else {
                                DropdownMenuItem(text = { Text("Usar na tela de bloqueio") }, onClick = { close(); controller.useOnLockScreen(w) })
                            }
                            if (HtmlRole.APPS in roles) {
                                DropdownMenuItem(text = { Text("Voltar à lista do tema") }, onClick = { close(); vm.setAppsHtml(null) })
                            } else {
                                DropdownMenuItem(text = { Text("Usar como lista de apps") }, onClick = { close(); controller.useAsAppList(w) })
                            }
                            if (HtmlRole.DOCK in roles) {
                                DropdownMenuItem(text = { Text("Voltar à dock do tema") }, onClick = { close(); vm.setDockHtml(null) })
                            } else {
                                DropdownMenuItem(text = { Text("Usar como dock") }, onClick = { close(); controller.useAsDock(w) })
                            }
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { close(); vm.duplicateHtmlWidget(w.id) })
                            DropdownMenuItem(text = { Text("Publicar na loja") }, onClick = { close(); publishing = w })
                            DropdownMenuItem(text = { Text("Compartilhar código") }, onClick = { close(); shareHtml(controller, w.name, w.html) })
                            DropdownMenuItem(text = { Text("Excluir", color = colors.error) }, onClick = { close(); confirmDelete = w })
                        }
                    }
                } else if (tab == 2) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        StoreHeader(
                            loading = storeLoading,
                            error = storeError,
                            count = store?.size,
                            onRefresh = { storeReload++ },
                            onOpenPage = { controller.openUrl(HtmlStore.PAGE_URL) },
                        )
                    }
                    val items = store.orEmpty()
                    items(items, key = { "s-" + it.id }) { item ->
                        val local = state.htmlWidgets.firstOrNull { it.storeId == item.id }
                        StoreCard(
                            item = item,
                            local = local,
                            controller = controller,
                            palette = palette,
                            onDownload = {
                                // Já baixado e com versão nova: atualiza no lugar (mantém onde está em uso).
                                val widget = HtmlStore.toWidget(item, keepId = local?.id)
                                vm.saveHtmlWidget(if (local != null) widget.copy(onDesktop = local.onDesktop, software = local.software) else widget)
                                if (local == null) justDownloaded = widget to item.role else controller.toast("\"${item.name}\" atualizado")
                            },
                            onTest = { controller.testHtml(HtmlStore.toWidget(item), item.role) },
                        )
                    }
                } else {
                    item(span = StaggeredGridItemSpan.FullLine) { GuideCard(controller) }
                    HtmlRole.entries.forEach { role ->
                        val list = HtmlTemplates.all.filter { it.role == role }
                        if (list.isEmpty()) return@forEach
                        item(span = StaggeredGridItemSpan.FullLine, key = "h-${role.name}") {
                            Text(role.label, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(list, key = { "t-" + it.name }) { t ->
                            TemplateCard(t, controller, palette) { controller.newHtmlWidget(t) }
                        }
                    }
                }
            }
        }

        if (tab == 0) {
            ExtendedFloatingActionButton(
                onClick = { controller.newHtmlWidget() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Novo") },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            )
        }
    }

    choosing?.let { role ->
        RoleChooser(
            role = role,
            widgets = state.htmlWidgets,
            current = when (role) {
                HtmlRole.LOCK -> lockWidget?.takeIf { lock?.enabled == true }?.id
                HtmlRole.DOCK -> state.dockHtml?.id
                else -> state.appsHtml?.id
            },
            onTheme = {
                when (role) {
                    HtmlRole.LOCK -> LockScreen.update(context) { it.copy(style = LockStyle.THEME) }
                    HtmlRole.DOCK -> vm.setDockHtml(null)
                    else -> vm.setAppsHtml(null)
                }
                choosing = null
            },
            onPick = { w ->
                controller.useHtml(w, role)
                choosing = null
            },
            onTemplate = { tab = 1; choosing = null },
            onDismiss = { choosing = null },
        )
    }

    publishing?.let { w ->
        PublishDialog(w, controller.roleOf(w), controller) { publishing = null }
    }

    justDownloaded?.let { (w, role) ->
        AlertDialog(
            onDismissRequest = { justDownloaded = null },
            title = { Text("\"${w.name}\" baixado") },
            text = {
                Text(
                    if (role == HtmlRole.DESKTOP) "Já está na área de trabalho e em Meus widgets."
                    else "Está em Meus widgets. Quer usar agora como ${role.label.lowercase()}?",
                )
            },
            confirmButton = {
                if (role != HtmlRole.DESKTOP) {
                    TextButton(onClick = { controller.useHtml(w, role); justDownloaded = null }) { Text("Usar agora") }
                } else {
                    TextButton(onClick = { justDownloaded = null }) { Text("OK") }
                }
            },
            dismissButton = {
                if (role != HtmlRole.DESKTOP) TextButton(onClick = { justDownloaded = null }) { Text("Depois") }
            },
        )
    }

    confirmDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir \"${w.name}\"?") },
            text = { Text("O widget sai da área de trabalho, da tela de bloqueio, da lista de apps e da dock. Um backup é feito antes.") },
            confirmButton = {
                TextButton(onClick = {
                    if (state.appsHtml?.id == w.id) vm.setAppsHtml(null)
                    if (state.dockHtml?.id == w.id) vm.setDockHtml(null)
                    if (lockWidget?.id == w.id) LockScreen.update(context) { it.copy(style = LockStyle.THEME, htmlWidgetId = null) }
                    vm.removeHtmlWidget(w.id)
                    confirmDelete = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** Cartão de "onde está em uso". */
@Composable
private fun androidx.compose.foundation.layout.RowScope.UsageCard(title: String, value: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) colors.primaryContainer else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, fontSize = 11.sp, color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) colors.onPrimaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StudioChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        fontSize = 13.sp,
        color = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary else colors.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * Prévia de um widget. Os de tela cheia são desenhados no tamanho de um celular e reduzidos,
 * para a página ter o mesmo formato que terá de verdade. Quem usa a prévia põe uma camada por cima
 * se ela não deve receber toques.
 */
@Composable
fun HtmlPreview(widget: HtmlWidget, role: HtmlRole, controller: LauncherController, palette: LauncherPalette, modifier: Modifier = Modifier, onStatus: ((String) -> Unit)? = null) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp)).background(PreviewBackground)) {
        if (role == HtmlRole.DESKTOP) {
            HtmlWidgetView(widget, controller, palette, Modifier.fillMaxWidth().height(maxWidth * 0.62f).padding(6.dp), onStatus = onStatus)
        } else if (role == HtmlRole.DOCK) {
            // Dock: a largura de um celular e a altura configurada, reduzidas para caber.
            val dockH = controller.viewModel.state.value.dockHtmlHeight.dp
            val scale = maxWidth / PHONE_W
            Box(Modifier.size(PHONE_W * scale, (dockH + 24.dp) * scale).clipToBounds()) {
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(PHONE_W, dockH + 24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .padding(12.dp),
                ) { HtmlWidgetView(widget, controller, palette, Modifier.fillMaxSize(), onStatus = onStatus) }
            }
        } else {
            val scale = (maxWidth / PHONE_W).coerceAtMost(if (constraints.hasBoundedHeight) maxHeight / PHONE_H else 1f)
            Box(Modifier.align(Alignment.Center).size(PHONE_W * scale, PHONE_H * scale).clipToBounds()) {
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(PHONE_W, PHONE_H)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) { HtmlWidgetView(widget, controller, palette, Modifier.fillMaxSize(), onStatus = onStatus) }
            }
        }
    }
}

@Composable
private fun WidgetCard(
    widget: HtmlWidget,
    role: HtmlRole,
    roles: List<HtmlRole>,
    controller: LauncherController,
    palette: LauncherPalette,
    onOpen: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(8.dp),
    ) {
        Box {
            HtmlPreview(widget, role, controller, palette, Modifier.fillMaxWidth())
            Box(Modifier.matchParentSize().clickable(onClick = onOpen))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(widget.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
                Text(
                    if (roles.isEmpty()) "Guardado" else roles.joinToString(" · ") { it.label },
                    fontSize = 11.sp,
                    color = if (roles.isEmpty()) colors.onSurfaceVariant else colors.primary,
                    maxLines = 2,
                )
                Text(
                    listOfNotNull(if (widget.interactive) "JavaScript" else "Leve", if (widget.network) "Internet" else null).joinToString(" · "),
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Opções", tint = colors.onSurfaceVariant) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: HtmlTemplate, controller: LauncherController, palette: LauncherPalette, onUse: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val widget = remember(template) {
        HtmlWidget(id = "modelo-" + template.name, name = template.name, html = template.html, interactive = template.html.contains("<script"))
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onUse)
            .padding(8.dp),
    ) {
        Box {
            HtmlPreview(widget, template.role, controller, palette, Modifier.fillMaxWidth())
            Box(Modifier.matchParentSize().clickable(onClick = onUse))
        }
        Text(template.name, fontWeight = FontWeight.SemiBold, color = colors.onSurface, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        Text("Criar a partir deste", fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Copiar o guia para pedir um widget a outra IA. */
@Composable
private fun GuideCard(controller: LauncherController) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Text("Criar com outra IA", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Text(
            "Copie o guia com todas as variáveis (inclusive a lista de apps e os ícones), cole no ChatGPT, Claude ou Gemini e descreva o que quer.",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioChip("Copiar guia", selected = true) {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Guia de widget HTML", HtmlWidgetGuide.build()))
                controller.toast("Guia copiado")
            }
            StudioChip("Importar .html", selected = false) { controller.newHtmlWidget(); controller.importHtmlFile?.invoke() }
        }
    }
}

/** Escolher qual widget vai para a tela de bloqueio ou para a lista de apps. */
@Composable
private fun RoleChooser(
    role: HtmlRole,
    widgets: List<HtmlWidget>,
    current: String?,
    onTheme: () -> Unit,
    onPick: (HtmlWidget) -> Unit,
    onTemplate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(role.label) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                ChooserRow("Do tema", current == null, onTheme)
                widgets.forEach { w -> ChooserRow(w.name, current == w.id) { onPick(w) } }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (role == HtmlRole.DOCK) "A dock em HTML aparece no lugar da dock (ou barra de tarefas) em todos os temas. A altura fica em Ajustes › Tela inicial › Dock."
                    else if (role == HtmlRole.APPS) "A lista em HTML abre no lugar da gaveta em todos os temas (deslizar para cima, botão de apps e busca)."
                    else "Escolher um widget liga a tela de bloqueio do Launcher.",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onTemplate) { Text("Ver modelos") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun ChooserRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Compartilhar o código do widget (para guardar ou mandar a outra pessoa ou IA). */
fun shareHtml(controller: LauncherController, name: String, html: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, name)
        .putExtra(android.content.Intent.EXTRA_TEXT, html)
    controller.start(android.content.Intent.createChooser(send, "Compartilhar \"$name\""))
}

/** Topo da aba Loja: estado do catálogo e como publicar. */
@Composable
private fun StoreHeader(loading: Boolean, error: String?, count: Int?, onRefresh: () -> Unit, onOpenPage: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.secondaryContainer)
            .padding(14.dp),
    ) {
        Text("Loja da comunidade", fontWeight = FontWeight.SemiBold, color = colors.onSecondaryContainer)
        Text(
            when {
                loading && count == null -> "Carregando o catálogo…"
                error != null && count == null -> "Não deu para abrir a loja: $error"
                error != null -> "Sem conexão: mostrando a última cópia ($count widgets)."
                else -> "$count widgets. Baixe, teste antes de baixar e publique os seus pelo menu ⋮ de Meus widgets."
            },
            fontSize = 12.sp,
            color = colors.onSecondaryContainer,
        )
        Text(
            "Publicar abre o GitHub com tudo preenchido (precisa de uma conta). O widget entra na loja depois de aprovado.",
            fontSize = 11.sp,
            color = colors.onSecondaryContainer.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioChip(if (loading) "Atualizando…" else "Atualizar", selected = true, onClick = onRefresh)
            StudioChip("Ver no GitHub", selected = false, onClick = onOpenPage)
        }
    }
}

@Composable
private fun StoreCard(
    item: StoreWidget,
    local: HtmlWidget?,
    controller: LauncherController,
    palette: LauncherPalette,
    onDownload: () -> Unit,
    onTest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val preview = remember(item) { HtmlStore.toWidget(item, keepId = "loja-" + item.id) }
    val outdated = local != null && local.storeUpdated != item.updated
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(8.dp),
    ) {
        Box {
            HtmlPreview(preview, item.role, controller, palette, Modifier.fillMaxWidth())
            // Tocar na prévia testa o widget (links e scripts ficam para o testador).
            Box(Modifier.matchParentSize().clickable(onClick = onTest))
        }
        Text(item.name, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        Text(
            "${item.role.label} · por ${item.author}",
            fontSize = 11.sp,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (item.description.isNotBlank()) {
            Text(item.description, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Text(
            listOfNotNull(if (item.interactive) "JavaScript" else "Leve", if (item.network) "Usa internet" else null).joinToString(" · "),
            fontSize = 11.sp,
            color = if (item.network) colors.tertiary else colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudioChip(
                when {
                    local == null -> "Baixar"
                    outdated -> "Atualizar"
                    else -> "Baixado ✓"
                },
                selected = local == null || outdated,
                onClick = { if (local == null || outdated) onDownload() },
            )
            StudioChip("Testar", selected = false, onClick = onTest)
        }
    }
}

/** Enviar um widget para a loja: dados, aviso de código público e abrir o GitHub. */
@Composable
private fun PublishDialog(widget: HtmlWidget, currentRole: HtmlRole, controller: LauncherController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("loja", android.content.Context.MODE_PRIVATE) }
    var author by remember { mutableStateOf(prefs.getString("autor", "").orEmpty()) }
    var description by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(currentRole) }
    var agree by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publicar \"${widget.name}\"") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                androidx.compose.material3.OutlinedTextField(author, { author = it.take(40) }, label = { Text("Seu nome (opcional)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(description, { description = it.take(280) }, label = { Text("Descrição") }, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Text("Uso", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HtmlRole.entries.forEach { r -> StudioChip(r.label, selected = r == role) { role = r } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { agree = !agree }) {
                    androidx.compose.material3.Checkbox(agree, { agree = it })
                    Text("Entendo que o código fica público para todos.", fontSize = 13.sp)
                }
                Text(
                    "Abre o GitHub com a publicação preenchida; é só tocar em \"Submit new issue\" (precisa entrar na sua conta). " +
                        "O widget aparece na loja depois de aprovado.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = agree, onClick = {
                prefs.edit().putString("autor", author.trim()).apply()
                val body = HtmlStore.issueBody(widget, author, description, role)
                val (url, tooLong) = HtmlStore.issueUrl(widget, body)
                if (tooLong) {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Widget para a loja", body))
                    controller.toast("Widget grande: copiado. Cole no campo de texto do GitHub.")
                }
                controller.start(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                onDismiss()
            }) { Text("Abrir no GitHub") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
