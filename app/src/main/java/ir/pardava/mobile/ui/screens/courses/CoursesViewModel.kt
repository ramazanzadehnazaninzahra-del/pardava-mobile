package ir.pardava.mobile.ui.screens.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.CourseCardDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CoursesUiState {
    data object Loading : CoursesUiState
    data class Ready(val courses: List<CourseCardDto>) : CoursesUiState
    data class Failure(val message: String) : CoursesUiState
}

class CoursesViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<CoursesUiState>(CoursesUiState.Loading)
    val state: StateFlow<CoursesUiState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        if (force) _refreshing.value = true else _state.value = CoursesUiState.Loading
        viewModelScope.launch {
            try {
                val list = client.call { client.api.courses() }.courses
                loaded = true
                _state.value = CoursesUiState.Ready(list)
            } catch (e: Exception) {
                if (_state.value is CoursesUiState.Ready) return@launch
                _state.value = CoursesUiState.Failure(e.message ?: "error")
            } finally {
                _refreshing.value = false
            }
        }
    }
}
