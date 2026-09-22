package ir.pardava.mobile.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.CourseRefDto
import ir.pardava.mobile.data.dto.LeaderRowDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LeagueUiState {
    data object Loading : LeagueUiState
    data class Ready(
        val leaders: List<LeaderRowDto>,
        val courses: List<CourseRefDto>,
        val selected: String?,
        val scopeTitle: String?,
    ) : LeagueUiState
    data class Failure(val message: String) : LeagueUiState
}

/** League standings — public (no login), filterable by course. */
class LeagueViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<LeagueUiState>(LeagueUiState.Loading)
    val state: StateFlow<LeagueUiState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var loaded = false
    private var selectedCourse: String? = null

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        if (force) _refreshing.value = true else _state.value = LeagueUiState.Loading
        viewModelScope.launch {
            try {
                val res = client.call { client.api.league(selectedCourse) }
                loaded = true
                _state.value = LeagueUiState.Ready(
                    leaders = res.leaders,
                    courses = res.courses,
                    selected = selectedCourse,
                    scopeTitle = res.scope?.title,
                )
            } catch (e: Exception) {
                if (_state.value is LeagueUiState.Ready) return@launch
                _state.value = LeagueUiState.Failure(e.message ?: "error")
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun selectCourse(slug: String?) {
        selectedCourse = slug
        loaded = false
        load()
    }
}
