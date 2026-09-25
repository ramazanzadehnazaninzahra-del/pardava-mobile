package ir.pardava.mobile.ui.screens.web

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ir.pardava.mobile.R

/**
 * In-app browser (WebView) — how every pardava.ir service reaches mobile.
 * Session cookies are shared so the user stays signed in on the web tools;
 * pardava.ir links stay in-app, everything else opens in the browser app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(
    url: String,
    title: String,
    isDark: Boolean,
    onBack: () -> Unit,
) {
    var progress by remember { mutableIntStateOf(0) }
    var webCanGoBack by remember { mutableStateOf(false) }
    var goBackSignal by remember { mutableIntStateOf(0) }
    var webTitle by remember { mutableStateOf(title) }

    BackHandler(enabled = webCanGoBack) { goBackSignal++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(webTitle, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            WebViewLoader(
                url = url,
                isDark = isDark,
                onProgress = { progress = it },
                onTitle = { webTitle = it },
                onBackState = { webCanGoBack = it },
                goBackSignal = goBackSignal,
                onExternalBack = onBack,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewLoader(
    url: String,
    isDark: Boolean,
    onProgress: (Int) -> Unit,
    onTitle: (String) -> Unit,
    onBackState: (Boolean) -> Unit,
    goBackSignal: Int,
    onExternalBack: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Pop one history entry per back signal; leave the screen when history is empty.
    androidx.compose.runtime.LaunchedEffect(goBackSignal) {
        if (goBackSignal > 0) {
            val wv = webView
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
                onBackState(true)
            } else {
                onExternalBack()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.mediaPlaybackRequiresUserGesture = true
                setBackgroundColor(if (isDark) 0xFF0A0E17.toInt() else 0xFFF4F6FA.toInt())
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                        val uri = req.url
                        val host = uri.host ?: return false
                        val inApp = host == "pardava.ir" || host.endsWith(".pardava.ir")
                        if (inApp) return false
                        return try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        } catch (_: Exception) {
                            true
                        }
                    }

                    override fun onPageFinished(view: WebView, u: String?) {
                        onBackState(view.canGoBack())
                        val t = view.title?.trim().orEmpty()
                        if (t.isNotEmpty() && t != "about:blank") onTitle(t.take(42))
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }
                }
                webView = this
                loadUrl(url)
            }
        },
        update = { /* url is fixed per screen instance */ },
        modifier = Modifier.fillMaxSize(),
    )
}
