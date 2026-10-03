package ir.pardava.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.AppConfigStore
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.AppSectionDto
import ir.pardava.mobile.data.dto.MobileArticleDto
import ir.pardava.mobile.data.dto.MobileNewsItemDto
import ir.pardava.mobile.data.dto.PriceItemDto
import ir.pardava.mobile.data.dto.ServiceItemDto
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.SkeletonBlock
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

/**
 * Home — چینش بلوک‌ها از پنل «دیزاین اپ» سرور می‌آید (فعال/غیرفعال + ترتیب):
 * بنر، نرخ لحظه‌ای، دوره‌ها، ابزارهای هوشمند، لیگ، مقالات و اخبار.
 */
@Composable
fun HomeScreen(
    app: PardavaApp,
    onOpenCourse: (String) -> Unit,
    onOpenService: (ServiceItemDto) -> Unit,
    onOpenAllServices: (() -> Unit)?,
    onOpenArticle: (MobileArticleDto) -> Unit,
    onOpenLeague: () -> Unit,
    onOpenPrices: () -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenTool: (AppSectionDto) -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = SimpleVmFactory(app.api) { HomeViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val cfg by app.appConfig.config.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()

    LaunchedEffect(Unit) { vm.load() }

    when (val s = state) {
        is HomeUiState.Loading -> HomeSkeleton()
        is HomeUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
        is HomeUiState.Ready -> {
            val sectionOrder = (cfg.home.ifEmpty { AppConfigStore.Defaults.home }).mapNotNull { it.key }
            val tools = cfg.tools.ifEmpty { AppConfigStore.Defaults.tools }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                sectionOrder.forEach { key ->
                    when (key) {
                        "home_banners" -> if (s.data.banners.isNotEmpty()) {
                            item(key = key) {
                                BannerCarousel(urls = s.data.banners.mapNotNull { app.api.absoluteUrl(it) })
                            }
                        }

                        "home_prices" -> if (s.prices != null && s.prices.items.isNotEmpty()) {
                            item(key = key) {
                                SectionHeader(title = stringResource(R.string.home_prices), lang = lang)
                            }
                            item(key = "${key}_row") {
                                PricesTickerRow(
                                    items = featuredPrices(s.prices.items),
                                    lang = lang,
                                    onClick = onOpenPrices,
                                )
                            }
                        }

                        "home_courses" -> if (s.courses.isNotEmpty()) {
                            item(key = key) {
                                SectionHeader(title = stringResource(R.string.home_courses), lang = lang)
                            }
                            item(key = "${key}_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(s.courses, key = { it.slug ?: it.id?.toString() ?: "?" }) { course ->
                                        FeaturedCourseCard(
                                            title = course.title.orEmpty(),
                                            coverUrl = app.api.absoluteUrl(course.cover_url),
                                            free = course.effectiveFree,
                                            lessons = course.effectiveLessonCount,
                                            lang = lang,
                                        ) { course.slug?.let(onOpenCourse) }
                                    }
                                }
                            }
                        }

                        "home_tools" -> if (tools.isNotEmpty()) {
                            item(key = key) {
                                SectionHeader(title = stringResource(R.string.home_tools), lang = lang)
                            }
                            item(key = "${key}_grid") {
                                ToolsGrid(
                                    tools = tools.take(8),
                                    onOpenTool = onOpenTool,
                                )
                            }
                        }

                        "home_league" -> item(key = key) {
                            LeagueBanner(onOpenLeague = onOpenLeague)
                        }

                        "home_articles" -> if (s.articles.isNotEmpty()) {
                            item(key = key) {
                                SectionHeader(title = stringResource(R.string.home_articles), lang = lang)
                            }
                            item(key = "${key}_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(s.articles, key = { it.slug ?: it.id?.toString() ?: "?" }) { article ->
                                        ArticleMiniCard(
                                            title = article.title.orEmpty(),
                                            coverUrl = app.api.absoluteUrl(article.coverUrl),
                                            tag = article.tag,
                                        ) { onOpenArticle(article) }
                                    }
                                }
                            }
                        }

                        "home_news" -> if (s.news.isNotEmpty()) {
                            item(key = key) {
                                SectionHeader(title = stringResource(R.string.home_news), lang = lang)
                            }
                            item(key = "${key}_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(s.news, key = { it.id?.toString() ?: it.title ?: "?" }) { newsItem ->
                                        NewsMiniCard(newsItem = newsItem) { onOpenNews() }
                                    }
                                }
                            }
                        }
                    }
                }

                // قرص «همهٔ سرویس‌ها» — وقتی تب سرویس‌ها فعال است (کنترل از پنل)
                if (onOpenAllServices != null && s.services.isNotEmpty()) {
                    item(key = "all_services_pill") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clickable { onOpenAllServices() },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.home_all_services, Fmt.int(s.services.size, lang)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun featuredPrices(items: List<PriceItemDto>): List<PriceItemDto> {
    val featured = items.filter { it.featured != null }.sortedBy { it.featured ?: 99 }
    val chosen = if (featured.isNotEmpty()) featured else items.filter { it.group in setOf("gold", "currency") }
    return chosen.take(10)
}

/** تیکر نرخ لحظه‌ای — کارت‌های کوچک با رنگ تغییرات؛ کل ردیف به صفحهٔ قیمت باز می‌شود. */
@Composable
private fun PricesTickerRow(items: List<PriceItemDto>, lang: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 10.dp),
    ) {
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.key ?: it.fa ?: "?" }) { price ->
                Column(
                    Modifier
                        .width(116.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        price.fa.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        Fmt.amount(price.toman ?: 0L, lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    ChangeBadge(change = price.change_percent, dir = price.dir)
                }
            }
        }
    }
}

