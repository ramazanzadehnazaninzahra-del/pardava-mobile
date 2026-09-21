package ir.pardava.mobile.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.BuildConfigDefault
import ir.pardava.mobile.ui.components.ServerSettingsSection
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory
import kotlinx.coroutines.launch

private enum class LoginTab { PASSWORD, OTP, GOOGLE, TOKEN }

@Composable
fun LoginScreen(
    app: PardavaApp,
    onSignedIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vm: LoginViewModel = viewModel(factory = SimpleVmFactory(app.api) { LoginViewModel(it) })
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val otpSentTo by vm.otpSentTo.collectAsState()
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(LoginTab.PASSWORD.ordinal) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var googleHelp by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column {
                Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val tabs = listOf(
            stringResource(R.string.login_tab_password),
            stringResource(R.string.login_tab_otp),
            stringResource(R.string.login_tab_google),
            stringResource(R.string.login_tab_token),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = tab == index,
                    onClick = { tab = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                ) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }

        error?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        when (LoginTab.entries[tab]) {
            LoginTab.PASSWORD -> {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.loginWithPassword(username.trim(), password) { onSignedIn() } },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.login_button))
                }
            }

            LoginTab.OTP -> {
                val sent = otpSentTo
                if (sent == null) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.phone_label)) },
                        placeholder = { Text(stringResource(R.string.phone_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { vm.requestOtp(phone.trim()) },
                        enabled = !busy && phone.trim().length >= 10,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.request_code))
                    }
                } else {
                    Text(
                        stringResource(R.string.code_sent_to, sent, 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringResource(R.string.code_label)) },
                        placeholder = { Text(stringResource(R.string.code_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { vm.verifyOtp(sent, code.trim()) { onSignedIn() } },
                        enabled = !busy && code.trim().length >= 4,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.verify_code))
                    }
                    TextButton(onClick = { vm.resetOtp() }) { Text(stringResource(R.string.edit_number)) }
                }
            }

            LoginTab.GOOGLE -> {
                val clientId = BuildConfigDefault.googleClientId
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.google_sign_in),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (clientId.isBlank()) {
                            Text(
                                stringResource(R.string.google_needs_setup),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { googleHelp = true }) {
                                    Text(stringResource(R.string.google_help_button))
                                }
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    signInWithGoogle(app, context, vm, onSignedIn)
                                }
                            },
                            enabled = !busy && clientId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.google_sign_in))
                        }
                        Text(
                            stringResource(R.string.google_token_fallback),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            LoginTab.TOKEN -> {
                Text(
                    stringResource(R.string.token_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.token_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.loginWithToken(token) { onSignedIn() } },
                    enabled = !busy && token.trim().length > 10,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.token_validate))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        ServerSettingsSection(app = app)
    }

    if (googleHelp) {
        GoogleHelpDialog(onDismiss = { googleHelp = false })
    }
}

/** Credential Manager flow → Google ID token → POST /auth/google. */
private suspend fun signInWithGoogle(
    app: PardavaApp,
    context: android.content.Context,
    vm: LoginViewModel,
    onSignedIn: () -> Unit,
) {
    val clientId = BuildConfigDefault.googleClientId
    if (clientId.isBlank()) return
    try {
        val manager = CredentialManager.create(context)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response: GetCredentialResponse = manager.getCredential(context, request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleId = GoogleIdTokenCredential.createFrom(credential.data)
            vm.loginWithGoogleIdToken(googleId.idToken) { onSignedIn() }
        }
    } catch (e: NoCredentialException) {
        vm.reportGoogleError(context.getString(R.string.google_no_account))
    } catch (e: GetCredentialException) {
        vm.reportGoogleError(e.message ?: context.getString(R.string.google_signin_failed))
    } catch (e: Exception) {
        vm.reportGoogleError(e.message ?: context.getString(R.string.google_signin_failed))
    }
}

@Composable
private fun GoogleHelpDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.google_help_title)) },
        text = { Text(stringResource(R.string.google_help_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}
