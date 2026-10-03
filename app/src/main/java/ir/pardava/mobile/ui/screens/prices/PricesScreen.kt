package ir.pardava.mobile.ui.screens.prices

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.PriceItemDto
import ir.pardava.mobile.data.dto.PricesResponse
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.LoadingBox
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PricesUiState {
    data object Loading : PricesUiState
    data class Ready(val data: PricesResponse) : PricesUiState
    data class Failure(val message: String) : PricesUiState
}

class PricesViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<PricesUiState>(PricesUiState.Loading)
    val state: StateFlow<PricesUiState> = _state

    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        _state.value = PricesUiState.Loading
        viewModelScope.launch {
            try {
                val resp = client.call { client.api.prices() }
                loaded = true
                _state.value = PricesUiState.Ready(resp)
            } catch (e: Exception) {
                if (_state.value is PricesUiState.Ready) return@launch
                _state.value = PricesUiState.Failure(e.message ?: "error")
            }
        }
    }
}

/** برچسب فارسی گروه‌های قیمتی؛ گروه ناشناخته با همان نام انگلیسی نشان داده می‌شود. */
internal fun groupLabel(group: String?): String = when (group) {
    "gold" -> "طلا و سکه"
    "currency" -> "ارز"
    "crypto" -> "رمزارز"
    "energy" -> "انرژی"
    "metal" -> "فلزات"
    "index" -> "شاخص‌ها"
    null, "" -> "سایر"
    else -> group
}

/**
 * قیمت لحظه‌ای — همان منبع صفحهٔ /prices سایت (طلا/ارز/انرژی/رمزارز) با
 * فیلتر گروه‌ها و رنگ‌بندی تغییرات؛ فقط محتوا، بدون توضیح اضافه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(app: PardavaApp, onBack: () -> Unit) {
    val vm: PricesViewModel = viewModel(factory = SimpleVmFactory(app.api) { PricesViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val lang = app.currentLanguage()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                PricesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingBox() }
                is PricesUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
                is PricesUiState.Ready -> {
                    val items = s.data.items
                    val groups = items.mapNotNull { it.group }.distinct()
                    val filtered = if (group == null) items else items.filter { it.group == group }

                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = group == null,
                                onClick = { group = null },
                                label = { Text(stringResource(R.string.filter_all)) },
                            )
                            groups.forEach { g ->
                                FilterChip(
                                    selected = group == g,
                                    onClick = { group = if (group == g) null else g },
                                    label = { Text(groupLabel(g), maxLines = 1) },
                                )
                            }
                        }
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = if (s.data.live == true) Color(0xE610B981) else Color(0x8871717A),
                                contentColor = Color.White,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    if (s.data.live == true) {
                                        stringResource(R.string.prices_live)
                                    } else {
                                        stringResource(R.string.prices_delayed)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.prices_updated, s.data.updatedAt.orEmpty()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it.key ?: it.fa ?: "?" }) { price ->
                                PriceRow(price = price, lang = lang)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRow(price: PriceItemDto, lang: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (price.fa ?: price.key ?: "؟").take(1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    price.fa.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(price.unitFa, groupLabel(price.group)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Fmt.amount(price.toman ?: 0L, lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = when (price.dir) {
                        "up" -> Color(0xFF10B981)
                        "down" -> Color(0xFFF87171)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val icon = when (price.dir) {
                        "up" -> Icons.Filled.TrendingUp
                        "down" -> Icons.Filled.TrendingDown
                        else -> Icons.Filled.TrendingFlat
                    }
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        Fmt.percent(price.change_percent ?: 0.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
