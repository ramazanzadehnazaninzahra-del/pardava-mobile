package ir.pardava.mobile.ui.screens.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.ApiException
import ir.pardava.mobile.data.dto.CourseDetailResponse
import ir.pardava.mobile.data.dto.RateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CourseUiState {
    data object Loading : CourseUiState
    data class Ready(val detail: CourseDetailResponse) : CourseUiState
    data class Failure(val message: String) : CourseUiState
}

/** Learning extras loaded from /api/courses/<slug>/learning (rating, quiz, certificate). */
data class LearningExtras(
    val learning: ir.pardava.mobile.data.dto.LearningResponse? = null,
)

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

    private val _extras = MutableStateFlow(LearningExtras())
    val extras: StateFlow<LearningExtras> = _extras

    fun consumeAction() { _action.value = null }

    fun load() {
        _state.value = CourseUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = CourseUiState.Ready(client.call { client.api.course(slug) })
                loadExtras()
            } catch (e: Exception) {
                _state.value = CourseUiState.Failure(e.message ?: "error")
            }
        }
    }

    /** Fire-and-forget bundle: rating/quiz/certificate (never blocks the page). */
    fun loadExtras() {
        viewModelScope.launch {
            runCatching {
                val res = client.call { client.api.learning(slug) }
                _extras.value = LearningExtras(res)
            }
        }
    }

    /** Submit a star rating (logged-in only). */
    fun rate(signedIn: Boolean, stars: Int) {
        if (!signedIn) {
            _action.value = CourseAction.NeedLogin("برای امتیاز دادن به دوره ابتدا وارد حساب شوید.")
            return
        }
        if (stars !in 1..5) return
        viewModelScope.launch {
            try {
                val res = client.call { client.api.rate(slug, RateIn(stars)) }
                _action.value = CourseAction.Message("امتیاز شما ثبت شد؛ سپاس!")
                loadExtras()
            } catch (e: ApiException) {
                if (e.isAuthError) {
                    _action.value = CourseAction.NeedLogin("نشست شما منقضی شده است؛ دوباره وارد شوید.")
                } else {
                    _action.value = CourseAction.Message(e.message, isError = true)
                }
            } catch (e: Exception) {
                _action.value = CourseAction.Message(e.message ?: "خطا", isError = true)
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
