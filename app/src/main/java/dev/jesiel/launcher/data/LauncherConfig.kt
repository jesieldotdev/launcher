package dev.jesiel.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Todas as opções do launcher num JSON legível (para uma IA preparar a configuração inteira).
 * Exportar dá o JSON atual; aplicar aceita um JSON parcial: só as chaves presentes mudam.
 * Apps são indicados pelo pacote (ex.: "com.whatsapp").
 */
object LauncherConfig {
    const val FORMAT = "launcher-config"

    /** Chaves de cada tema em "layouts": descrição e limites (usados para validar). */
    val LAYOUT_KEYS: List<Triple<String, String, IntRange?>> = listOf(
        Triple("iconSize", "Tamanho dos ícones da área de trabalho (dp)", 24..96),
        Triple("columns", "Colunas da grade da área de trabalho", 3..8),
        Triple("labels", "Mostrar nome dos apps (true/false)", null),
        Triple("labelSize", "Tamanho do nome dos apps (sp)", 8..20),
        Triple("labelLines", "Linhas do nome dos apps", 1..3),
        Triple("cellHeight", "Altura de cada linha da grade (dp); 0 = automática", 0..240),
        Triple("itemSpacing", "Espaço em volta de cada item (dp)", 0..32),
        Triple("marginSide", "Margem extra dos lados da área de trabalho (dp)", 0..96),
        Triple("marginTop", "Margem extra em cima (dp)", 0..240),
        Triple("marginBottom", "Margem extra embaixo (dp)", 0..240),
        Triple("free", "Posição livre (true) ou grade (false)", null),
        Triple("pushDown", "Ao soltar um item sobre outros, os de baixo descem (true) ou vão para o vão livre mais próximo (false)", null),
        Triple("multiPage", "Várias telas, deslizando para o lado", null),
        Triple("pages", "Quantidade de telas", 1..12),
        Triple("themeWallpaper", "Usar o papel de parede do tema (false = o do sistema)", null),
        Triple("menuIcon", "Ícone do botão de apps: " + MenuIcon.entries.joinToString { it.name }, null),
        Triple("searchPill", "Pílula de busca acima da dock (tema ios)", null),
        Triple("topBar", "Barra superior (temas ubuntu e macos)", null),
        Triple("dock", "Mostrar a dock / barra de tarefas", null),
        Triple("dockBackground", "Fundo translúcido da dock", null),
        Triple("dockIcon", "Tamanho dos ícones da dock (dp); 0 = do tema", 0..80),
        Triple("dockMax", "Máximo de apps na dock", 1..8),
        Triple("dockPadding", "Espaço interno da dock (dp); -1 = do tema", -1..32),
        Triple("dockSpacing", "Espaço entre os ícones da dock (dp)", 0..32),
        Triple("dockMargin", "Margem dos lados da dock (dp)", 0..96),
        Triple("dockLift", "Distância da dock até a borda de baixo (dp)", 0..96),
        Triple("dockCorner", "Arredondamento da dock (dp); -1 = do tema", -1..60),
        Triple("dockTray", "Hora na dock / barra de tarefas", null),
        Triple("trayDate", "Data junto da hora", null),
        Triple("trayStatus", "Wi-Fi e bateria junto da hora", null),
        Triple("dockCentered", "Windows: ícones da barra centralizados", null),
        Triple("dockSearch", "Windows: botão de pesquisa na barra", null),
        Triple("menuAllApps", "Windows: o Iniciar abre direto em Todos os apps", null),
        Triple("drawerIcon", "Tamanho dos ícones na lista de apps (dp)", 24..96),
        Triple("drawerColumns", "Colunas da lista de apps", 3..8),
        Triple("letters", "Letras do alfabeto na lista de apps", null),
        Triple("appsHeader", "Título, exibição e filtros no topo da lista de apps", null),
        Triple("searchBottom", "Busca embaixo na lista de apps", null),
    )

