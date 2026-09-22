package ir.pardava.mobile.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.ApiException
import ir.pardava.mobile.data.dto.OtpRequestIn
import ir.pardava.mobile.data.dto.OtpVerifyIn
import ir.pardava.mobile.data.dto.LoginIn
import ir.pardava.mobile.data.dto.GoogleLoginIn
import ir.pardava.mobile.data.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Phone normalizer: Persian/Arabic digits → Latin, keeps 09xxxxxxxxx / +98… */
object Phone {
    fun normalize(raw: String): String {
        val sb = StringBuilder()
        for (c in raw) {
            when (c) {
                in '۰'..'۹' -> sb.append('0' + (c - '۰'))
                in '٠'..'٩' -> sb.append('0' + (c - '٠'))
                else -> sb.append(c)
            }
        }
        var s = sb.toString().replace(" ", "").replace("-", "")
        if (s.startsWith("+98")) s = "0" + s.removePrefix("+98")
        if (s.startsWith("0098")) s = "0" + s.removePrefix("0098")
        if (s.startsWith("98") && s.length == 12) s = "0" + s.removePrefix("98")
        return s
    }

    fun isValidIranianMobile(raw: String): Boolean =
        Regex("^09\\d{9}$").matches(normalize(raw))
}

class LoginViewModel(private val client: ApiClient) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    /** Set after a successful otp/request; drives the verify step + countdown. */
    private val _otpSent = MutableStateFlow<String?>(null)
    val otpSent: StateFlow<String?> = _otpSent

    fun consumeError() { _error.value = null }
    fun consumeInfo() { _info.value = null }

    /** Back to the phone step (e.g. «اصلاح شماره»). */
    fun resetOtp() { _otpSent.value = null }

    /** Surface client-side failures (Google flow) in the same error dialog. */
    fun showError(msg: String) { _error.value = msg }

    fun requestOtp(phoneRaw: String) {
        if (!Phone.isValidIranianMobile(phoneRaw)) {
            _error.value = "لطفاً یک شمارهٔ موبایل معتبر وارد کنید."
            return
        }
        val phone = Phone.normalize(phoneRaw)
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val res = client.call { client.api.otpRequest(OtpRequestIn(phone)) }
                _otpSent.value = phone
                _info.value = "کد تأیید به $phone ارسال شد" + (res.expiresIn?.let { " (اعتبار $it ثانیه)" } ?: "") + "."
            } catch (e: ApiException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "خطا"
            } finally {
                _busy.value = false
            }
        }
    }

    fun verifyOtp(phoneRaw: String, code: String, onSignedIn: (UserDto?) -> Unit) {
        val phone = Phone.normalize(phoneRaw)
        val clean = code.trim()
        if (!Regex("^\\d{4,8}$").matches(clean)) {
            _error.value = "کد تأیید را وارد کنید."
            return
        }
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val res = client.call { client.api.otpVerify(OtpVerifyIn(phone, clean)) }
                finishLogin(res.token, res.user, onSignedIn)
            } catch (e: ApiException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "خطا"
            } finally {
                _busy.value = false
            }
        }
    }

    fun loginWithPassword(username: String, password: String, onSignedIn: (UserDto?) -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            _error.value = "نام کاربری و رمز عبور را وارد کنید."
            return
        }
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val res = client.call { client.api.login(LoginIn(username.trim(), password)) }
                finishLogin(res.token, res.user, onSignedIn)
            } catch (e: ApiException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "خطا"
            } finally {
                _busy.value = false
            }
        }
    }

    /** Exchanges a Google ID token (from Credential Manager) for a session. */
    fun loginWithGoogle(idToken: String, onSignedIn: (UserDto?) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val res = client.call { client.api.googleLogin(GoogleLoginIn(idToken)) }
                finishLogin(res.token, res.user, onSignedIn)
            } catch (e: ApiException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "خطا"
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun finishLogin(token: String?, user: UserDto?, onSignedIn: (UserDto?) -> Unit) {
        if (token.isNullOrBlank()) {
            _error.value = "پاسخ ورود نامعتبر بود."
            return
        }
        client.session.saveSession(token, user)
        onSignedIn(user)
    }
}
