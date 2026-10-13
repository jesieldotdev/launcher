package dev.jesiel.launcher.ui.common

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Leitor de matérias dentro do launcher, para não pular para outro app ao tocar numa notícia. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArticleViewer(url: String, onClose: () -> Unit, onOpenExternal: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var current by remember { mutableStateOf(url) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val colors = MaterialTheme.colorScheme

    androidx.activity.compose.BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
        ) {
            Icon(
                Icons.Default.Close, "Fechar", tint = colors.onBackground,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClose).padding(10.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(title.ifBlank { "Carregando…" }, color = colors.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(android.net.Uri.parse(current).host.orEmpty(), color = colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew, "Abrir no navegador", tint = colors.onBackground,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable { onOpenExternal(current) }.padding(10.dp),
            )
        }
        if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            current = url
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            title = view.title.orEmpty()
                            progress = 1f
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress / 100f
                        }

                        override fun onReceivedTitle(view: WebView, t: String?) {
                            title = t.orEmpty()
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
