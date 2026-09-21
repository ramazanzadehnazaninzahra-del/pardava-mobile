package ir.pardava.mobile.ui.screens.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.CourseDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CourseUiState {
    data object Loading : CourseUiState
    data class Ready(val course: CourseDetail) : CourseUiState
    data class LockedLesson(val message: String) : CourseUiState
    data class Failure(val message: String) : CourseUiState
}

class CourseViewModel(private val client: ApiClient, private val slug: String) : ViewModel() {

    private val _state = MutableStateFlow<CourseUiState>(CourseUiState.Loading)
    val state: StateFlow<CourseUiState> = _state

    fun load() {
        _state.value = CourseUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = CourseUiState.Ready(apiCall { client.api().course(slug) })
            } catch (e: ir.pardava.mobile.core.ApiException) {
                _state.value = CourseUiState.Failure(e.error.messageFa + " / " + e.error.messageEn)
            } catch (e: Exception) {
                _state.value = CourseUiState.Failure(e.message ?: "error")
            }
        }
    }
}