    fun export(d: LauncherData, apps: List<AppInfo>): JSONObject {
        val pkg = apps.associate { it.key to it.packageName }
        val layouts = JSONObject()
        val themes = (d.layouts.keys + d.themeId).distinct()
        themes.forEach { id -> layouts.put(id, LauncherPrefs.layoutToJson(d.layoutFor(id))) }
        return JSONObject()
            .put("formato", FORMAT)
            .put("versao", 1)
            .put("tema", d.themeId)
            .put("modoCor", d.colorMode.name)
            .put("transparencia", d.transparency)
            .put("exibicaoApps", d.appsView.name)
            .put("telaEsquerda", d.leftScreen.name)
            .put("deslizarParaBaixo", d.swipeDown.name)
            .put("painelControle", d.controlCenter)
            .put("modoJanela", d.windowMode)
            .put("mesmaOrganizacao", d.sharedLayout)
            .put("appsNovosNaTelaAoLado", d.newAppsToHome)
            .put("pacoteIcones", d.iconPack ?: JSONObject.NULL)
            .put("estiloDoPacoteNosOutros", d.iconPackStyleUnthemed)
            .put("alturaDockHtml", d.dockHtmlHeight)
            .put("dock", JSONArray(d.dock.mapNotNull(pkg::get)))
            .put("telaInicial", JSONArray(d.homeOrder.mapNotNull(pkg::get)))
            .put("layouts", layouts)
    }

    /**
     * Aplica [json] sobre [d]. Devolve os dados novos e avisos (chaves desconhecidas, pacotes que
     * não estão instalados, valores fora do limite).
     */
    fun apply(d: LauncherData, json: JSONObject, apps: List<AppInfo>, themeIds: List<String>): Pair<LauncherData, List<String>> {
        val warnings = mutableListOf<String>()
        if (json.has("formato") && json.optString("formato") != FORMAT) warnings += "\"formato\" diferente de \"$FORMAT\"; tentando mesmo assim"
        var out = d
        fun <E : Enum<E>> enumOf(key: String, values: Array<E>, set: (E) -> Unit) {
            if (!json.has(key)) return
            val name = json.optString(key).uppercase()
            values.firstOrNull { it.name == name }?.let(set) ?: run { warnings += "$key: \"${json.optString(key)}\" não existe (use ${values.joinToString { it.name }})" }
        }
        fun bool(key: String, set: (Boolean) -> Unit) { if (json.has(key)) set(json.optBoolean(key)) }

        if (json.has("tema")) {
            val t = json.optString("tema")
            if (t in themeIds) out = out.copy(themeId = t) else warnings += "tema: \"$t\" não existe (use ${themeIds.joinToString()})"
        }
        enumOf("modoCor", ColorMode.entries.toTypedArray()) { out = out.copy(colorMode = it) }
        enumOf("exibicaoApps", AppsView.entries.toTypedArray()) { out = out.copy(appsView = it) }
        enumOf("telaEsquerda", LeftScreen.entries.toTypedArray()) { out = out.copy(leftScreen = it) }
        enumOf("deslizarParaBaixo", SwipeAction.entries.toTypedArray()) { out = out.copy(swipeDown = it) }
        bool("transparencia") { out = out.copy(transparency = it) }
        bool("painelControle") { out = out.copy(controlCenter = it) }
        bool("modoJanela") { out = out.copy(windowMode = it) }
        bool("mesmaOrganizacao") { out = out.copy(sharedLayout = it) }
        bool("appsNovosNaTelaAoLado") { out = out.copy(newAppsToHome = it) }
        bool("estiloDoPacoteNosOutros") { out = out.copy(iconPackStyleUnthemed = it) }
        if (json.has("pacoteIcones")) out = out.copy(iconPack = if (json.isNull("pacoteIcones")) null else json.optString("pacoteIcones").ifBlank { null })
        if (json.has("alturaDockHtml")) out = out.copy(dockHtmlHeight = json.optInt("alturaDockHtml", d.dockHtmlHeight).coerceIn(40, 240))

        // Apps pelo pacote (o primeiro app do pacote, se houver mais de um).
        val byPackage = apps.groupBy { it.packageName }
        fun keys(field: String): List<String>? {
            val a = json.optJSONArray(field) ?: return null
            return (0 until a.length()).mapNotNull { i ->
                val p = a.optString(i)
                byPackage[p]?.first()?.key ?: run { warnings += "$field: \"$p\" não está instalado"; null }
            }.distinct()
        }
        keys("dock")?.let { out = out.copy(dock = it.take(8)) }
        keys("telaInicial")?.let { list ->
            // Pastas continuam; os apps soltos passam a ser os da lista, nessa ordem.
            val folders = out.homeOrder.filter { it.startsWith("folder:") }
            val inFolders = out.folders.values.flatMap { it.appKeys }.toSet()
            out = out.copy(homeOrder = list.filter { it !in inFolders } + folders)
        }

        json.optJSONObject("layouts")?.let { layouts ->
            val known = LAYOUT_KEYS.associateBy { it.first }
            var all = out.layouts
            layouts.keys().forEach { theme ->
                if (theme !in themeIds) {
                    warnings += "layouts: tema \"$theme\" não existe"
                    return@forEach
                }
                val given = layouts.optJSONObject(theme) ?: return@forEach
                val merged = LauncherPrefs.layoutToJson(out.layoutFor(theme))
                given.keys().forEach { k ->
                    val spec = known[k]
                    if (spec == null) {
                        warnings += "layouts.$theme.$k: chave desconhecida"
                        return@forEach
                    }
                    val range = spec.third
                    val v = given.get(k)
                    if (range != null && v is Number) {
                        val n = v.toInt()
                        if (n !in range) warnings += "layouts.$theme.$k: $n fora de ${range.first}..${range.last}; ajustado"
                        merged.put(k, n.coerceIn(range))
                    } else merged.put(k, v)
                }
                all = all + (theme to LauncherPrefs.layoutFromJson(theme, merged))
            }
            out = out.copy(layouts = all)
        }

        val topKeys = setOf(
            "formato", "versao", "tema", "modoCor", "transparencia", "exibicaoApps", "telaEsquerda", "deslizarParaBaixo",
            "painelControle", "modoJanela", "mesmaOrganizacao", "appsNovosNaTelaAoLado", "pacoteIcones",
            "estiloDoPacoteNosOutros", "alturaDockHtml", "dock", "telaInicial", "layouts",
        )
        json.keys().forEach { if (it !in topKeys) warnings += "$it: chave desconhecida (ignorada)" }
        return out to warnings
    }