@Composable
private fun ChangeBadge(change: Double?, dir: String?) {
    val color = when (dir) {
        "up" -> Color(0xFF10B981)
        "down" -> Color(0xFFF87171)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (dir) {
        "up" -> Icons.Filled.TrendingUp
        "down" -> Icons.Filled.TrendingDown
        else -> Icons.Filled.TrendingFlat
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            Fmt.percent(change ?: 0.0),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** شبکهٔ ۴تایی ابزارهای هوشمند (نیتیو/وب) — از کانفیگ سرور. */
@Composable
private fun ToolsGrid(tools: List<AppSectionDto>, onOpenTool: (AppSectionDto) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { tool ->
                    ServiceTile(
                        icon = iconFor(tool.icon),
                        label = tool.title.orEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { onOpenTool(tool) }
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LeagueBanner(onOpenLeague: () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp)) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clickable { onOpenLeague() },
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        ),
                    ),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.league_card),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.league_card_hint),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** کارت خبری خانه — تصویر + عنوان فارسی. */
@Composable
private fun NewsMiniCard(newsItem: MobileNewsItemDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(190.dp)
            .clickable { onClick() },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                val img = newsItem.imageUrl
                if (!img.isNullOrBlank()) {
                    AsyncImage(
                        model = img,
                        contentDescription = newsItem.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Newspaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Text(
                newsItem.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

/**
 * Site banners: auto-advancing pager with the exact images the website's
 * homepage slider shows (managed from the admin panel hero section).
 */
@Composable
private fun BannerCarousel(urls: List<String>) {
    if (urls.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { urls.size })
    LaunchedEffect(urls.size) {
        while (isActive) {
            delay(4_500)
            if (urls.size > 1) {
                val next = (pagerState.currentPage + 1) % urls.size
                runCatching { pagerState.animateScrollToPage(next) }
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp)),
        ) { page ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 6f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = urls[page],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (urls.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(urls.size) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 7.dp else 5.dp)
                            .background(
                                if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, lang: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
    )
}

@Composable
private fun FeaturedCourseCard(
    title: String,
    coverUrl: String?,
    free: Boolean,
    lessons: Int,
    lang: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(248.dp)
            .clickable { onClick() },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                if (free) {
                    Surface(
                        color = Color(0xE610B981),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.badge_free),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    Fmt.digits("$lessons ${stringResource(R.string.lessons_word)}", lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ServiceTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.clickable { onClick() },
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArticleMiniCard(title: String, coverUrl: String?, tag: String?, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(190.dp)
            .clickable { onClick() },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                if (!tag.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), corner = 18.dp)
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { SkeletonBlock(Modifier.weight(1f).height(74.dp), corner = 14.dp) }
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(74.dp), corner = 18.dp)
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { SkeletonBlock(Modifier.weight(1f).height(90.dp), corner = 16.dp) }
        }
    }
}

/** Map the mobile-api icon keys to material icons; unknown keys fall back. */
internal fun iconFor(key: String?): ImageVector = when (key) {
    "chat" -> Icons.Filled.Chat
    "support_agent" -> Icons.Filled.SupportAgent
    "visibility" -> Icons.Filled.Visibility
    "photo_camera" -> Icons.Filled.PhotoCamera
    "mic" -> Icons.Filled.Mic
    "menu_book" -> Icons.Filled.MenuBook
    "graphic_eq" -> Icons.Filled.GraphicEq
    "auto_stories" -> Icons.Filled.AutoStories
    "translate" -> Icons.Filled.Translate
    "layers" -> Icons.Filled.Layers
    "auto_fix_high" -> Icons.Filled.AutoFixHigh
    "history" -> Icons.Filled.History
    "palette" -> Icons.Filled.Palette
    "tune" -> Icons.Filled.Tune
    "science" -> Icons.Filled.Science
    "candlestick_chart" -> Icons.Filled.CandlestickChart
    "monitoring" -> Icons.Filled.QueryStats
    "query_stats" -> Icons.Filled.QueryStats
    "account_balance" -> Icons.Filled.AccountBalance
    "sports_esports" -> Icons.Filled.SportsEsports
    "web" -> Icons.Filled.Web
    "design_services" -> Icons.Filled.DesignServices
    "work" -> Icons.Filled.Work
    "newspaper" -> Icons.Filled.Newspaper
    "balance" -> Icons.Filled.Balance
    "document_scanner" -> Icons.Filled.DocumentScanner
    "emoji_events" -> Icons.Filled.EmojiEvents
    "school" -> Icons.Filled.School
    "home" -> Icons.Filled.Home
    "person" -> Icons.Filled.Person
    "apps" -> Icons.Filled.Apps
    "article" -> Icons.Filled.Article
    "image" -> Icons.Filled.Image
    else -> Icons.Filled.Apps
}
