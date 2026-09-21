package ir.pardava.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.ApiException
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.MeOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Ready(val me: MeOut) : ProfileUiState
    data class Failure(val message: String) : ProfileUiState
}

class ProfileViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state

    fun load() {
        _state.value = ProfileUiState.Loading
        viewModelScope.launch {
            try {
                val me: MeOut = apiCall { client.api().me() }
                me.user?.let { client.session.profile = it }
                _state.value = ProfileUiState.Ready(me)
            } catch (e: Exception) {
                // Revoked/expired token → drop the local session; Profile shows the signed-out card.
                if (e is ApiException && e.error.status == 401) client.session.clear()
                _state.value = ProfileUiState.Failure(e.message ?: "error")
            }
        }
    }
}
