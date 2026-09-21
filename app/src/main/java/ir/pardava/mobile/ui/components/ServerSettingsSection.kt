package ir.pardava.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import kotlinx.coroutines.launch

/**
 * Server address editor (login screen + profile). The stored value is the
 * courses API prefix, e.g. `https://pardava.ir/api/courses`.
 */
@Composable
fun ServerSettingsSection(app: PardavaApp, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var url by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!loaded) {
        url = app.session.baseUrl.trimEnd('/')
        loaded = true
    }

    Column(Modifier.fillMaxWidth()) {
        if (!expanded) {
            OutlinedButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.server_settings))
            }
        } else {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    invalid = false
                    saved = false
                },
                label = { Text(stringResource(R.string.server_url_label)) },
                singleLine = true,
                isError = invalid,
                supportingText = {
                    val text = when {
                        invalid -> stringResource(R.string.server_url_invalid)
                        saved -> stringResource(R.string.server_url_saved)
                        else -> null
                    }
                    text?.let { Text(it) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        val trimmed = url.trim()
                        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                            invalid = true
                        } else {
                            scope.launch {
                                app.session.setBaseUrl(trimmed)
                                saved = true
                            }
                        }
                    },
                ) { Text(stringResource(R.string.server_url_save)) }
            }
        }
        Text(
            stringResource(R.string.server_url_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
