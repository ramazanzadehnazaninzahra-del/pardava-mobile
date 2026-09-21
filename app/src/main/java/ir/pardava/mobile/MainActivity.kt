package ir.pardava.mobile

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import ir.pardava.mobile.ui.nav.PardavaNav
import ir.pardava.mobile.ui.theme.PardavaTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PardavaApp
        setContent {
            PardavaTheme {
                PardavaNav(app = app)
            }
        }
    }
}
