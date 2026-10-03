package ir.pardava.mobile.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.ChatAudioRecorder
import ir.pardava.mobile.core.ChatAudioPlayer
import java.io.File
import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val MAX_RECORD_SECONDS = 60

/** حالت‌های صفحهٔ دستیار صوتی. */
private enum class VaPhase { IDLE, RECORDING, WORKING, RESULT }

/**
 * دستیار صوتی: کاربر می‌پرسد (صدا یا متن) → سرور پایپ‌لاین را اجرا می‌کند
 * (صوت ← متن ← چت‌بات ← صدا) → پرسش و پاسخ نمایش داده می‌شود و پاسخِ صوتی
 * (در صورت وجود) خودکار پخش می‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantScreen(
    app: PardavaApp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(VaPhase.IDLE) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var answerFile by remember { mutableStateOf<File?>(null) }
    var playing by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(1f) }

    val recorder = remember { ChatAudioRecorder(context) }
    val player = remember { ChatAudioPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            player.stop()
        }
    }
    player.onStateChanged = { p, _ -> playing = p }
    player.onDone = { playing = false }

    fun stopPlayback() {
        player.stop()
        playing = false
    }

    fun submit(file: File?) {
        stopPlayback()
        phase = VaPhase.WORKING
        scope.launch {
            try {
                val part = file?.let { f ->
                    val bytes = f.readBytes()
                    MultipartBody.Part.createFormData(
                        "file",
                        "voice.m4a",
                        bytes.toRequestBody("audio/mp4".toMediaType()),
                    )
                }
                val textPart = if (part == null && textInput.isNotBlank()) {
                    textInput.trim().toRequestBody("text/plain".toMediaType())
                } else {
                    null
                }
                val resp = app.api.call { app.api.api.voiceAssistantAsk(part, textPart) }
                if (resp.ok == true) {
                    question = resp.question.orEmpty()
                    answer = resp.answer.orEmpty()
                    answerFile = resp.audioB64?.takeIf { it.isNotBlank() }?.let { b64 ->
                        runCatching {
                            val out = File(context.cacheDir, "va_answer_${System.currentTimeMillis()}.mp3")
                            out.writeBytes(Base64.getDecoder().decode(b64))
                            out
                        }.getOrNull()
                    }
                    phase = VaPhase.RESULT
                    answerFile?.let { af ->
                        player.toggle(context, "file://" + af.absolutePath, app.session.token)
                    }
                } else {
                    errorMessage = when (resp.stage) {
                        "quota_exhausted", "key_quota_exhausted" -> context.getString(R.string.va_error_quota)
                        "asr_failed", "short_question", "bad_format", "too_large", "empty_audio", "no_input" ->
                            context.getString(R.string.va_error_asr)
                        "chat_unavailable" -> context.getString(R.string.va_error_chat)
                        "rate_limited" -> context.getString(R.string.va_rate_limited)
                        else -> resp.error ?: context.getString(R.string.va_error_generic)
                    }
                    phase = VaPhase.IDLE
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: context.getString(R.string.va_error_generic)
                phase = VaPhase.IDLE
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recordSeconds = 0
            if (recorder.start() != null) {
                phase = VaPhase.RECORDING
            } else {
                errorMessage = context.getString(R.string.va_record_failed)
            }
        } else {
            errorMessage = context.getString(R.string.va_mic_permission)
        }
    }

    fun startRecording() {
        stopPlayback()
        errorMessage = null
        question = ""
        answer = ""
        answerFile = null
        recordSeconds = 0
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (recorder.start() != null) {
                phase = VaPhase.RECORDING
            } else {
                errorMessage = context.getString(R.string.va_record_failed)
            }
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // recording timer + live mic level + auto-stop at 60s
    LaunchedEffect(phase) {
        if (phase != VaPhase.RECORDING) return@LaunchedEffect
        var elapsed = 0
        while (elapsed < MAX_RECORD_SECONDS) {
            delay(250)
            elapsed += 1
            recordSeconds = elapsed / 4
            level = 1f + (recorder.amplitude().coerceIn(0, 12000) / 12000f) * 0.35f
        }
        if (phase == VaPhase.RECORDING) {
            val f = recorder.stop()
            if (f != null && f.length() > 0) submit(f) else {
                errorMessage = context.getString(R.string.va_record_failed)
                phase = VaPhase.IDLE
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.va_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        recorder.cancel()
                        stopPlayback()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.va_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            when (phase) {
                VaPhase.IDLE -> MicIdle(
                    onClick = { startRecording() },
                )
                VaPhase.RECORDING -> MicRecording(
                    seconds = recordSeconds,
                    level = level,
                    onStop = {
                        val f = recorder.stop()
                        if (f != null && f.length() > 0) {
                            submit(f)
                        } else {
                            errorMessage = context.getString(R.string.va_record_failed)
                            phase = VaPhase.IDLE
                        }
                    },
                    onCancel = {
                        recorder.cancel()
                        phase = VaPhase.IDLE
                    },
                )
                VaPhase.WORKING -> WorkingIndicator()
                VaPhase.RESULT -> ResultCard(
                    question = question,
                    answer = answer,
                    playing = playing,
                    hasAudio = answerFile != null,
                    onReplay = {
                        answerFile?.let { af ->
                            player.toggle(context, "file://" + af.absolutePath, app.session.token)
                        }
                    },
                    onNewQuestion = { startRecording() },
                )
            }

            errorMessage?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        msg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // پرسش متنی جایگزین — وقتی میکروفون در دسترس نیست یا ترجیح داده می‌شود
            if (phase == VaPhase.IDLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                stringResource(R.string.va_text_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2,
                    )
                    Spacer(Modifier.size(8.dp))
                    FilledIconButton(
                        onClick = { submit(null) },
                        enabled = textInput.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.va_send))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ── زیرمؤلفه‌ها ─────────────────────────────────────────────────────────── */

@Composable
private fun MicIdle(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MicButton(onClick = onClick)
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.va_tap_to_ask),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MicRecording(seconds: Int, level: Float, onStop: () -> Unit, onCancel: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(pulse * level)
                    .alpha(glow)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.28f), CircleShape),
            )
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(84.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.va_stop),
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.va_listening) + "  •  " + secondsLabel(seconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.va_cancel)) }
    }
}

@Composable
private fun WorkingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 5.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.va_thinking),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.va_thinking_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultCard(
    question: String,
    answer: String,
    playing: Boolean,
    hasAudio: Boolean,
    onReplay: () -> Unit,
    onNewQuestion: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (question.isNotBlank()) {
            Bubble(text = question, isUser = true)
        }
        Spacer(Modifier.height(10.dp))
        Bubble(text = answer, isUser = false)
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (hasAudio) {
                FilledIconButton(onClick = onReplay) {
                    Icon(
                        if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.va_replay),
                    )
                }
                Spacer(Modifier.size(10.dp))
            }
            FilledIconButton(onClick = onNewQuestion) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.va_new_question),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (playing) stringResource(R.string.va_playing) else stringResource(R.string.va_tap_again),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Bubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(if (isUser) R.string.va_you else R.string.va_assistant),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MicButton(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "idle")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "idleAlpha",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .alpha(glow)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape),
        )
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(84.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = stringResource(R.string.va_ask),
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

private fun secondsLabel(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
