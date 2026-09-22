package ir.pardava.mobile.ui.screens.login

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.BuildConfigDefault
import ir.pardava.mobile.ui.components.BrandBanner
import ir.pardava.mobile.ui.components.LanguageSwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sign-in screen with three methods matching the server contract:
 * 1) Iranian phone OTP (primary), 2) username/password, 3) Google (Credential
 * Manager — active once a client id is configured; otherwise an honest notice).
 */
@Composable
fun LoginScreen(app: PardavaApp, onSignedIn: () -> Unit) {
    val vm: LoginViewModel = viewModel(factory = LoginViewModelFactory(app.api))
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val otpSent by vm.otpSent.collectAsStateWithLifecycle()

    var method by rememberSaveable { mutableIntStateOf(0) } // 0 = OTP, 1 = password
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(info) { info?.let { vm.consumeInfo() } }
    LaunchedEffect(otpSent, countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    // Errors surface as a dialog (server messages are already localized).
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.consumeError() },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { vm.consumeError() }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ---- brand ----
        BrandBanner(height = 148.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                )
            }
        }

        Column(Modifier.padding(20.dp)) {
            // ---- method switch ----
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = method == 0,
                    onClick = { method = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.login_method_phone)) }
                SegmentedButton(
                    selected = method == 1,
                    onClick = { method = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.login_method_password)) }
            }
            Spacer(Modifier.height(18.dp))

            if (method == 0) {
                // ---- OTP flow ----
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone_label)) },
                    placeholder = { Text(stringResource(R.string.phone_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    enabled = otpSent == null && !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                if (otpSent == null) {
                    Button(
                        onClick = {
                            vm.requestOtp(phone)
                            countdown = 60
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.request_code))
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringResource(R.string.code_label)) },
                        placeholder = { Text(stringResource(R.string.code_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.verifyOtp(phone, code) { onSignedIn() } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.verify_code))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { vm.resetOtp(); code = "" }) {
                            Text(stringResource(R.string.edit_number))
                        }
                        if (countdown > 0) {
                            Text(
                                stringResource(R.string.resend_in, countdown),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        } else {
                            TextButton(onClick = { vm.requestOtp(phone); countdown = 60 }) {
                                Text(stringResource(R.string.resend_code))
                            }
                        }
                    }
                }
            } else {
                // ---- username / password flow ----
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username_label)) },
                    leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.loginWithPassword(username, password) { onSignedIn() } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.login_password_cta))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            GoogleRow(app, vm, busy, onSignedIn)

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.login_guest_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                LanguageSwitchRow(app)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GoogleRow(app: PardavaApp, vm: LoginViewModel, busy: Boolean, onSignedIn: () -> Unit) {
    val context = LocalContext.current
    var showNotice by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            if (BuildConfigDefault.googleClientId.isBlank()) {
                showNotice = true
            } else if (context is Activity) {
                googleSignIn(
                    context,
                    BuildConfigDefault.googleClientId,
                    onToken = { idToken -> vm.loginWithGoogle(idToken) { onSignedIn() } },
                    onError = { msg -> vm.showError(msg) },
                )
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Filled.AlternateEmail, contentDescription = null, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.google_sign_in))
    }

    if (showNotice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNotice = false },
            title = { Text(stringResource(R.string.google_sign_in)) },
            text = { Text(stringResource(R.string.google_needs_config)) },
            confirmButton = {
                TextButton(onClick = { showNotice = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

/** Credential Manager sign-in; requires a configured web client id. */
private fun googleSignIn(
    activity: Activity,
    clientId: String,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val manager = CredentialManager.create(activity)
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(false)
                .build(),
        )
        .build()
    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
        try {
            val result = manager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                onToken(googleCred.idToken)
            } else {
                onError("اعتبارنامهٔ گوگل شناسایی نشد.")
            }
        } catch (e: GetCredentialException) {
            onError("ورود با گوگل انجام نشد: ${e.message ?: "خطای نامشخص"}")
        }
    }
}
