package ir.pardava.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * Settings: language (fa default / en), theme (system / light / dark),
 * font size (4 steps), site address (runtime-switchable API root) and about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: PardavaApp, onBack: () -> Unit) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var serverUrl by rememberSaveable { mutableStateOf(app.session.baseUrl) }
    var saving by remember { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.server_url_saved)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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

            // ---- server ----
            SettingsSection(title = stringResource(R.string.server_settings)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.server_url_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Button(
                            onClick = {
                                saving = true
                                scope.launch(Dispatchers.Main) {
                                    try {
                                        app.session.setBaseUrl(serverUrl)
                                        serverUrl = app.session.baseUrl
                                        snackbar.showSnackbar(savedMsg)
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(e.message ?: "")
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.server_url_save))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.Main) {
                                    app.session.setBaseUrl(ir.pardava.mobile.core.BuildConfigDefault.url)
                                    serverUrl = app.session.baseUrl
                                    snackbar.showSnackbar(savedMsg)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(stringResource(R.string.server_url_reset)) }
                    }
                    Text(
                        stringResource(R.string.server_url_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- about ----
            SettingsSection(title = stringResource(R.string.settings_about)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        stringResource(R.string.settings_about_app, stringResource(R.string.app_name)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.settings_about_site),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
