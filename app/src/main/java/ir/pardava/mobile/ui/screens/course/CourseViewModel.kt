package ir.pardava.mobile.ui.screens.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.CourseDetailOut
import ir.pardava.mobile.data.dto.MessageOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CourseUiState {
    data object Loading : CourseUiState
    data class Ready(val detail: CourseDetailOut) : CourseUiState
    data class Failure(val message: String) : CourseUiState
}

class CourseViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<CourseUiState>(CourseUiState.Loading)
    val state: StateFlow<CourseUiState> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private var slug: String = ""

    fun bind(slug: String) {
        if (this.slug != slug) {
            this.slug = slug
            load()
        }
    }

    fun load() {
        if (slug.isEmpty()) return
        _state.value = CourseUiState.Loading
        viewModelScope.launch {
            try {
                val detail = apiCall { client.api().course(slug) }
                _state.value = CourseUiState.Ready(detail)
            } catch (e: Exception) {
                _state.value = CourseUiState.Failure(e.message ?: "error")
            }
        }
    }

    /** Enroll in a free course (token required by the server). */
    fun enroll() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val out: MessageOut = apiCall { client.api().enroll(slug) }
                _notice.value = out.message
                load()
            } catch (e: Exception) {
                _notice.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    /** Request admin approval for a paid course. */
    fun requestPurchase() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val out: MessageOut = apiCall { client.api().purchase(slug) }
                _notice.value = out.message
                load()
            } catch (e: Exception) {
                _notice.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }
}
