package ir.pardava.mobile.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.ui.components.LanguageSwitchRow
import ir.pardava.mobile.ui.components.ServerSettingsSection
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(app: PardavaApp, onGoLogin: () -> Unit) {
    val lang = app.currentLanguage()
    val signedIn by app.session.signedIn.collectAsState()
    val scope = rememberCoroutineScope()
    var loggingOut by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleLarge)
            LanguageSwitchRow(app = app)
        }

        if (!signedIn) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.profile_not_signed_in),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.profile_sign_in_cta),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoLogin) { Text(stringResource(R.string.go_login)) }
                }
            }
        } else {
            val vm: ProfileViewModel = viewModel(factory = SimpleVmFactory(app.api) { ProfileViewModel(it) })
            val state by vm.state.collectAsState()

            LaunchedEffect(signedIn) { if (signedIn) vm.load() }

            when (val s = state) {
                is ProfileUiState.Loading -> androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is ProfileUiState.Failure -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                    }
                }

                is ProfileUiState.Ready -> {
                    val user = s.me.user
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                user?.displayName?.ifBlank { stringResource(R.string.league_anonymous) } ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!user?.email.isNullOrBlank()) {
                                Text(
                                    user?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (!user?.username.isNullOrBlank()) {
                                Text(
                                    "@${user?.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column {
                                    Text(
                                        Fmt.int(s.me.points, lang),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        stringResource(R.string.profile_points),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column {
                                    Text(
                                        Fmt.int(s.me.enrollments, lang),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        stringResource(R.string.profile_enrollments),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    loggingOut = true
                    scope.launch {
                        app.api.logout()
                        loggingOut = false
                    }
                },
                enabled = !loggingOut,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.logout)) }
        }

        HorizontalDivider()

        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.server_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            ServerSettingsSection(app = app)
        }
    }
}

