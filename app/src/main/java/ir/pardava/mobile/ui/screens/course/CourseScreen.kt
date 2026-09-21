package ir.pardava.mobile.ui.screens.course

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.LessonBrief

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(
    app: PardavaApp,
    slug: String,
    onBack: () -> Unit,
    onOpenLesson: (courseSlug: String, lessonSlug: String) -> Unit,
) {
    val lang = app.currentLanguage()
    val vm: CourseViewModel = viewModel(factory = CourseVmFactory(app.api, slug))
    val state by vm.state.collectAsState()

    LaunchedEffect(slug) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? CourseUiState.Ready)?.course?.title ?: ""
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is CourseUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is CourseUiState.Failure, is CourseUiState.LockedLesson -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                val msg = when (s) {
                    is CourseUiState.Failure -> s.message
                    is CourseUiState.LockedLesson -> s.message
                    else -> ""
                }
                Text(msg, color = MaterialTheme.colorScheme.error)
            }

            is CourseUiState.Ready -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                s.course.chapters.forEach { chapter ->
                    item(key = "ch-${chapter.id}") {
                        Column {
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            chapter.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(chapter.lessons, key = { "ls-${it.id}" }) { lesson ->
                        LessonRow(
                            lesson = lesson,
                            lang = lang,
                            onClick = {
                                if (!lesson.locked) onOpenLesson(s.course.slug, lesson.slug)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: LessonBrief, lang: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = !lesson.locked,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                lesson.locked -> Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.locked_lesson),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )

                lesson.completed -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.completed_lesson),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )

                else -> Spacer(Modifier.width(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (lesson.has_quiz) {
                        Text(
                            stringResource(R.string.quiz_passed) + if (lesson.quiz_passed) " ✓" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lesson.quiz_passed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (lesson.locked) {
                        lesson.unlock_requirements.forEach { req ->
                            Text(
                                req,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    } else if (lesson.best_score_percent != null) {
                        Text(
                            stringResource(R.string.quiz_score, Fmt.int(lesson.best_score_percent!!.toInt(), lang)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                Fmt.int(lesson.sort_order, lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

class CourseVmFactory(private val client: ir.pardava.mobile.core.ApiClient, private val slug: String) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        CourseViewModel(client, slug) as T
}
