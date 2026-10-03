package ir.pardava.mobile.ui.screens.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.CourseCardDto
import ir.pardava.mobile.ui.components.EmptyState
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.SkeletonCourseCard

/**
 * Public course catalog — the heart of the guest experience. No login needed.
 * Educational-app UX: search field, filter chips (price / level / category),
 * rich course cards (students, duration, lessons, points) and pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(app: PardavaApp, onOpenCourse: (String) -> Unit) {
    val vm: CoursesViewModel = viewModel(factory = SimpleVmFactory(app.api) { CoursesViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.load(force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val s = state) {
            is CoursesUiState.Loading -> CatalogSkeleton()
            is CoursesUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
            is CoursesUiState.Ready -> CatalogContent(
                courses = s.courses,
                refreshing = refreshing,
                lang = lang,
                coverUrlOf = { app.api.absoluteUrl(it) },
                onOpenCourse = onOpenCourse,
            )
        }
    }
}

@Composable
private fun CatalogSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SkeletonCourseCard()
        Spacer(Modifier.height(14.dp))
        SkeletonCourseCard()
        Spacer(Modifier.height(14.dp))
        SkeletonCourseCard()
    }
}

@Composable
private fun CatalogContent(
    courses: List<CourseCardDto>,
    refreshing: Boolean,
    lang: String,
    coverUrlOf: (String?) -> String?,
    onOpenCourse: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // price filter: 0 = all, 1 = free, 2 = paid
    var priceFilter by rememberSaveable { mutableStateOf(0) }
    var levelFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val levels = courses.mapNotNull { it.level_label }.filter { it.isNotBlank() }.distinct()
    val categories = courses.mapNotNull { it.category_label }.filter { it.isNotBlank() }.distinct()

    val filtered = courses.filter { c ->
        val q = query.trim()
        val matchesQuery = q.isEmpty() ||
            c.title?.contains(q, ignoreCase = true) == true ||
            c.summary?.contains(q, ignoreCase = true) == true ||
            c.category_label?.contains(q, ignoreCase = true) == true
        val matchesPrice = when (priceFilter) {
            1 -> c.effectiveFree
            2 -> !c.effectiveFree
            else -> true
        }
        val matchesLevel = levelFilter == null || c.level_label == levelFilter
        val matchesCategory = categoryFilter == null || c.category_label == categoryFilter
        matchesQuery && matchesPrice && matchesLevel && matchesCategory
    }

    Column(Modifier.fillMaxSize()) {
        // ---- header ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.courses_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.courses_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- search field ----
        SearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))

        // ---- filter chips ----
        if (courses.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = priceFilter == 0 && levelFilter == null && categoryFilter == null,
                        onClick = {
                            priceFilter = 0; levelFilter = null; categoryFilter = null
                        },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = priceFilter == 1,
                        onClick = { priceFilter = if (priceFilter == 1) 0 else 1 },
                        label = { Text(stringResource(R.string.badge_free)) },
                    )
                }
                item {
                    FilterChip(
                        selected = priceFilter == 2,
                        onClick = { priceFilter = if (priceFilter == 2) 0 else 2 },
                        label = { Text(stringResource(R.string.badge_paid)) },
                    )
                }
                levels.forEach { lvl ->
                    item(key = "lvl:$lvl") {
                        FilterChip(
                            selected = levelFilter == lvl,
                            onClick = { levelFilter = if (levelFilter == lvl) null else lvl },
                            label = { Text(lvl, maxLines = 1) },
                        )
                    }
                }
                categories.forEach { cat ->
                    item(key = "cat:$cat") {
                        FilterChip(
                            selected = categoryFilter == cat,
                            onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                            label = { Text(cat, maxLines = 1) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ---- result count ----
        Text(
            Fmt.digits(stringResource(R.string.results_count, "${filtered.size}"), lang),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp),
        )

        // ---- list ----
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.no_results_title),
                    subtitle = stringResource(R.string.no_results_subtitle),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    filtered,
                    key = { it.slug ?: it.id?.toString() ?: it.title ?: "?" },
                ) { course ->
                    CourseCard(
                        course = course,
                        lang = lang,
                        coverUrl = coverUrlOf(course.cover_url),
                    ) { slug -> slug?.let(onOpenCourse) }
                }
            }
        }
    }
}

/** Rounded search input with leading icon and a clear button. */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(22.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.filter_all),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CourseCard(course: CourseCardDto, lang: String, coverUrl: String?, onClick: (String?) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(course.slug) },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp),
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = course.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.School,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
                // Bottom scrim for badge legibility
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.45f),
                            ),
                        ),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (course.effectiveFree) {
                        Badge(stringResource(R.string.badge_free), container = Color(0xCC10B981), contentColor = Color.White)
                    } else {
                        Badge(stringResource(R.string.badge_paid), container = Color(0xCCF59E0B), contentColor = Color.Black)
                    }
                    if (course.enrolled == true) {
                        Badge(stringResource(R.string.badge_enrolled), container = Color(0xCC4F46E5), contentColor = Color.White)
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    course.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!course.summary.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        course.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    course.category_label?.let { InfoChip(it) }
                    course.level_label?.let { InfoChip(it) }
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                // ---- meta row: lessons · students · duration · points ----
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Meta(
                        icon = { Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        text = Fmt.digits("${course.effectiveLessonCount} ${stringResource(R.string.lessons_word)}", lang),
                    )
                    if ((course.studentsCount ?: 0) > 0) {
                        Meta(
                            icon = { Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            text = Fmt.digits("${course.studentsCount} ${stringResource(R.string.students_word)}", lang),
                        )
                    }
                    course.totalMinutes?.takeIf { it > 0 }?.let { mins ->
                        Meta(
                            icon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            text = durationLabel(mins, lang),
                        )
                    }
                }
                course.pointsPerLesson?.takeIf { it > 0 }?.let { pts ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.points_per_lesson_short, Fmt.int(pts, lang)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** "۹۰ دقیقه" / "۲ ساعت و ۳۰ دقیقه" — localized digits. */
@Composable
private fun durationLabel(totalMinutes: Int, lang: String): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val hoursWord = stringResource(R.string.hours_word)
    val minutesWord = stringResource(R.string.minutes_word)
    val raw = when {
        h > 0 && m > 0 -> "$h $hoursWord و $m $minutesWord"
        h > 0 -> "$h $hoursWord"
        else -> "$m $minutesWord"
    }
    return Fmt.digits(raw, lang)
}

@Composable
private fun Meta(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.secondary,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Badge(text: String, container: Color, contentColor: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = contentColor, fontWeight = FontWeight.Bold)
    }
}
