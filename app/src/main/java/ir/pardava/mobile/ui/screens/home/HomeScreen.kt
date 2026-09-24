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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Work
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
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.MobileArticleDto
import ir.pardava.mobile.data.dto.ServiceItemDto
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.SkeletonBlock
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

/**
 * Home — pure content, almost no words: fresh courses, quick services,
 * latest articles and the league, all one tap away.
 */
@Composable
fun HomeScreen(
    app: PardavaApp,
    onOpenCourse: (String) -> Unit,
    onOpenService: (ServiceItemDto) -> Unit,
    onOpenAllServices: () -> Unit,
    onOpenArticle: (MobileArticleDto) -> Unit,
    onOpenLeague: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = SimpleVmFactory(app.api) { HomeViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    when (val s = state) {
        is HomeUiState.Loading -> HomeSkeleton()
        is HomeUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
        is HomeUiState.Ready -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---- site banners (same hero slider the admin panel manages) ----
            if (s.data.banners.isNotEmpty()) {
                item {
                    BannerCarousel(urls = s.data.banners.mapNotNull { app.api.absoluteUrl(it) })
                }
            }

            // ---- fresh courses ----
            if (s.courses.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_courses),
                        lang = lang,
                    )
                }
                item {
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

            // ---- services quick grid (8) ----
            if (s.services.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.home_services), lang = lang) }
                item {
                    val quick = s.services.take(8)
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        quick.chunked(4).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowItems.forEach { svc ->
                                    ServiceTile(
                                        icon = iconFor(svc.icon),
                                        label = svc.title.orEmpty(),
                                        modifier = Modifier.weight(1f),
                                    ) { onOpenService(svc) }
                                }
                                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        // entry to the full services tab
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier
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

            // ---- league banner ----
            item {
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

            // ---- latest articles ----
            if (s.articles.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.home_articles), lang = lang) }
                item {
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
    "account_balance" -> Icons.Filled.AccountBalance
    "sports_esports" -> Icons.Filled.SportsEsports
    "web" -> Icons.Filled.Web
    "design_services" -> Icons.Filled.DesignServices
    "work" -> Icons.Filled.Work
    else -> Icons.Filled.Apps
}
