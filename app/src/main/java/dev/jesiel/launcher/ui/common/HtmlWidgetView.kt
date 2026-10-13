package dev.jesiel.launcher.ui.common

import android.annotation.SuppressLint
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
fun rememberHtmlValues(palette: LauncherPalette, used: Set<String>): Map<String, String> {
    val context = LocalContext.current
    val now = rememberNow()
    val locale = remember { Locale.getDefault() }
    val battery = if ("bateria" in used || "carregando" in used) rememberBatteryState() else null
    val memory = if ("memoria" in used || "armazenamento" in used) rememberMemoryState() else null
    return remember(now, battery, memory, palette, used) {
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
        }
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
) {
    val used = remember(widget.html) { HtmlVariables.used(widget.html) }
    val values = rememberHtmlValues(palette, used)
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
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                Lifecycle.Event.ON_RESUME -> view?.onResume()
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
                // Sem isso o WebView é medido pelo conteúdo; como o widget usa height:100%, a
                // página ficava com altura zero e nada aparecia.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        controller.handleWidgetLink(request.url.toString())

                    override fun onPageFinished(view: WebView, url: String?) {
                        onStatus?.invoke("Página carregada · ${view.width}×${view.height}px")
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame) onStatus?.invoke("Erro ao carregar: ${error.description}")
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
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
