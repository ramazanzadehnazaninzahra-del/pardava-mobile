package ir.pardava.mobile.ui.screens.certificate

import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R

/**
 * Printable certificate viewer: loads the site certificate page (session or
 * Bearer header via WebView headers) and offers system print → PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateScreen(
    app: PardavaApp,
    slug: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loaded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cert_title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = loaded,
                        onClick = {
                            val webView = webViewRef ?: return@IconButton
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                val printManager = context.getSystemService(PrintManager::class.java) ?: return@IconButton
                                val adapter = webView.createPrintDocumentAdapter("pardava-certificate")
                                printManager.print("pardava-certificate-$slug", adapter, PrintAttributes.Builder().build())
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = stringResource(R.string.cert_print))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                loaded = true
                            }
                        }
                        val headers = HashMap<String, String>()
                        app.session.token?.let { headers["Authorization"] = "Bearer $it" }
                        loadUrl(app.siteUrl("courses/$slug/certificate"), headers)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!loaded) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}
