package ir.pardava.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pardava.mobile.core.ThemeMode
import ir.pardava.mobile.core.FontScale
import ir.pardava.mobile.ui.nav.PardavaNav
import ir.pardava.mobile.ui.theme.PardavaTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        setContent {
            val settings by (application as PardavaApp).settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            // Status-bar icons follow the active theme instantly.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                )
            }
            PardavaTheme(
                darkTheme = darkTheme,
                fontScale = FontScale.factor(settings.fontScale),
            ) {
                PardavaNav(app = application as PardavaApp)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * pardava://auth/callback?code=… — the Google web-bridge return path.
     * The code is parked on the Application object; the nav host exchanges it.
     */
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "pardava" && data.host == "auth") {
            val code = data.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                (application as PardavaApp).postAuthCode(code)
            }
        }
    }
}
