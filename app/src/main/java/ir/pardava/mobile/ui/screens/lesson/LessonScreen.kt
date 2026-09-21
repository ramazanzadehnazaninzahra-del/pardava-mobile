package ir.pardava.mobile.ui.screens.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory
import kotlinx.coroutines.delay

private val TAB_COUNT = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    app: PardavaApp,
    courseSlug: String,
    lessonSlug: String,
    onBack: () -> Unit,
    onOpenQuiz: (courseSlug: String, lessonSlug: String, quizId: Long) -> Unit,
) {
    val lang = app.currentLanguage()
    val vm: LessonViewModel = viewModel(
        key = "$courseSlug/$lessonSlug",
        factory = LessonVmFactory(app.api, courseSlug, lessonSlug),
    )
    val state by vm.state.collectAsState()
    val media by vm.media.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(courseSlug, lessonSlug) { vm.load(lang) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? LessonUiState.Ready)?.lesson?.title ?: ""
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
            is LessonUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is LessonUiState.Failure -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(s.message, color = MaterialTheme.colorScheme.error) }

            is LessonUiState.Ready -> if (s.lesson.locked) {
                LockedPanel(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    requirements = s.lesson.unlock_requirements,
                    lang = lang,
                )
            } else {
                UnlockedLesson(
                    app = app,
                    vm = vm,
                    s = s,
                    media = media,
                    lang = lang,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    tab = tab,
                    onTab = { tab = it },
                    onOpenQuiz = onOpenQuiz,
                )
            }
        }
    }
}

@Composable
private fun LockedPanel(modifier: Modifier, requirements: Map<String, Map<String, String>>, lang: String) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = stringResource(R.string.locked_lesson),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.error_locked_content), style = MaterialTheme.typography.titleMedium)
        requirements.forEach { (_, texts) ->
            Text(
                if (lang == "en") texts["en"] ?: texts["fa"] ?: "" else texts["fa"] ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnlockedLesson(
    app: PardavaApp,
    vm: LessonViewModel,
    s: LessonUiState.Ready,
    media: LessonMedia?,
    lang: String,
    modifier: Modifier,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenQuiz: (String, String, Long) -> Unit,
) {
    val lesson = s.lesson
    val context = LocalContext.current
    val player = remember(media?.videoUrl?.url) {
        media?.let { m ->
            val item = buildMediaItem(app, m)
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(item)
                prepare()
                playWhenReady = false
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages(m.videoUrl.lang)
                    .build()
            }
        }
    }

    // Periodic progress reporting every 15 s while the lesson is open.
    LaunchedEffect(player, lesson.id) {
        if (player == null) return@LaunchedEffect
        while (true) {
            delay(15_000)
            val duration = player.duration.takeIf { it > 0 } ?: return@LaunchedEffect
            val position = player.currentPosition.coerceAtLeast(0)
            val percent = ((position * 100.0) / duration).toInt().coerceIn(0, 100)
            vm.reportProgress(lesson, media?.videoUrl?.lang ?: lang, (position / 1000).toInt(), percent)
        }
    }

    DisposableEffect(player) { onDispose { player?.release() } }

    Column(modifier.verticalScroll(rememberScrollState())) {
        if (player != null) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.subtitles_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, onClick = { onTab(0) }, text = { Text(stringResource(R.string.lesson_body)) })
            Tab(tab == 1, onClick = { onTab(1) }, text = { Text(stringResource(R.string.lesson_code)) })
            Tab(tab == 2, onClick = { onTab(2) }, text = { Text(stringResource(R.string.lesson_exercise)) })
            Tab(tab == 3, onClick = { onTab(3) }, text = { Text(stringResource(R.string.lesson_quiz)) })
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> SectionText(lesson.body)
                1 -> CodeBlock(lesson.code_sample)
                2 -> SectionText(lesson.exercise)
                3 -> lesson.quiz?.let { quiz ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(quiz.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    R.string.quiz_meta,
                                    Fmt.int(quiz.question_count, lang),
                                    Fmt.int((quiz.duration_seconds ?: 0) / 60, lang),
                                    Fmt.int(quiz.pass_score_percent, lang),
                                    Fmt.int(quiz.attempts_used ?: 0, lang),
                                    Fmt.int(quiz.max_attempts, lang),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val exhausted = (quiz.attempts_used ?: 0) >= quiz.max_attempts
                            if (!exhausted) {
                                Button(onClick = { onOpenQuiz(lesson.course_slug, lesson.slug, quiz.id) }) {
                                    Text(
                                        if ((quiz.attempts_used ?: 0) > 0) stringResource(R.string.quiz_resume)
                                        else stringResource(R.string.quiz_start),
                                    )
                                }
                            } else {
                                Text(stringResource(R.string.quiz_no_attempts), color = MaterialTheme.colorScheme.error)
                            }
                            if (lesson.quiz_passed) {
                                Text(
                                    stringResource(R.string.quiz_passed) + " ✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else if (lesson.best_score_percent != null) {
                                Text(
                                    stringResource(R.string.quiz_score, Fmt.int(lesson.best_score_percent!!.toInt(), lang)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            if (lesson.fallback_used) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.fallback_translation_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionText(text: String?) {
    if (text.isNullOrBlank()) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CodeBlock(code: String?) {
    if (code.isNullOrBlank()) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Text(
            code,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun buildMediaItem(app: PardavaApp, m: LessonMedia): MediaItem {
    val videoUri = android.net.Uri.parse(app.api.absoluteUrl(m.videoUrl.url))
    val subtitleConfig = m.subtitle?.let { sub ->
        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(app.api.absoluteUrl(sub.url)))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(m.videoUrl.lang)
            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
            .build()
    }
    return MediaItem.Builder()
        .setUri(videoUri)
        .apply { subtitleConfig?.let { setSubtitleConfigurations(listOf(it)) } }
        .build()
}

class LessonVmFactory(
    private val client: ir.pardava.mobile.core.ApiClient,
    private val courseSlug: String,
    private val lessonSlug: String,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        LessonViewModel(client, courseSlug, lessonSlug) as T
}
