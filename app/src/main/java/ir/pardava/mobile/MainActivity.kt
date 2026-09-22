package ir.pardava.mobile

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
        val app = application as PardavaApp
        setContent {
            val settings by app.settings.collectAsStateWithLifecycle()
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
                PardavaNav(app = app)
            }
        }
    }
}
