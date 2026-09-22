package ir.pardava.mobile.ui.screens.course

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import ir.pardava.mobile.data.dto.LessonItemDto
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.InfoChip
import ir.pardava.mobile.ui.components.LoadingBox

/**
 * Public course page: hero cover, meta chips, description, progress for the
 * signed-in learner, and the lesson map with lock/preview/completed states.
 * Enrollment / purchase CTAs push the login screen for guests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(
    app: PardavaApp,
    slug: String,
    onBack: () -> Unit,
    onOpenLesson: (String, Long) -> Unit,
    onOpenLogin: () -> Unit,
) {
    val vm: CourseViewModel = viewModel(
        key = slug,
        factory = SimpleVmFactory2(app.api, slug) { c, s -> CourseViewModel(c, s) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()
    val signedIn = app.session.isSignedIn
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(slug) { vm.load() }

    LaunchedEffect(action) {
        when (val a = action) {
            is CourseAction.Message -> {
                snackbar.showSnackbar(a.text)
                vm.consumeAction()
            }
            is CourseAction.NeedLogin -> {
                snackbar.showSnackbar(a.message)
                vm.consumeAction()
                onOpenLogin()
            }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.course_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when (val s = state) {
            is CourseUiState.Loading -> LoadingBox()
            is CourseUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load() })
            is CourseUiState.Ready -> {
                val detail = s.detail
                val course = detail.course
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ---- hero ----
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    ) {
                        val cover = app.api.absoluteUrl(course?.cover_url)
                        if (cover != null) {
                            AsyncImage(
                                model = cover,
                                contentDescription = course?.title,
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
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.55f),
                                    ),
                                ),
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                        ) {
                            Text(
                                course?.title ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                course?.category_label?.let { InfoChip(it, container = Color(0xB3FFFFFF), content = Color.Black) }
                                course?.level_label?.let { InfoChip(it, container = Color(0xB3FFFFFF), content = Color.Black) }
                                InfoChip(
                                    Fmt.digits("${course?.effectiveLessonCount ?: 0}", lang) + " " + stringResource(R.string.lessons_word),
                                    container = Color(0xB3FFFFFF),
                                    content = Color.Black,
                                )
                            }
                        }
                    }

                    Column(Modifier.padding(16.dp)) {
                        // ---- progress (signed-in learners) ----
                        if (detail.enrolled == true) {
                            val done = detail.completedCount ?: 0
                            val total = course?.effectiveLessonCount ?: 0
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            stringResource(R.string.course_progress),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            Fmt.digits("$done/$total", lang),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { if (total > 0) done.toFloat() / total else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        Fmt.digits(
                                            "${detail.myPointsInCourse ?: 0} " + stringResource(R.string.points_word),
                                            lang,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        // ---- CTA ----
                        val enrolled = detail.enrolled == true
                        val free = course?.effectiveFree ?: true
                        when {
                            enrolled -> Unit // lessons below are the CTA
                            free -> {
                                Button(
                                    onClick = { vm.enroll(signedIn) },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.enroll_free))
                                    }
                                }
                            }
                            detail.pendingPurchase == true -> {
                                OutlinedButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) { Text(stringResource(R.string.purchase_pending)) }
                            }
                            else -> {
                                Button(
                                    onClick = { vm.purchase(signedIn) },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.purchase_request) + " — " +
                                            Fmt.digits("${course?.price ?: 0}", lang) + " " + stringResource(R.string.toman),
                                    )
                                }
                            }
                        }
                        if (!enrolled) Spacer(Modifier.height(14.dp))

                        // ---- summary / description ----
                        if (!course?.summary.isNullOrBlank()) {
                            Text(
                                course?.summary ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        if (!course?.description.isNullOrBlank()) {
                            Text(
                                course?.description ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        // ---- lessons ----
                        Text(
                            stringResource(R.string.lessons_word),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        detail.lessons.forEach { lesson ->
                            LessonRow(
                                lesson = lesson,
                                lang = lang,
                                unlocked = lesson.state != LessonItemDto.STATE_LOCKED,
                                onClick = {
                                    val id = lesson.id
                                    when {
                                        id == null -> Unit
                                        lesson.state == LessonItemDto.STATE_LOCKED -> Unit // row disabled
                                        else -> onOpenLesson(slug, id)
                                    }
                                },
                                onLockedClick = {
                                    // Locked: guests → login; enrolled → explain sequence lock
                                    if (!signedIn) {
                                        if (lesson.isFreePreview == true) {
                                            lesson.id?.let { onOpenLesson(slug, it) }
                                        } else {
                                            onOpenLogin()
                                        }
                                    } else if (detail.enrolled != true) {
                                        // Not enrolled: allow only previews
                                        if (lesson.isFreePreview == true) {
                                            lesson.id?.let { onOpenLesson(slug, it) }
                                        }
                                        // else: silently ignore — row shows lock
                                    }
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(
    lesson: LessonItemDto,
    lang: String,
    unlocked: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
) {
    val done = lesson.state == LessonItemDto.STATE_COMPLETED
    val preview = lesson.state == LessonItemDto.STATE_PREVIEW || lesson.isFreePreview == true && unlocked
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (unlocked) onClick() else onLockedClick() },
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = when {
                done -> MaterialTheme.colorScheme.tertiary
                !unlocked -> MaterialTheme.colorScheme.outline
                preview -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
            Icon(
                when {
                    done -> Icons.Filled.CheckCircle
                    !unlocked -> Icons.Filled.Lock
                    preview -> Icons.Filled.RemoveRedEye
                    else -> Icons.Filled.PlayCircleOutline
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.title ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = mutableListOf<String>()
                lesson.durationMin?.let { parts.add(Fmt.digits("$it " + stringResource(R.string.minutes_word), lang)) }
                if (preview && !done) parts.add(stringResource(R.string.badge_preview))
                if (!unlocked) parts.add(stringResource(R.string.locked_lesson))
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Factory variant for VMs needing (ApiClient, String). */
class SimpleVmFactory2(
    private val client: ir.pardava.mobile.core.ApiClient,
    private val arg: String,
    private val create: (ir.pardava.mobile.core.ApiClient, String) -> androidx.lifecycle.ViewModel,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create(client, arg) as T
}
