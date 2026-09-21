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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.CourseDto
import ir.pardava.mobile.data.dto.LessonDto
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

@Composable
fun CourseScreen(
    app: PardavaApp,
    slug: String,
    onBack: () -> Unit,
    onOpenLesson: (String, Long) -> Unit,
    onGoLogin: () -> Unit,
) {
    val vm: CourseViewModel = viewModel(factory = SimpleVmFactory(app.api) { CourseViewModel(it) })
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    val signedIn by app.session.signedIn.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(slug) { vm.bind(slug) }
    // Refresh when the user comes back from the login screen.
    LaunchedEffect(signedIn) { if (slug.isNotEmpty()) vm.load() }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    stringResource(R.string.course_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            when (val s = state) {
                is CourseUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is CourseUiState.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                    }
                }

                is CourseUiState.Ready -> CourseContent(
                    app = app,
                    detail = s.detail,
                    signedIn = signedIn,
                    busy = busy,
                    onEnroll = { vm.enroll() },
                    onPurchase = { vm.requestPurchase() },
                    onGoLogin = onGoLogin,
                    onOpenLesson = onOpenLesson,
                )
            }
        }
    }
}

@Composable
private fun CourseContent(
    app: PardavaApp,
    detail: ir.pardava.mobile.data.dto.CourseDetailOut,
    signedIn: Boolean,
    busy: Boolean,
    onEnroll: () -> Unit,
    onPurchase: () -> Unit,
    onGoLogin: () -> Unit,
    onOpenLesson: (String, Long) -> Unit,
) {
    val course = detail.course ?: return
    val lang = app.currentLanguage()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CourseHeader(course, lang) }

        item {
            when {
                detail.enrolled -> EnrolledBanner(detail, course, lang)
                !signedIn -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.course_login_cta), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onGoLogin) { Text(stringResource(R.string.go_login)) }
                    }
                }
                course.is_free || course.price == 0L -> Button(
                    onClick = onEnroll,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.course_enroll))
                    }
                }
                detail.pending_purchase != null -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.purchase_pending), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> OutlinedButton(
                    onClick = onPurchase,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.course_purchase_request))
                }
            }
        }

        item {
            Text(
                stringResource(R.string.course_lessons),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        items(detail.lessons, key = { it.id }) { lesson ->
            LessonRow(lesson, lang, onClick = {
                when {
                    lesson.isAccessible -> onOpenLesson(course.slug, lesson.id)
                    !signedIn -> onGoLogin()
                    // state == "enroll" while signed in → surface the CTA again
                    else -> if (course.is_free || course.price == 0L) onEnroll() else onPurchase()
                }
            })
        }
    }
}

@Composable
private fun CourseHeader(course: CourseDto, lang: String) {
    Column {
        Text(course.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (course.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(course.summary, style = MaterialTheme.typography.bodyMedium)
        }
        if (!course.description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(course.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.lessons_count, Fmt.int(course.lesson_count, lang)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (course.total_minutes > 0) {
                Text(
                    stringResource(R.string.minutes_total, Fmt.int(course.total_minutes, lang)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun EnrolledBanner(detail: ir.pardava.mobile.data.dto.CourseDetailOut, course: CourseDto, lang: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.course_progress, Fmt.int(detail.completed_count, lang), Fmt.int(course.lesson_count, lang)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (course.lesson_count > 0) {
                LinearProgressIndicator(
                    progress = { detail.completed_count.toFloat() / course.lesson_count },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (detail.pending_purchase != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.purchase_pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun stateIcon(state: String): ImageVector = when (state) {
    "done" -> Icons.Filled.CheckCircle
    "open" -> Icons.Filled.PlayCircle
    "preview" -> Icons.Filled.Visibility
    "enroll" -> Icons.Filled.Person
    else -> Icons.Filled.Lock
}

@Composable
private fun stateLabel(state: String): String = when (state) {
    "done" -> stringResource(R.string.lesson_state_done)
    "open" -> stringResource(R.string.lesson_state_open)
    "preview" -> stringResource(R.string.lesson_state_preview)
    "enroll" -> stringResource(R.string.lesson_state_enroll)
    else -> stringResource(R.string.lesson_state_locked)
}

@Composable
private fun LessonRow(lesson: LessonDto, lang: String, onClick: () -> Unit) {
    val accessible = lesson.isAccessible
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                stateIcon(lesson.state),
                contentDescription = stateLabel(lesson.state),
                tint = if (accessible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    "${Fmt.digits(lesson.position.toString(), lang)}. ${lesson.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (lesson.duration_min > 0) {
                        Text(
                            stringResource(R.string.minutes_short, Fmt.int(lesson.duration_min, lang)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stateLabel(lesson.state),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (accessible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
