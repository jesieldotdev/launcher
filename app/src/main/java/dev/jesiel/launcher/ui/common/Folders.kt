package dev.jesiel.launcher.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.LauncherUiState
import dev.jesiel.launcher.data.AppInfo
import dev.jesiel.launcher.data.FolderData
import dev.jesiel.launcher.data.HomeItem

/** Ícone de pasta: miniaturas dos quatro primeiros apps sobre um fundo do tema. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTile(
    folder: HomeItem.Folder,
    state: LauncherUiState,
    controller: LauncherController,
    background: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = state.layout.iconSizeDp.dp,
    labelStyle: TextStyle = TextStyle(color = Color.White, fontSize = 12.sp),
    panelColor: Color = Color.White,
    panelTextColor: Color = Color.Black,
    labels: AppMenuLabels = AppMenuLabels(),
) {
    var open by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = { open = true }, onLongClick = { menuOpen = true })
                .padding(vertical = 6.dp, horizontal = 2.dp),
        ) {
            Box(
                Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(iconSize * 0.26f))
                    .background(background)
                    .padding(iconSize * 0.12f),
            ) {
                val mini = (iconSize - iconSize * 0.24f - 4.dp) / 2
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    folder.apps.take(4).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { Image(it.icon, null, Modifier.size(mini), colorFilter = LocalIconStyle.current.filter) }
                        }
                    }
                }
            }
            if (state.layout.showLabels) {
                Spacer(Modifier.height(6.dp))
                Text(
                    folder.name,
                    style = labelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(iconSize + 28.dp),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(12.dp)) {
            DropdownMenuItem(text = { Text("Abrir") }, onClick = { menuOpen = false; open = true })
            DropdownMenuItem(
                text = { Text("Desfazer pasta") },
                onClick = { menuOpen = false; controller.viewModel.deleteFolder(folder.id) },
            )
        }
    }

    if (open) {
        FolderDialog(folder, state, controller, panelColor, panelTextColor, labels, onDismiss = { open = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderDialog(
    folder: HomeItem.Folder,
    state: LauncherUiState,
    controller: LauncherController,
    panelColor: Color,
    textColor: Color,
    labels: AppMenuLabels,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var name by remember(folder.name) { mutableStateOf(folder.name) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = panelColor) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                if (renaming) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Nome da pasta") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            controller.viewModel.renameFolder(folder.id, name)
                            renaming = false
                        }) { Text("Salvar") }
                    }
                } else {
                    Text(
                        folder.name,
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { renaming = true }.padding(bottom = 4.dp),
                    )
                    Text("Toque no nome para renomear", color = textColor.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    folder.apps.forEach { app ->
                        AppTile(
                            app = app,
                            state = state,
                            controller = controller,
                            labelStyle = TextStyle(color = textColor, fontSize = 12.sp),
                            iconSize = 48.dp,
                            showLabel = true,
                            labels = labels,
                            folderId = folder.id,
                            onLaunched = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

/** Escolha da pasta de destino, ou criação de uma nova. */
@Composable
fun MoveToFolderDialog(
    app: AppInfo,
    folders: List<FolderData>,
    onDismiss: () -> Unit,
    onPick: (folderId: String?, newName: String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover ${app.label}") },
        text = {
            Column {
                folders.forEach { folder ->
                    Text(
                        folder.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(folder.id, folder.name) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Nova pasta") },
                    placeholder = { Text("Ex.: Trabalho") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null, newName.ifBlank { "Pasta" }) }) { Text("Criar pasta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
