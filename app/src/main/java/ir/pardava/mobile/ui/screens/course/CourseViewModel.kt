package ir.pardava.mobile.ui.screens.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.ApiException
import ir.pardava.mobile.data.dto.CourseDetailResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CourseUiState {
    data object Loading : CourseUiState
    data class Ready(val detail: CourseDetailResponse) : CourseUiState
    data class Failure(val message: String) : CourseUiState
}

sealed interface CourseAction {
    data class Message(val text: String, val isError: Boolean = false) : CourseAction
    data class NeedLogin(val message: String) : CourseAction
}

class CourseViewModel(
    private val client: ApiClient,
    private val slug: String,
) : ViewModel() {

    private val _state = MutableStateFlow<CourseUiState>(CourseUiState.Loading)
    val state: StateFlow<CourseUiState> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _action = MutableStateFlow<CourseAction?>(null)
    val action: StateFlow<CourseAction?> = _action

    fun consumeAction() { _action.value = null }

    fun load() {
        if (slug.isEmpty()) return
        _state.value = CourseUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = CourseUiState.Ready(client.call { client.api.course(slug) })
            } catch (e: Exception) {
                _state.value = CourseUiState.Failure(e.message ?: "error")
            }
        }
    }

    /** Free course enrollment. Guests are routed to the login screen. */
    fun enroll(signedIn: Boolean) {
        if (!signedIn) {
            _action.value = CourseAction.NeedLogin("برای ثبت‌نام در دوره ابتدا وارد حساب شوید.")
            return
        }
        action { client.api.enroll(slug) }
    }

    /** Paid course purchase request — administrator approves it afterwards. */
    fun purchase(signedIn: Boolean) {
        if (!signedIn) {
            _action.value = CourseAction.NeedLogin("برای درخواست خرید دوره ابتدا وارد حساب شوید.")
            return
        }
        action { client.api.purchase(slug) }
    }

    private fun action(block: suspend () -> ir.pardava.mobile.data.dto.SimpleOkResponse) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val res = client.call { block() }
                _action.value = CourseAction.Message(res.message ?: "با موفقیت انجام شد.")
                load()
            } catch (e: ApiException) {
                if (e.isAuthError) {
                    _action.value = CourseAction.NeedLogin("نشست شما منقضی شده است؛ دوباره وارد شوید.")
                } else {
                    _action.value = CourseAction.Message(e.message, isError = true)
                }
            } catch (e: Exception) {
                _action.value = CourseAction.Message(e.message ?: "خطا", isError = true)
            } finally {
                _busy.value = false
            }
        }
    }
}
