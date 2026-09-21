package ir.pardava.mobile.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.ui.components.LanguageSwitchRow

/**
 * OTP login (phone → code). Google Sign-In button is shown but disabled until a
 * real `google-services.json` / web client id is configured (Firebase step).
 */
@Composable
fun LoginScreen(app: PardavaApp, onSignedIn: () -> Unit) {
    val lang = app.currentLanguage()
    val vm: LoginViewModel = viewModel(factory = LoginViewModelFactory(app.api))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var phoneError by remember { mutableStateOf(false) }
    var googleMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        if (state is LoginState.CodeEntry) code = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        when (val s = state) {
            is LoginState.PhoneEntry, is LoginState.Submitting, is LoginState.Failure -> {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; phoneError = false },
                    label = { Text(stringResource(R.string.phone_label)) },
                    placeholder = { Text(stringResource(R.string.phone_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phoneError,
                    supportingText = if (phoneError) {
                        { Text(stringResource(R.string.invalid_phone)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.requestOtp(phone) { phoneError = true } },
                    enabled = state !is LoginState.Submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state is LoginState.Submitting) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.request_code))
                    }
                }
            }

            is LoginState.CodeEntry -> {
                Text(
                    stringResource(R.string.code_sent_to, Fmt.digits(s.phone, lang), s.expiresIn / 60),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (s.devCode != null) {
                    Text(
                        stringResource(R.string.dev_code_hint, Fmt.digits(s.devCode, lang)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(6) },
                    label = { Text(stringResource(R.string.code_label)) },
                    placeholder = { Text(stringResource(R.string.code_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.verifyOtp(code, lang) { onSignedIn() } },
                    enabled = code.length >= 4 && state !is LoginState.Submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.verify_code))
                }
                TextButton(onClick = { vm.backToPhone() }) {
                    Text(stringResource(R.string.edit_number))
                }
            }
        }

        if (state is LoginState.Failure) {
            Spacer(Modifier.height(8.dp))
            Text(
                (state as LoginState.Failure).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (googleMsg != null) {
            Text(
                googleMsg!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { googleMsg = context.getString(R.string.google_not_configured) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.google_sign_in))
        }
        Spacer(Modifier.height(16.dp))
        LanguageSwitchRow(app = app)
    }
}
