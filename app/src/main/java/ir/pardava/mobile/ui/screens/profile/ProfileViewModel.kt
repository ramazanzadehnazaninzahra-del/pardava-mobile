package ir.pardava.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.CourseCardDto
import ir.pardava.mobile.data.dto.MeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Guest : ProfileUiState
    data object Loading : ProfileUiState

    /** [enrolledCourses] powers the "continue learning" rows (from /api/courses). */
    data class Ready(
        val me: MeResponse,
        val enrolledCourses: List<CourseCardDto> = emptyList(),
    ) : ProfileUiState

    data class Failure(val message: String) : ProfileUiState
}

class ProfileViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state

    fun refresh(signedIn: Boolean) {
        if (!signedIn) {
            _state.value = ProfileUiState.Guest
            return
        }
        _state.value = ProfileUiState.Loading
        viewModelScope.launch {
            try {
                val me = client.call { client.api.me() }
                // me() carries only the enrollment COUNT; the course rows for
                // "continue learning" come from the catalog (enrolled flag).
                val courses = runCatching { client.call { client.api.courses() } }.getOrNull()
                val enrolled = courses?.courses?.filter { it.enrolled == true }.orEmpty()
                _state.value = ProfileUiState.Ready(me, enrolled)
            } catch (e: Exception) {
                _state.value = ProfileUiState.Failure(e.message ?: "error")
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            client.logout()
            onDone()
        }
    }
}