    /** Guia para colar numa IA, com todas as chaves, valores aceitos e a configuração atual. */
    fun guide(current: JSONObject, themes: List<Pair<String, String>>, apps: List<AppInfo>): String = buildString {
        appendLine("# Configuração do Launcher (Android) em JSON")
        appendLine()
        appendLine("Você vai preparar a configuração de um launcher Android. Responda com UM bloco ```json``` contendo só as chaves que devem mudar (as que faltarem ficam como estão). O usuário cola a resposta em Ajustes › Sistema › Configuração em JSON › Colar e aplicar.")
        appendLine()
        appendLine("## Chaves gerais")
        appendLine("- `tema`: " + themes.joinToString { (id, name) -> "\"$id\" ($name)" })
        appendLine("- `modoCor`: " + ColorMode.entries.joinToString { "\"${it.name}\" (${it.label})" })
        appendLine("- `exibicaoApps` (lista de apps): " + AppsView.entries.joinToString { "\"${it.name}\" (${it.label})" })
        appendLine("- `telaEsquerda`: " + LeftScreen.entries.joinToString { "\"${it.name}\" (${it.label})" })
        appendLine("- `deslizarParaBaixo` (gesto na tela inicial): " + SwipeAction.entries.joinToString { "\"${it.name}\" (${it.label})" })
        appendLine("- `transparencia`, `painelControle`, `modoJanela`, `mesmaOrganizacao` (mesmas posições em todos os temas), `appsNovosNaTelaAoLado`, `estiloDoPacoteNosOutros`: true/false")
        appendLine("- `pacoteIcones`: pacote de um pacote de ícones instalado, ou null para os do sistema")
        appendLine("- `alturaDockHtml`: altura da dock em HTML (40–240)")
        appendLine("- `dock`: lista de pacotes de apps, na ordem (até 8)")
        appendLine("- `telaInicial`: lista de pacotes dos apps soltos na tela inicial, na ordem (as pastas continuam)")
        appendLine()
        appendLine("## `layouts`: um objeto por tema (\"windows\", \"ios\"…), com qualquer uma destas chaves")
        LAYOUT_KEYS.forEach { (k, desc, range) -> appendLine("- `$k`: $desc" + (range?.let { " (${it.first}–${it.last})" } ?: "")) }
        appendLine()
        appendLine("## Apps instalados (nome → pacote)")
        apps.distinctBy { it.packageName }.forEach { appendLine("- ${it.label} → ${it.packageName}") }
        appendLine()
        appendLine("## Configuração atual")
        appendLine("```json")
        appendLine(current.toString(2))
        appendLine("```")
        appendLine()
        append("## Pedido\n[descreva aqui como quer o launcher]")
    }
}
