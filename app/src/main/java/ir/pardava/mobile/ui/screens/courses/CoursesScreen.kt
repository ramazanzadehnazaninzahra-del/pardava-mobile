package ir.pardava.mobile.ui.screens.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import ir.pardava.mobile.ui.components.InfoChip
import ir.pardava.mobile.ui.components.LoadingBox

/**
 * Public course catalog — the heart of the guest experience. No login needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(app: PardavaApp, onOpenCourse: (String) -> Unit) {
    val vm: CoursesViewModel = viewModel(factory = SimpleVmFactory(app.api) { CoursesViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    when (val s = state) {
        is CoursesUiState.Loading -> LoadingBox()
        is CoursesUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
        is CoursesUiState.Ready -> {
            if (s.courses.isEmpty()) {
                Column {
                    RefreshBar(refreshing) { vm.load(force = true) }
                    EmptyState(
                        icon = Icons.Filled.School,
                        title = stringResource(R.string.courses_empty_title),
                        subtitle = stringResource(R.string.courses_empty_subtitle),
                    )
                }
                return
            }
            Column(Modifier.fillMaxSize()) {
                RefreshBar(refreshing) { vm.load(force = true) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(s.courses, key = { it.slug ?: it.id?.toString() ?: it.title ?: "?" }) { course ->
                        CourseCard(
                            course = course,
                            lang = lang,
                            coverUrl = app.api.absoluteUrl(course.cover_url),
                        ) { slug -> slug?.let(onOpenCourse) }
                    }
                }
            }
        }
    }
}

/** Header with title + explicit refresh button (all buttons stay tappable). */
@Composable
private fun RefreshBar(refreshing: Boolean, onRefresh: () -> Unit) {
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
        androidx.compose.material3.IconButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.retry))
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
                    Icon(
                        Icons.Filled.ConfirmationNumber,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Fmt.digits("${course.effectiveLessonCount} ${stringResource(R.string.lessons_word)}", lang),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
