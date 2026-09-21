package ir.pardava.mobile.ui.screens.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.CourseBrief
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CoursesUiState {
    data object Loading : CoursesUiState
    data class Ready(val courses: List<CourseBrief>) : CoursesUiState
    data class Failure(val message: String) : CoursesUiState
}

class CoursesViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<CoursesUiState>(CoursesUiState.Loading)
    val state: StateFlow<CoursesUiState> = _state

    fun load() {
        _state.value = CoursesUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = CoursesUiState.Ready(apiCall { client.api().courses() })
            } catch (e: Exception) {
                _state.value = CoursesUiState.Failure(e.message ?: "error")
            }
        }
    }
}
