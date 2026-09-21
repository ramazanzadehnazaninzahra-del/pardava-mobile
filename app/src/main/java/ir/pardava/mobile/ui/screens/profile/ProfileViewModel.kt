package ir.pardava.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.AchievementOut
import ir.pardava.mobile.data.dto.UserStatsOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Ready(val stats: UserStatsOut, val achievements: List<AchievementOut>) : ProfileUiState
    data class Failure(val message: String) : ProfileUiState
}

class ProfileViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state

    fun load() {
        _state.value = ProfileUiState.Loading
        viewModelScope.launch {
            try {
                val stats = apiCall { client.api().myStats() }
                val achievements = apiCall { client.api().myAchievements() }
                _state.value = ProfileUiState.Ready(stats, achievements)
            } catch (e: Exception) {
                _state.value = ProfileUiState.Failure(e.message ?: "error")
            }
        }
    }
}
