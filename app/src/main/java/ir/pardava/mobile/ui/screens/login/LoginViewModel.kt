package ir.pardava.mobile.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.OtpRequestIn
import ir.pardava.mobile.data.dto.OtpVerifyIn
import ir.pardava.mobile.data.dto.OtpRequestOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object PhoneEntry : LoginState
    data class CodeEntry(val phone: String, val devCode: String?, val expiresIn: Int) : LoginState
    data object Submitting : LoginState
    data class Failure(val message: String) : LoginState
}

class LoginViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.PhoneEntry)
    val state: StateFlow<LoginState> = _state

    fun requestOtp(phone: String, onInvalidPhone: () -> Unit) {
        val normalized = phone.trim()
        if (!normalized.any { it.isDigit() } || normalized.length < 8) {
            onInvalidPhone()
            return
        }
        _state.value = LoginState.Submitting
        viewModelScope.launch {
            try {
                val out: OtpRequestOut = client.api().otpRequest(OtpRequestIn(normalized))
                _state.value = LoginState.CodeEntry(normalized, out.dev_code, out.expires_in)
            } catch (e: Exception) {
                _state.value = LoginState.Failure(e.message ?: "network error")
            }
        }
    }

    fun verifyOtp(code: String, lang: String, onSuccess: () -> Unit) {
        val current = _state.value as? LoginState.CodeEntry ?: return
        _state.value = LoginState.Submitting
        viewModelScope.launch {
            try {
                val tokens = client.api().otpVerify(OtpVerifyIn(current.phone, code.trim(), device = "android"))
                client.session.save(tokens.access_token, tokens.refresh_token, tokens.user.id)
                onSuccess()
            } catch (e: ir.pardava.mobile.core.ApiException) {
                _state.value = LoginState.Failure(e.error.message(lang))
            } catch (e: Exception) {
                _state.value = LoginState.Failure(e.message ?: "network error")
            }
        }
    }

    fun googleSignIn(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _state.value = LoginState.Submitting
        viewModelScope.launch {
            try {
                val tokens = client.api().googleLogin(ir.pardava.mobile.data.dto.GoogleLoginIn(idToken, device = "android"))
                client.session.save(tokens.access_token, tokens.refresh_token, tokens.user.id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "google sign-in failed")
            }
        }
    }

    fun backToPhone() {
        _state.value = LoginState.PhoneEntry
    }
}
