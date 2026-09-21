package ir.pardava.mobile.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.MessageOut
import ir.pardava.mobile.data.dto.OtpRequestIn
import ir.pardava.mobile.data.dto.OtpVerifyIn
import ir.pardava.mobile.data.dto.TokenOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One login screen, four methods: password · OTP · Gmail · app token. */
class LoginViewModel(private val client: ApiClient) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Set after POST /auth/otp/request succeeded. */
    private val _otpSentTo = MutableStateFlow<String?>(null)
    val otpSentTo: StateFlow<String?> = _otpSentTo

    fun consumeError() {
        _error.value = null
    }

    private fun <T> run(block: suspend () -> T, onSuccess: (T) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                onSuccess(block())
            } catch (e: Exception) {
                _error.value = e.message ?: "error"
            } finally {
                _busy.value = false
            }
        }
    }

    fun loginWithPassword(username: String, password: String, onDone: () -> Unit) {
        run({
            val out: TokenOut = apiCall { client.api().login(ir.pardava.mobile.data.dto.LoginIn(username, password)) }
            client.saveLogin(out.token, out.user)
            out
        }, { onDone() })
    }

    fun requestOtp(phone: String) {
        run({
            val out: MessageOut = apiCall { client.api().otpRequest(OtpRequestIn(phone)) }
            out
        }, { _otpSentTo.value = phone })
    }

    fun verifyOtp(phone: String, code: String, onDone: () -> Unit) {
        run({
            val out: TokenOut = apiCall { client.api().otpVerify(OtpVerifyIn(phone, code)) }
            client.saveLogin(out.token, out.user)
            out
        }, { onDone() })
    }

    /** Exchange a Google ID token for a Pardava session token. */
    fun loginWithGoogleIdToken(idToken: String, onDone: () -> Unit) {
        run({
            val out: TokenOut = apiCall { client.api().googleLogin(ir.pardava.mobile.data.dto.GoogleIn(idToken)) }
            client.saveLogin(out.token, out.user)
            out
        }, { onDone() })
    }

    /** Validate a token pasted from the website profile page. */
    fun loginWithToken(raw: String, onDone: () -> Unit) {
        run({
            client.signInWithToken(raw)
        }, { onDone() })
    }

    fun resetOtp() {
        _otpSentTo.value = null
    }

    /** Surface a Google sign-in failure as a regular inline error. */
    fun reportGoogleError(message: String) {
        _error.value = message
    }
}
