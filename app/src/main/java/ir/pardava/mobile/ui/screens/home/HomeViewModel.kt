package ir.pardava.mobile.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.CourseCardDto
import ir.pardava.mobile.data.dto.HomeResponse
import ir.pardava.mobile.data.dto.MobileArticleDto
import ir.pardava.mobile.data.dto.MobileNewsItemDto
import ir.pardava.mobile.data.dto.PricesResponse
import ir.pardava.mobile.data.dto.ServiceItemDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val data: HomeResponse,
        val courses: List<CourseCardDto>,
        val services: List<ServiceItemDto>,
        val articles: List<MobileArticleDto>,
        val prices: PricesResponse? = null,
        val news: List<MobileNewsItemDto> = emptyList(),
    ) : HomeUiState

    data class Failure(val message: String) : HomeUiState
}

class HomeViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state

    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                // قیمت‌ها و اخبار جانبی‌اند: اگر کند/خطا دادند، خانه بدون آن‌ها می‌ماند.
                val pricesDeferred = async { runCatching { client.call { client.api.prices() } }.getOrNull() }
                val newsDeferred = async { runCatching { client.call { client.api.mobileNews(limit = 10) } }.getOrNull() }
                val home = client.call { client.api.mobileHome() }
                loaded = true
                _state.value = HomeUiState.Ready(
                    data = home,
                    courses = home.courses,
                    services = home.services,
                    articles = home.articles,
                    prices = pricesDeferred.await(),
                    news = newsDeferred.await()?.items.orEmpty(),
                )
            } catch (e: Exception) {
                _state.value = HomeUiState.Failure(e.message ?: "error")
            }
        }
    }
}
