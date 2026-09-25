package ir.pardava.mobile.ui.screens.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.MobileArticleDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ArticlesUiState {
    data object Loading : ArticlesUiState
    data class Ready(val articles: List<MobileArticleDto>) : ArticlesUiState
    data class Failure(val message: String) : ArticlesUiState
}

class ArticlesViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<ArticlesUiState>(ArticlesUiState.Loading)
    val state: StateFlow<ArticlesUiState> = _state

    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        viewModelScope.launch {
            try {
                val resp = client.call { client.api.mobileArticles(limit = 40) }
                loaded = true
                _state.value = ArticlesUiState.Ready(resp.articles)
            } catch (e: Exception) {
                if (_state.value is ArticlesUiState.Ready) return@launch
                _state.value = ArticlesUiState.Failure(e.message ?: "error")
            }
        }
    }
}
