package ir.pardava.mobile.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pardava.mobile.BuildConfig
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.FontScale
import ir.pardava.mobile.core.ThemeMode
import ir.pardava.mobile.ui.components.LanguageSwitchRow
import ir.pardava.mobile.ui.components.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * User-facing settings only: language (fa default / en), theme (system / light /
 * dark), font size (4 steps) and a short about card. No technical/server fields —
 * this app talks to the official Pardava service automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: PardavaApp, onBack: () -> Unit, onOpenSite: () -> Unit) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var checkingBusy by remember { mutableStateOf(false) }
    var updateFound by remember { mutableStateOf<ir.pardava.mobile.data.dto.LatestVersionDto?>(null) }
    var upToDate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ---- update check (in-app updates, served by pardava.ir) ----
            SettingsSection(title = stringResource(R.string.update_section)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            checkingBusy = true
                            scope.launch(Dispatchers.Main) {
                                try {
                                    val latest = ir.pardava.mobile.core.UpdateManager.check(app.api)
                                    if (latest != null && ir.pardava.mobile.core.UpdateManager.isNewer(
                                            latest, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME,
                                        )
                                    ) {
                                        updateFound = latest
                                    } else {
                                        upToDate = true
                                    }
                                } finally {
                                    checkingBusy = false
                                }
                            }
                        },
                        enabled = !checkingBusy,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (checkingBusy) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.update_check))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- language ----
            SettingsSection(title = stringResource(R.string.settings_language)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    LanguageSwitchRow(app)
                }
                Text(
                    stringResource(R.string.settings_language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---- theme ----
            SettingsSection(title = stringResource(R.string.settings_theme)) {
                val modes = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                )
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { app.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- font size ----
            SettingsSection(title = stringResource(R.string.settings_font)) {
                val sizes = listOf(
                    FontScale.SMALL to stringResource(R.string.font_small),
                    FontScale.NORMAL to stringResource(R.string.font_normal),
                    FontScale.LARGE to stringResource(R.string.font_large),
                    FontScale.XLARGE to stringResource(R.string.font_xlarge),
                )
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    sizes.forEachIndexed { index, (scale, label) ->
                        SegmentedButton(
                            selected = settings.fontScale == scale,
                            onClick = { app.setFontScale(scale) },
                            shape = SegmentedButtonDefaults.itemShape(index, sizes.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- about: no marketing copy — “about us” lives on the website ----
            SettingsSection(title = stringResource(R.string.settings_about)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenSite() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.settings_about_open),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                stringResource(R.string.settings_about_site),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ---- update result dialogs (in-app download + auto install) ----
        updateFound?.let { latest ->
            ir.pardava.mobile.ui.components.UpdateDialog(
                app = app,
                latest = latest,
                onDismiss = { updateFound = null },
            )
        }
        if (upToDate && updateFound == null) {
            AlertDialog(
                onDismissRequest = { upToDate = false },
                title = { Text(stringResource(R.string.update_uptodate_title)) },
                text = { Text(stringResource(R.string.update_uptodate_body)) },
                confirmButton = {
                    TextButton(onClick = { upToDate = false }) { Text(stringResource(R.string.ok)) }
                },
            )
        }
    }
}
