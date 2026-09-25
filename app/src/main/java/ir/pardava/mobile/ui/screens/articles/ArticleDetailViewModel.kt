package ir.pardava.mobile.ui.screens.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.ArticleDetailDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ArticleDetailUiState {
    data object Loading : ArticleDetailUiState
    data class Ready(val article: ArticleDetailDto) : ArticleDetailUiState
    data class Failure(val message: String) : ArticleDetailUiState
}

class ArticleDetailViewModel(
    private val client: ApiClient,
    private val key: String,
) : ViewModel() {

    private val _state = MutableStateFlow<ArticleDetailUiState>(ArticleDetailUiState.Loading)
    val state: StateFlow<ArticleDetailUiState> = _state

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            try {
                val resp = client.call { client.api.mobileArticle(key) }
                val article = resp.article ?: throw IllegalStateException("not found")
                _state.value = ArticleDetailUiState.Ready(article)
            } catch (e: Exception) {
                _state.value = ArticleDetailUiState.Failure(e.message ?: "error")
            }
        }
    }
}
