package ir.pardava.mobile.ui.screens.lesson

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.BodySegment
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.core.LessonBody
import ir.pardava.mobile.data.dto.LessonAttachmentDto
import ir.pardava.mobile.data.dto.LessonContentDto
import ir.pardava.mobile.data.dto.LessonItemDto
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.LoadingBox
import ir.pardava.mobile.ui.player.LessonVideoPlayer

/**
 * Lesson reader: rich text content, completion with point award, prev/next
 * navigation and attachment download. Open lessons (preview / unlocked) are
 * viewable without an account; completing requires one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    app: PardavaApp,
    slug: String,
    lessonId: Long,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val vm: LessonViewModel = viewModel(
        key = "$slug/$lessonId",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                LessonViewModel(app.api, slug, lessonId) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()
    val signedIn by app.signedIn.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(slug, lessonId) { vm.load() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // ---- video fullscreen state machine (hoisted here: while fullscreen the
    //      whole screen collapses to the player so the video slot truly expands,
    //      the top bar disappears and system bars hide) ----
    var fullscreen by remember { mutableStateOf(false) }
    var landscapeLatch by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val config = LocalConfiguration.current
    val activity = LocalContext.current as? Activity
    val ready = state as? LessonUiState.Ready
    val readyLesson = ready?.lesson?.lesson
    val videoUrl = ready?.let { vm.videoUrl() }
    val showVideo = readyLesson?.hasVideo == true && videoUrl != null &&
        readyLesson.state != LessonItemDto.STATE_LOCKED

    fun exitFullscreen() {
        fullscreen = false
        landscapeLatch = true
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // auto fullscreen on physical rotation to landscape, auto-exit on portrait.
    // landscapeLatch blocks instant re-entry after a manual exit while the
    // device is still held in landscape; it clears as soon as portrait returns.
    LaunchedEffect(config.orientation, showVideo) {
        when (config.orientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                if (showVideo && !fullscreen && !landscapeLatch) fullscreen = true
            else -> {
                landscapeLatch = false
                if (fullscreen) fullscreen = false
            }
        }
    }

    BackHandler(enabled = fullscreen) { exitFullscreen() }

    // never leave the activity stuck in a locked orientation
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text(stringResource(R.string.lesson_body), maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
    ) { padding ->
        when (val s = state) {
            is LessonUiState.Loading -> LoadingBox()
            is LessonUiState.Failure -> when (s.action) {
                "enroll" -> AccessGate(
                    icon = { Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                    message = s.message,
                    busy = busy,
                    ctaLabel = stringResource(R.string.enroll_continue),
                    onCta = { vm.enroll(signedIn, onOpenLogin) },
                )
                "login" -> AccessGate(
                    icon = { Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                    message = s.message,
                    busy = busy,
                    ctaLabel = stringResource(R.string.sign_in_cta),
                    onCta = onOpenLogin,
                )
                else -> ErrorState(message = s.message, onRetry = { vm.load() })
            }
            is LessonUiState.Ready -> {
                val lesson = s.lesson.lesson
                if (lesson == null) {
                    ErrorState(message = stringResource(R.string.error_generic), onRetry = { vm.load() })
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(if (fullscreen) PaddingValues(0.dp) else padding),
                    ) {
                        // ---- video player: collapses the whole screen when fullscreen,
                        //      same composable slot either way (no player re-creation) ----
                        if (showVideo && videoUrl != null) {
                            androidx.compose.runtime.key(videoUrl) {
                                LessonVideoPlayer(
                                    url = videoUrl,
                                    bearerToken = app.session.token,
                                    resumePositionSec = vm.resumeSec.collectAsStateWithLifecycle().value ?: 0.0,
                                    lang = lang,
                                    isFullscreen = fullscreen,
                                    onToggleFullscreen = { enter ->
                                        if (enter) {
                                            fullscreen = true
                                            activity?.requestedOrientation =
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            exitFullscreen()
                                        }
                                    },
                                    onProgressTick = { pos, dur -> vm.saveProgress(pos, dur) },
                                    onEvent = { name, detail -> app.logger.log(name, label = lesson.title, detail = detail) },
                                    modifier = if (fullscreen) {
                                        Modifier.fillMaxSize()
                                    } else {
                                        Modifier.fillMaxWidth().height(220.dp)
                                    },
                                )
                            }
                        }
                        if (!fullscreen) {
                            LessonContent(
                                app = app,
                                lesson = lesson,
                                lang = lang,
                                signedIn = signedIn,
                                busy = busy,
                                scrollState = scrollState,
                                onComplete = { vm.complete(signedIn, onNeedLogin = onOpenLogin) },
                                onDownload = { vm.downloadFile(app) },
                                onDownloadAttachment = { att -> vm.downloadAttachment(app, att) },
                                onEnroll = { vm.enroll(signedIn, onOpenLogin) },
                                onPrev = { lesson.prevId?.let { id -> vm.openLesson(id) } },
                                onNext = { lesson.nextId?.let { id -> vm.openLesson(id) } },
                                onOpenLogin = onOpenLogin,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lesson access gate: shown when the server answers action=enroll / action=login.
 * Offers the one-tap fix instead of a dead error state.
 */
