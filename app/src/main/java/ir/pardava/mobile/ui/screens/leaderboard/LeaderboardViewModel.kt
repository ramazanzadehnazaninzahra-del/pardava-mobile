package ir.pardava.mobile.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.LeaderboardOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LeaderboardUiState {
    data object Loading : LeaderboardUiState
    data class Ready(val board: LeaderboardOut) : LeaderboardUiState
    data class Failure(val message: String) : LeaderboardUiState
}

class LeaderboardViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val state: StateFlow<LeaderboardUiState> = _state

    fun load(scope: String) {
        _state.value = LeaderboardUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = LeaderboardUiState.Ready(apiCall { client.api().leaderboard(scope) })
            } catch (e: Exception) {
                _state.value = LeaderboardUiState.Failure(e.message ?: "error")
            }
        }
    }
}
