package ir.pardava.mobile.ui.screens.courses

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.CourseDto
import ir.pardava.mobile.ui.components.LanguageSwitchRow

@Composable
fun CoursesScreen(app: PardavaApp, onOpenCourse: (String) -> Unit) {
    val lang = app.currentLanguage()
    val vm: CoursesViewModel = viewModel(factory = SimpleVmFactory(app.api) { CoursesViewModel(it) })
    val state by vm.state.collectAsState()
    val signedIn by app.session.signedIn.collectAsState()

    // Reload whenever the tab opens or the session changes (enrolled flags).
    LaunchedEffect(signedIn) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.courses_title), style = MaterialTheme.typography.titleLarge)
            LanguageSwitchRow(app = app)
        }

        when (val s = state) {
            is CoursesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is CoursesUiState.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                }
            }

            is CoursesUiState.Ready -> {
                if (s.courses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.courses_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.courses, key = { it.slug.ifEmpty { it.id.toString() } }) { course ->
                            CourseCard(course, lang, onClick = { onOpenCourse(course.slug) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: CourseDto, lang: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    course.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (course.enrolled) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.course_enrolled),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (course.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    course.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = onClick,
                    label = { Text(priceLabel(course, lang), style = MaterialTheme.typography.labelMedium) },
                )
                Text(
                    stringResource(R.string.lessons_count, Fmt.int(course.lesson_count, lang)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (course.level_label.isNotBlank()) {
                    Text(
                        course.level_label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun priceLabel(course: CourseDto, lang: String): String =
    if (course.is_free || course.price == 0L) {
        stringResource(R.string.course_free)
    } else {
        stringResource(R.string.course_price, Fmt.digits(course.price.toString(), lang))
    }