@Composable
private fun AccessGate(
    icon: @Composable () -> Unit,
    message: String,
    busy: Boolean,
    ctaLabel: String,
    onCta: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                icon()
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onCta,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text(ctaLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonContent(
    app: PardavaApp,
    lesson: LessonContentDto,
    lang: String,
    signedIn: Boolean,
    busy: Boolean,
    scrollState: ScrollState,
    onComplete: () -> Unit,
    onDownload: () -> Unit,
    onDownloadAttachment: (LessonAttachmentDto) -> Unit,
    onEnroll: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Column(Modifier.padding(16.dp)) {
        Text(
            lesson.title ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lesson.durationMin?.let {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Fmt.digits("$it " + stringResource(R.string.minutes_word), lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lesson.state == LessonItemDto.STATE_PREVIEW) {
                InfoChipSmall(stringResource(R.string.badge_preview), MaterialTheme.colorScheme.secondary)
            } else if (lesson.state == LessonItemDto.STATE_DONE) {
                InfoChipSmall(stringResource(R.string.completed_lesson), MaterialTheme.colorScheme.tertiary)
            } else if (lesson.state == LessonItemDto.STATE_LOCKED) {
                InfoChipSmall(stringResource(R.string.locked_lesson), MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- body: plain text with optional ![alt](url) in-text images ----
        val body = lesson.description ?: ""
        LessonBody.parse(body).forEach { segment ->
            when (segment) {
                is BodySegment.Text -> {
                    segment.value.split("\n\n").forEach { paragraph ->
                        val text = paragraph.trim()
                        if (text.isNotEmpty()) {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                is BodySegment.Image -> {
                    AsyncImage(
                        model = app.api.absoluteUrl(segment.url),
                        contentDescription = segment.alt.ifBlank { null },
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- actions ----
        val completed = lesson.state == LessonItemDto.STATE_DONE
        val preview = lesson.state == LessonItemDto.STATE_PREVIEW
        val locked = lesson.state == LessonItemDto.STATE_LOCKED

        if (locked) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.error_locked_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (!completed && !preview && lesson.hasVideo != true) {
            // Manual completion exists only for lessons without a video —
            // video lessons auto-complete from watch time (server-side).
            Button(
                onClick = onComplete,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.complete_lesson))
                }
            }
        } else if (completed) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        stringResource(R.string.lesson_completed_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else if (preview) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(
                            if (signedIn) R.string.preview_notice_signed_in else R.string.preview_notice
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        // Signed-in users enroll in place (server-side action);
                        // only guests are routed to the login screen.
                        onClick = onEnroll,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (signedIn) R.string.enroll_continue else R.string.sign_in_cta
                            )
                        )
                    }
                }
            }
        }

        // ---- attachment ----
        if (lesson.hasFile == true) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDownload,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(lesson.fileName ?: stringResource(R.string.lesson_file))
            }
        }

        // ---- multi attachments: images show inline, files download ----
        val context = LocalContext.current
        var viewerAtt by remember { mutableStateOf<LessonAttachmentDto?>(null) }
        if (lesson.attachments.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.attachments_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            lesson.attachments.forEach { att ->
                val absUrl = app.api.absoluteUrl(att.url)
                if (att.isImageAtt && absUrl != null) {
                    val model = ImageRequest.Builder(context)
                        .data(absUrl)
                        .addHeader("Authorization", "Bearer ${app.session.token ?: ""}")
                        .build()
                    AsyncImage(
                        model = model,
                        contentDescription = att.title,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewerAtt = att },
                    )
                } else {
                    OutlinedButton(
                        onClick = { onDownloadAttachment(att) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(att.title ?: stringResource(R.string.lesson_file), maxLines = 1)
                    }
                }
            }
        }
        viewerAtt?.let { att ->
            AttachmentViewer(app = app, attachment = att, onDismiss = { viewerAtt = null })
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))

        // ---- prev / next ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lesson.prevId != null) {
                OutlinedButton(onClick = onPrev, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.prev_lesson))
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            if (lesson.nextId != null) {
                val nextLocked = lesson.nextState == LessonItemDto.STATE_LOCKED
                OutlinedButton(
                    onClick = onNext,
                    enabled = !nextLocked,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.next_lesson))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoChipSmall(text: String, tint: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
    }
}

/** Full-screen attachment image viewer (gated URL → loaded with the Bearer token). */
@Composable
private fun AttachmentViewer(
    app: PardavaApp,
    attachment: LessonAttachmentDto,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(app.api.absoluteUrl(attachment.url))
                    .addHeader("Authorization", "Bearer ${app.session.token ?: ""}")
                    .build(),
                contentDescription = attachment.title,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .clickable(onClick = { /* keep open inside the image */ }),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.back),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}
