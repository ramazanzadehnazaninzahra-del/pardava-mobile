package ir.pardava.mobile.ui.screens.services

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.AppConfigStore
import ir.pardava.mobile.data.dto.AppSectionDto
import ir.pardava.mobile.data.dto.ServiceItemDto
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.LoadingBox
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory
import ir.pardava.mobile.ui.screens.home.iconFor

/**
 * سرویس‌ها و ابزارها: ردیف «ابزارهای پردآوا» از کانفیگ سرور (نیتیو/وب،
 * فعال/غیرفعال و ترتیب از پنل دیزاین اپ) + همهٔ سرویس‌های سایت با فیلتر دسته.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    app: PardavaApp,
    onOpenService: (ServiceItemDto) -> Unit,
    onOpenTool: (AppSectionDto) -> Unit = {},
) {
    val vm: ServicesViewModel = viewModel(factory = SimpleVmFactory(app.api) { ServicesViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val cfg by app.appConfig.config.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    when (val s = state) {
        is ServicesUiState.Loading -> LoadingBox()
        is ServicesUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
        is ServicesUiState.Ready -> {
            val tools = (cfg.tools.ifEmpty { AppConfigStore.Defaults.tools })
            // ابزارهایی که نسخهٔ وب‌شان در سرویس‌ها هم هست، دوباره در گرید پایین تکرار نشوند.
            val toolServiceKeys = tools.mapNotNull { it.key }.mapNotNull { k ->
                if (k.startsWith("tool_")) k.removePrefix("tool_") else null
            }.toSet()
            val categories = s.services
                .filter { it.key !in toolServiceKeys }
                .mapNotNull { it.category }
                .distinct()
            val filtered = if (category == null) {
                s.services
            } else {
                s.services.filter { it.category == category }
            }

            Column(Modifier.fillMaxSize()) {
                // ── ابزارهای پردآوا (کانفیگ سرور) ──
                if (tools.isNotEmpty()) {
                    Text(
                        stringResource(R.string.tools_quick),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        items(tools, key = { it.key ?: it.title ?: "?" }) { tool ->
                            ToolTile(tool = tool) { onOpenTool(tool) }
                        }
                    }
                }

                // ── فیلتر دسته‌ها ──
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = category == null,
                        onClick = { category = null },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = if (category == cat) null else cat },
                            label = { Text(cat, maxLines = 1) },
                        )
                    }
                }

                // ── همهٔ سرویس‌ها ──
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        filtered.filter { it.key !in toolServiceKeys },
                        key = { it.key ?: it.title ?: "?" },
                    ) { svc ->
                        ServiceCard(
                            icon = iconFor(svc.icon),
                            title = svc.title.orEmpty(),
                            desc = svc.desc.orEmpty(),
                        ) { onOpenService(svc) }
                    }
                }
            }
        }
    }
}

/** کاشی ابزار — نیتیو و وب هر دو یک‌شکل باز می‌شوند؛ مسیریابی در PardavaNav. */
@Composable
private fun ToolTile(tool: AppSectionDto, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.clickable { onClick() },
    ) {
        Column(
            Modifier.padding(vertical = 11.dp, horizontal = 14.dp).width(78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(tool.icon),
                    contentDescription = tool.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                tool.title.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ServiceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp),
            )
        }
    }
}
