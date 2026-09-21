package ir.pardava.mobile.ui.screens.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

@Composable
fun LessonScreen(
    app: PardavaApp,
    slug: String,
    lessonId: Long,
    onBack: () -> Unit,
    onGoLogin: () -> Unit,
    onOpenLesson: (String, Long) -> Unit,
) {
    val vm: LessonViewModel = viewModel(factory = SimpleVmFactory(app.api) { LessonViewModel(it) })
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    val download by vm.download.collectAsState()
    val signedIn by app.session.signedIn.collectAsState()
    val lang = app.currentLanguage()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(slug, lessonId) { vm.bind(slug, lessonId) }
    // Reload after returning from the login screen.
    LaunchedEffect(signedIn) { if (lessonId != 0L) vm.load() }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }
    LaunchedEffect(download) {
        val d = download
        if (d is DownloadState.Failed) {
            snackbar.showSnackbar(d.message)
            vm.consumeDownloadResult()
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
                    (state as? LessonUiState.Ready)?.detail?.course?.title
                        ?: stringResource(R.string.lesson_body),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
            }

            when (val s = state) {
                is LessonUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is LessonUiState.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                    }
                }

                is LessonUiState.Ready -> {
                    val lesson = s.detail.lesson
                    if (lesson == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.error_generic))
                        }
                        return@Scaffold
                    }
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            lesson.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (lesson.duration_min > 0) {
                                Text(
                                    stringResource(R.string.minutes_short, Fmt.int(lesson.duration_min, lang)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (lesson.state == "preview") {
                                Text(
                                    stringResource(R.string.lesson_state_preview),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            if (lesson.state == "done") {
                                Text(
                                    stringResource(R.string.lesson_state_done),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (!lesson.description.isNullOrBlank()) {
                            Text(lesson.description, style = MaterialTheme.typography.bodyMedium)
                        }

                        if (!signedIn) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(stringResource(R.string.lesson_login_banner), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = onGoLogin) { Text(stringResource(R.string.go_login)) }
                                }
                            }
                        }

                        if (lesson.file != null && (lesson.file.url != null || signedIn)) {
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(lesson.file.name.ifBlank { stringResource(R.string.lesson_file) }, style = MaterialTheme.typography.bodyMedium)
                                        if (lesson.file.size > 0) {
                                            Text(
                                                Fmt.digits(formatSize(lesson.file.size), lang),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    when {
                                        download == DownloadState.Running -> CircularProgressIndicator(
                                            Modifier.height(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        else -> TextButton(onClick = { vm.downloadFile(context) }) {
                                            Text(stringResource(R.string.lesson_file_download))
                                        }
                                    }
                                }
                            }
                        }

                        if (signedIn && lesson.state != "done") {
                            Button(
                                onClick = { vm.complete() },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.lesson_complete_button))
                                }
                            }
                        } else if (lesson.state == "done") {
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                Text(stringResource(R.string.lesson_state_done))
                            }
                        }

                        // Prev / next navigation
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (lesson.prev_id != null) {
                                OutlinedButton(onClick = { onOpenLesson(slug, lesson.prev_id!!) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                    Text(stringResource(R.string.lesson_prev))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            if (lesson.next_id != null) {
                                OutlinedButton(onClick = { onOpenLesson(slug, lesson.next_id!!) }) {
                                    Text(stringResource(R.string.lesson_next))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
