package ir.pardava.mobile.ui.screens.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.LeagueOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LeagueUiState {
    data object Loading : LeagueUiState
    data class Ready(val data: LeagueOut) : LeagueUiState
    data class Failure(val message: String) : LeagueUiState
}

/** League table: site-wide by default, or scoped to one course. */
class LeagueViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<LeagueUiState>(LeagueUiState.Loading)
    val state: StateFlow<LeagueUiState> = _state

    private val _selectedCourse = MutableStateFlow<String?>(null)
    val selectedCourse: StateFlow<String?> = _selectedCourse

    fun selectCourse(slug: String?) {
        if (_selectedCourse.value != slug) {
            _selectedCourse.value = slug
            load()
        }
    }

    fun load() {
        _state.value = LeagueUiState.Loading
        viewModelScope.launch {
            try {
                val out = apiCall { client.api().league(_selectedCourse.value) }
                _state.value = LeagueUiState.Ready(out)
            } catch (e: Exception) {
                _state.value = LeagueUiState.Failure(e.message ?: "error")
            }
        }
    }
}
