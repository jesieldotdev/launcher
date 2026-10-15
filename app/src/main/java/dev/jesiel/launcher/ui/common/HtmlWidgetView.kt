package dev.jesiel.launcher.ui.common

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import android.app.AlarmManager
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.jesiel.launcher.LauncherController
import dev.jesiel.launcher.data.ClaudeLink
import dev.jesiel.launcher.data.MediaWatcher
import dev.jesiel.launcher.data.HtmlVariables
import dev.jesiel.launcher.data.HtmlWidget
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Valores das variáveis usadas pelo widget. Só o que o HTML usa é calculado: bateria e memória
 * nem são observadas se o widget não as menciona.
 */
@Composable
fun rememberHtmlValues(
    palette: LauncherPalette,
    used: Set<String>,
    controller: LauncherController? = null,
    extra: Map<String, String> = emptyMap(),
): Map<String, String> {
    val context = LocalContext.current
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    val battery = if ("bateria" in used || "carregando" in used || "tempBateria" in used) rememberBatteryState() else null
    val memory = if ("memoria" in used || "armazenamento" in used) rememberMemoryState() else null
    val ntfy = if ("ntfy" in used) ClaudeLink.topic(context).collectAsState().value.orEmpty() else ""
    val music = if (used.any { it.startsWith("musica") }) MediaWatcher.nowPlaying.collectAsState().value else null
    val notifications = if ("notificacoes" in used) MediaWatcher.notifications.collectAsState().value else 0
    val notificationItems = if (used.any { it == "notificacoesTexto" || it == "notificacaoUltima" || it == "notificacoesLista" }) {
        MediaWatcher.notificationItems.collectAsState().value
    } else emptyList()
    val network = if ("rede" in used) rememberNetworkType() else ""
    val volume = if ("volume" in used) rememberMediaVolume() else 0
    val needsState = controller != null && used.any { it == "ultimoApp" || it.startsWith("apps") }
    val launcherState = if (needsState) controller!!.viewModel.state.collectAsState().value else null
    val lastApp = if ("ultimoApp" in used) launcherState?.recents?.firstOrNull()?.app?.label.orEmpty() else ""
    // A lista de apps só é montada de novo quando apps, favoritos ou dock mudam.
    val appsList = if ("appsLista" in used && launcherState != null) {
        remember(launcherState.apps, launcherState.favoriteKeys, launcherState.dockKeys) {
            HtmlVariables.appsJson(launcherState.apps, launcherState.favoriteKeys, launcherState.dockKeys)
        }
    } else ""
    val recentApps = if ("appsRecentes" in used && launcherState != null) {
        remember(launcherState.recents) { HtmlVariables.appsJson(launcherState.recents.map { it.app }.distinctBy { it.key }.take(8)) }
    } else ""
    val appsCount = launcherState?.apps?.size ?: 0
    val dockApps = if ("appsDock" in used && launcherState != null) {
        remember(launcherState.dock) { HtmlVariables.appsJson(launcherState.dock, launcherState.favoriteKeys, launcherState.dockKeys) }
    } else ""
    // Posição da música: só anda (1×/s) se o widget mostra posição ou progresso e algo está tocando.
    val tick = if (music?.playing == true && ("musicaPosicao" in used || "musicaProgresso" in used)) rememberTicker(1_000) else 0L
    // A configuração muda quando o papel de parede (e com ele a paleta do Material You) muda.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val material = if (used.any { it in MaterialColors.names }) {
        remember(configuration, palette.dark, palette.accent) { MaterialColors.of(context, palette.dark, palette.accent.toArgb()) }
    } else emptyMap()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val insetTop = (WindowInsets.statusBars.getTop(density) / density.density).toInt()
    val insetBottom = (WindowInsets.navigationBars.getBottom(density) / density.density).toInt()
    return remember(now, battery, memory, palette, used, ntfy, music, notifications, notificationItems, network, volume, lastApp, tick, material, appsList, recentApps, appsCount, dockApps, extra, insetTop, insetBottom) {
        val date = Date(now)
        fun fmt(p: String) = SimpleDateFormat(p, locale).format(date)
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        buildMap {
            put("hora", fmt(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"))
            put("data", fmt(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM")))
            put("diaSemana", fmt("EEEE"))
            put("dia", fmt("d"))
            put("mes", fmt("MMMM"))
            put("ano", fmt("yyyy"))
            put("saudacao", when (hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" })
            put("bateria", (battery?.percent ?: 0).toString())
            put("carregando", if (battery?.charging == true) "Carregando" else "")
            put(
                "alarme",
                context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let {
                    SimpleDateFormat("EEE HH:mm", locale).format(Date(it))
                }.orEmpty(),
            )
            put("memoria", (memory?.usedPercent ?: 0).toString())
            put("armazenamento", memory?.let { "%.0f".format(it.storageFreeGb) } ?: "0")
            put("tema", if (palette.dark) "escuro" else "claro")
            put("cor", "#%06X".format(palette.accent.toArgb() and 0xFFFFFF))
            put("ntfy", ntfy)
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            put("horas", "%02d".format(cal.get(Calendar.HOUR_OF_DAY)))
            put("minutos", "%02d".format(cal.get(Calendar.MINUTE)))
            put("semana", cal.get(Calendar.WEEK_OF_YEAR).toString())
            if ("tempBateria" in used) put("tempBateria", batteryTemperature(context))
            put("rede", network)
            put("volume", volume.toString())
            put("ultimoApp", lastApp)
            put("notificacoes", notifications.toString())
            put("notificacoesTexto", notificationItems.take(5).joinToString("\n", transform = HtmlVariables::notificationLine))
            put("notificacaoUltima", notificationItems.firstOrNull()?.let(HtmlVariables::notificationLine).orEmpty())
            if ("notificacoesLista" in used) {
                val hm = SimpleDateFormat("HH:mm", locale)
                put("notificacoesLista", HtmlVariables.notificationsJson(notificationItems) { hm.format(Date(it)) })
            }
            val position = music?.positionNow() ?: 0L
            put("musicaTitulo", music?.title.orEmpty())
            put("musicaArtista", music?.artist.orEmpty())
            put("musicaAlbum", music?.album.orEmpty())
            put("musicaApp", music?.appLabel.orEmpty())
            put("musicaTocando", if (music?.playing == true) "sim" else "")
            put("musicaPosicao", if (music != null) MediaWatcher.formatTime(position) else "")
            put("musicaDuracao", if (music != null && music.durationMs > 0) MediaWatcher.formatTime(music.durationMs) else "")
            put("musicaProgresso", if (music != null && music.durationMs > 0) (position * 100 / music.durationMs).toString() else "0")
            put("musicaCapa", music?.artDataUri.orEmpty())
            putAll(material)
            if ("appsLista" in used) put("appsLista", appsList)
            if ("appsRecentes" in used) put("appsRecentes", recentApps)
            put("appsTotal", appsCount.toString())
            if ("appsDock" in used) put("appsDock", dockApps)
            put("dockVertical", "")
            put("modoBusca", "")
            put("barraTopo", insetTop.toString())
            put("barraBase", insetBottom.toString())
            putAll(extra)
        }.let(HtmlVariables::withEnglish)
    }
}

/**
 * Widget HTML na área de trabalho. Gasta bateria só quando necessário:
 * - pausado (sem timers nem JavaScript rodando) sempre que a tela inicial sai de cena;
 * - no modo leve não há JavaScript, e a página só é redesenhada quando um valor muda;
 * - no modo interativo os dados novos chegam por `onLauncherData`, sem recarregar a página;
 * - sem acesso à internet, a menos que o widget peça.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWidgetView(
    widget: HtmlWidget,
    controller: LauncherController,
    palette: LauncherPalette,
    modifier: Modifier = Modifier,
    /** Recebe mensagens de diagnóstico (carregamento, tamanho, erros), para o editor. */
    onStatus: ((String) -> Unit)? = null,
    /** Valores a mais (ex.: modoBusca na lista de apps). */
    extra: Map<String, String> = emptyMap(),
    /** Todas as mensagens do console da página (log, aviso, erro), para o testador. */
    onConsole: ((level: String, message: String, line: Int) -> Unit)? = null,
    /** Onde o `localStorage` do widget é salvo: o id dele; nulo guarda só na memória (prévias). */
    storageId: String? = widget.id,
    /**
     * Falso: a página continua carregada (na memória) mas some, não recebe toques e fica
     * pausada. A lista de apps em HTML usa isso para abrir na hora, sem recarregar.
     */
    visible: Boolean = true,
) {
    val used = remember(widget.html) { HtmlVariables.used(widget.html) }
    val values = rememberHtmlValues(palette, used, controller, extra)
    // No modo interativo a página é montada uma vez; depois só os dados são enviados.
    val document = if (widget.interactive) {
        remember(widget.html, widget.interactive) { HtmlVariables.render(widget.html, values, interactive = true) }
    } else {
        remember(widget.html, values) { HtmlVariables.render(widget.html, values, interactive = false) }
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Último documento carregado (para só recarregar quando algo mudou).
    val loaded = remember { arrayOfNulls<String>(1) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, webView) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // pauseTimers também para setInterval/fetch em JS enquanto a tela inicial não aparece.
                Lifecycle.Event.ON_PAUSE -> view?.run { onPause(); pauseTimers() }
                Lifecycle.Event.ON_RESUME -> view?.run { onResume(); resumeTimers() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(webView, values) {
        if (widget.interactive) webView?.evaluateJavascript("window.__launcherUpdate && __launcherUpdate(${JSONObject(values as Map<*, *>)})", null)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // localStorage do widget, salvo pelo launcher (só funciona com JavaScript ligado).
                storageId?.let { addJavascriptInterface(dev.jesiel.launcher.data.WidgetStorage.Bridge(ctx, it), "__launcherStorage") }
                // Sem isso o WebView é medido pelo conteúdo; como o widget usa height:100%, a
                // página ficava com altura zero e nada aparecia.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                // Toque longo é do Launcher (menu do widget), não seleção de texto da página.
                isLongClickable = false
                isHapticFeedbackEnabled = false
                setOnLongClickListener { true }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        controller.handleWidgetLink(request.url.toString())

                    // Ícones dos apps: launcher-icon://<pacote>, respondidos sem rede.
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                        val url = request.url.toString()
                        if (!url.startsWith(HtmlVariables.ICON_SCHEME)) return null
                        val png = controller.appIconPng(url.removePrefix(HtmlVariables.ICON_SCHEME).substringBefore('?').trimEnd('/'))
                            ?: return android.webkit.WebResourceResponse("image/png", null, 404, "Not Found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                        return android.webkit.WebResourceResponse("image/png", null, java.io.ByteArrayInputStream(png)).apply {
                            responseHeaders = mapOf("Cache-Control" to "max-age=86400", "Access-Control-Allow-Origin" to "*")
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        onStatus?.invoke("Página carregada · ${view.width}×${view.height}px")
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame) onStatus?.invoke("Erro ao carregar: ${error.description}")
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        onConsole?.invoke(message.messageLevel().name.lowercase(), message.message(), message.lineNumber())
                        if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            onStatus?.invoke("Erro no script (linha ${message.lineNumber()}): ${message.message()}")
                        }
                        return true
                    }
                }
                addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                    if (r - l == 0 || b - t == 0) onStatus?.invoke("O widget está com tamanho zero")
                }
                webView = this
            }
        },
        update = { view ->
            val shown = if (visible) View.VISIBLE else View.INVISIBLE
            if (view.visibility != shown) {
                view.visibility = shown
                if (visible) view.onResume() else view.onPause()
            }
            val layer = if (widget.software) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE
            if (view.layerType != layer) view.setLayerType(layer, null)
            view.settings.javaScriptEnabled = widget.interactive
            view.settings.blockNetworkLoads = !widget.network
            if (loaded[0] != document) {
                loaded[0] = document
                if (widget.network) {
                    view.loadDataWithBaseURL("https://widget.launcher/", document, "text/html", "utf-8", null)
                } else {
                    // Sem internet: documento embutido em base64, sem origem de rede.
                    val encoded = android.util.Base64.encodeToString(document.toByteArray(), android.util.Base64.NO_PADDING)
                    view.loadData(encoded, "text/html; charset=utf-8", "base64")
                }
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}

/** Incrementa a cada [periodMs] enquanto estiver na composição. */
@Composable
private fun rememberTicker(periodMs: Long): Long {
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(periodMs) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            tick++
        }
    }
    return tick
}

/** Wi-Fi, Dados móveis, Ethernet ou Sem conexão, acompanhando mudanças de rede. */
@Composable
private fun rememberNetworkType(): String {
    val context = LocalContext.current
    var type by remember { mutableStateOf("") }
    DisposableEffect(context) {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        fun describe(caps: android.net.NetworkCapabilities?) = when {
            caps == null -> "Sem conexão"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Conectado"
        }
        type = runCatching { describe(cm?.getNetworkCapabilities(cm.activeNetwork)) }.getOrDefault("")
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { type = describe(caps) }
            override fun onLost(network: android.net.Network) { type = "Sem conexão" }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return type
}

/** Volume de mídia em %, atualizado quando muda. */
@Composable
private fun rememberMediaVolume(): Int {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(android.media.AudioManager::class.java) }
    fun read(): Int = runCatching {
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 / max
    }.getOrDefault(0)
    var volume by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, intent: android.content.Intent) { volume = read() }
        }
        // Ação oculta, mas enviada pelo sistema há muitas versões a cada mudança de volume.
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return volume
}

private fun batteryTemperature(context: android.content.Context): String {
    val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val tenths = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
    return if (tenths < 0) "" else "%.0f".format(tenths / 10f)
}
