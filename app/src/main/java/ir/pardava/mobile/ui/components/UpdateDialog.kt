package ir.pardava.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.core.UpdateManager
import ir.pardava.mobile.data.dto.LatestVersionDto
import kotlinx.coroutines.launch

/**
 * In-app update dialog: shows what's new, downloads the APK INSIDE the app
 * with a live progress bar, then opens the system installer automatically
 * (no browser, no external downloader). Reused by the startup check and the
 * settings screen so both share the exact same flow.
 */
@Composable
fun UpdateDialog(
    app: PardavaApp,
    latest: LatestVersionDto,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    val lang = app.currentLanguage()
    val fallbackError = stringResource(R.string.update_failed)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.update_title, latest.versionName ?: "")) },
        text = {
            Column {
                latest.whatsNew?.takeIf { it.isNotBlank() }?.let {
                    Text(it)
                    Spacer(Modifier.height(10.dp))
                }
                if (busy) {
                    if (progress >= 0) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            Fmt.digits("%${progress}", lang),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val apkUrl = latest.apkUrl ?: return@TextButton
                    busy = true
                    error = null
                    progress = -1
                    scope.launch {
                        val result = UpdateManager.downloadAndInstall(
                            context,
                            app.api,
                            apkUrl,
                            latest.versionName,
                        ) { p -> progress = p }
                        when (result) {
                            is UpdateManager.InstallResult.Started -> onDismiss()
                            is UpdateManager.InstallResult.NeedPermission -> {
                                busy = false
                                error = null
                            }
                            is UpdateManager.InstallResult.Failed -> {
                                busy = false
                                error = result.message ?: fallbackError
                            }
                        }
                    }
                },
            ) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}
