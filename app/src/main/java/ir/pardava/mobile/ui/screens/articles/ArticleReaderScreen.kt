package ir.pardava.mobile.ui.screens.articles

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.data.dto.ArticleDetailDto
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.LoadingBox
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

/**
 * Article reader — the server returns the article HTML; we wrap it with the
 * site's own palette so typography/colors match pardava.ir exactly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    app: PardavaApp,
    key: String,
    isDark: Boolean,
    onBack: () -> Unit,
) {
    val vm: ArticleDetailViewModel = viewModel(factory = SimpleVmFactory(app.api) { ArticleDetailViewModel(it, key) })
    val state by vm.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(key) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.article_reader), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is ArticleDetailUiState.Loading -> LoadingBox()
                is ArticleDetailUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
                is ArticleDetailUiState.Ready -> ReaderWebView(article = s.article, isDark = isDark)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReaderWebView(article: ArticleDetailDto, isDark: Boolean) {
    var progress by remember { mutableIntStateOf(100) }

    if (progress in 1..99) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth().height(3.dp),
        )
    }

    val bg = if (isDark) "#0a0e17" else "#f4f6fa"
    val card = if (isDark) "#151d2e" else "#ffffff"
    val text = if (isDark) "#e8edf5" else "#1a2332"
    val dim = if (isDark) "#8b9cb5" else "#5a6d85"
    val accent = if (isDark) "#3b82f6" else "#2563eb"
    val title = article.title.orEmpty().replace("'", "\\'")
    val cover = article.coverUrl.orEmpty()

    val wrapped = """
        <!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          body{background:$bg;color:$text;font-family:system-ui,'Vazirmatn',sans-serif;margin:0;padding:0 0 40px;line-height:1.9}
          .hero{width:100%;max-height:220px;object-fit:cover;display:block}
          .wrap{padding:16px}
          h1{font-size:1.35rem;margin:14px 0 8px}
          .meta{color:$dim;font-size:.8rem;margin-bottom:14px}
          img{max-width:100%;height:auto;border-radius:10px}
          a{color:$accent}
          code,pre{background:$card;border-radius:8px;padding:2px 6px;overflow-x:auto;direction:ltr;text-align:left}
          pre{padding:12px}
          blockquote{border-right:3px solid $accent;margin:0;padding:2px 12px;color:$dim}
          table{width:100%;border-collapse:collapse}td,th{border:1px solid $dim;padding:6px}
        </style></head><body>
        ${if (cover.isNotBlank()) "<img class='hero' src='$cover' alt=''>" else ""}
        <div class="wrap">
          <h1>$title</h1>
          ${if (article.createdAt != null) "<div class='meta'>${article.createdAt.take(10)}</div>" else ""}
          ${article.html.orEmpty()}
        </div>
      </body></html>
    """.trimIndent()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(if (isDark) 0xFF0A0E17.toInt() else 0xFFF4F6FA.toInt())
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress = newProgress
                    }
                }
                loadDataWithBaseURL(article.url ?: "https://pardava.ir/", wrapped, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
