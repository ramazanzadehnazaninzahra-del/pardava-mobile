package ir.pardava.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import ir.pardava.mobile.ui.nav.PardavaNav
import ir.pardava.mobile.ui.theme.PardavaTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PardavaApp
        handleDeepLink(intent)
        setContent {
            PardavaTheme {
                PardavaNav(app = app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /** Google sign-in deep link: ir.pardava.mobile://oauth?code=…|error=… */
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "ir.pardava.mobile" || data.host != "oauth") return
        val app = application as PardavaApp
        val code = data.getQueryParameter("code")
        val error = data.getQueryParameter("error")
        when {
            code != null -> app.googleLinkCode.value = code
            error != null -> app.googleLinkError.value = error
        }
    }
}
