package ir.pardava.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import ir.pardava.mobile.PardavaApp

/** In-app fa/en switcher; persists via AppCompat per-app locales + server preference. */
@Composable
fun LanguageSwitchRow(app: PardavaApp) {
    val current = app.currentLanguage()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current == "fa",
            onClick = { if (current != "fa") app.setAppLanguage("fa") },
            label = { Text("فارسی") },
        )
        FilterChip(
            selected = current == "en",
            onClick = { if (current != "en") app.setAppLanguage("en") },
            label = { Text("English") },
        )
    }
}
