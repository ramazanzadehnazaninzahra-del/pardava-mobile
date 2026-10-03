package ir.pardava.mobile.ui.screens.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.MobileNewsItemDto
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.LoadingBox
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface NewsUiState {
    data object Loading : NewsUiState
    data class Ready(
        val items: List<MobileNewsItemDto>,
        val count: Int,
        val endReached: Boolean,
    ) : NewsUiState

    data class Failure(val message: String) : NewsUiState
}

class NewsViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val state: StateFlow<NewsUiState> = _state

    private var offset = 0
    private val pageSize = 24
    private var loadingMore = false

    fun load(force: Boolean = false) {
        if (offset > 0 && !force) return
        offset = 0
        _state.value = NewsUiState.Loading
        viewModelScope.launch { loadPage() }
    }

    fun loadMore() {
        if (loadingMore) return
        val current = _state.value as? NewsUiState.Ready ?: return
        if (current.endReached) return
        loadingMore = true
        viewModelScope.launch {
            loadPage()
            loadingMore = false
        }
    }

    /** بارگذاری یک صفحه؛ خطای صفحه‌بندی بی‌صدا رد می‌شود (دکمهٔ «بیشتر» می‌ماند). */
    private suspend fun loadPage(): Boolean {
        return try {
            val resp = client.call { client.api.mobileNews(limit = pageSize, offset = offset) }
            val fresh = resp.items
            val isAppend = offset > 0
            offset += fresh.size
            val current = _state.value
            val merged = if (isAppend && current is NewsUiState.Ready) {
                (current.items + fresh).distinctBy { it.id }
            } else {
                fresh
            }
            _state.value = NewsUiState.Ready(
                items = merged,
                count = resp.count ?: merged.size,
                endReached = fresh.size < pageSize,
            )
            true
        } catch (e: Exception) {
            if (offset == 0) {
                _state.value = NewsUiState.Failure(e.message ?: "error")
            }
            offset == 0
        }
    }
}

/** نام نمایشی منبع خبر از URL فید (مثلاً host بدون www و پسوند). */
internal fun sourceLabel(sourceFeed: String?): String {
    if (sourceFeed.isNullOrBlank()) return ""
    return runCatching {
        java.net.URI(sourceFeed).host
            ?.removePrefix("www.")
            .orEmpty()
    }.getOrDefault("").ifBlank { sourceFeed.take(24) }
}

/**
 * اخبار پردآوا — فقط خبرهای منتشرشده با اولویت عنوان/توضیح فارسی؛
 * ضربه روی خبر، خوانندهٔ داخل اپ را باز می‌کند (شناسهٔ خبر ذخیره می‌شود).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(app: PardavaApp, onBack: () -> Unit) {
    val vm: NewsViewModel = viewModel(factory = SimpleVmFactory(app.api) { NewsViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    // شناسهٔ خبر انتخاب‌شده — فقط مقدار ساده برای rememberSaveable
    var selectedId by rememberSaveable { mutableStateOf(-1L) }

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.news_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedId >= 0) selectedId = -1L else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val s = state
            when {
                s is NewsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingBox() }
                s is NewsUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
                s is NewsUiState.Ready -> {
                    val selected = s.items.firstOrNull { it.id == selectedId && selectedId >= 0 }
                    if (selected != null) {
                        NewsDetail(newsItem = selected)
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(s.items, key = { it.id ?: it.title?.hashCode() ?: 0L }) { newsItem ->
                                NewsCard(newsItem = newsItem) { newsItem.id?.let { id -> selectedId = id } }
                            }
                            if (!s.endReached && s.items.isNotEmpty()) {
                                item(key = "load_more") {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        OutlinedButton(onClick = { vm.loadMore() }) {
                                            Text(stringResource(R.string.news_load_more))
                                        }
                                    }
                                }
                            }
                            if (s.items.isEmpty()) {
                                item(key = "empty") {
                                    Text(
                                        stringResource(R.string.news_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 32.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsCard(newsItem: MobileNewsItemDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Column {
            val img = newsItem.imageUrl
            if (!img.isNullOrBlank()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    AsyncImage(
                        model = img,
                        contentDescription = newsItem.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.padding(13.dp)) {
                Text(
                    newsItem.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val desc = newsItem.displayDescription
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val source = sourceLabel(newsItem.sourceFeed)
                if (source.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** خوانندهٔ خبر داخل اپ: تصویر + متن کامل + دکمهٔ منبع (مرورگر بیرونی). */
@Composable
private fun NewsDetail(newsItem: MobileNewsItemDto) {
    val ctx = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            val img = newsItem.imageUrl
            if (!img.isNullOrBlank()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    AsyncImage(
                        model = img,
                        contentDescription = newsItem.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Newspaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text(
                    newsItem.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                val source = sourceLabel(newsItem.sourceFeed)
                if (source.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        source,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val desc = newsItem.displayDescription
                if (desc.isNotBlank()) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                val link = newsItem.link
                if (!link.isNullOrBlank()) {
                    Button(
                        onClick = {
                            runCatching {
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                    .let { ctx.startActivity(it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.news_open_source))
                    }
                } else {
                    OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.news_no_source))
                    }
                }
            }
        }
    }
}
