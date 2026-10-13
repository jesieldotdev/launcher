package dev.jesiel.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap

/** Um aplicativo que pode ser aberto a partir do launcher. */
data class AppInfo(
    /** Identificador estável: componente + número de série do usuário (perfil de trabalho). */
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    val category: AppCategory = AppCategory.TOOLS,
) {
    val packageName: String get() = component.packageName
}

/** Um item da tela inicial: um app favorito ou uma pasta de apps. */
sealed interface HomeItem {
    val id: String

    data class App(val app: AppInfo) : HomeItem {
        override val id: String get() = app.key
    }

    data class Folder(override val id: String, val name: String, val apps: List<AppInfo>) : HomeItem
}

data class RecentApp(val app: AppInfo, val launchedAt: Long)

/** Categorias usadas nos filtros das gavetas de apps. */
enum class AppCategory(val label: String) {
    COMMUNICATION("Comunicação"),
    MEDIA("Multimídia"),
    PRODUCTIVITY("Produtividade"),
    GAMES("Jogos"),
    TOOLS("Ferramentas"),
}
