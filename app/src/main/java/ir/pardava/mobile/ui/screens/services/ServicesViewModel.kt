package ir.pardava.mobile.ui.screens.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.ServiceItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ServicesUiState {
    data object Loading : ServicesUiState
    data class Ready(val services: List<ServiceItemDto>) : ServicesUiState
    data class Failure(val message: String) : ServicesUiState
}

class ServicesViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<ServicesUiState>(ServicesUiState.Loading)
    val state: StateFlow<ServicesUiState> = _state

    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        viewModelScope.launch {
            try {
                val resp = client.call { client.api.mobileServices() }
                loaded = true
                _state.value = ServicesUiState.Ready(resp.services)
            } catch (e: Exception) {
                if (_state.value is ServicesUiState.Ready) return@launch
                _state.value = ServicesUiState.Failure(e.message ?: "error")
            }
        }
    }
}
