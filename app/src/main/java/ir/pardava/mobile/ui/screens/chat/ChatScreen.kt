package ir.pardava.mobile.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.ChatAudioPlayer
import ir.pardava.mobile.core.ChatAudioRecorder
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.core.chatDayLabel
import ir.pardava.mobile.core.tehranClock
import ir.pardava.mobile.data.dto.SendSupportIn
import ir.pardava.mobile.data.dto.SupportMessageDto
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
private const val MAX_AUDIO_BYTES = 10L * 1024 * 1024

private val BRAND_GRADIENT = Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)))
private val BRAND_GRAY = Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF475569)))

/** One row of the chat list: a day chip or a message. */
private sealed class ChatRow {
    data class Day(val label: String) : ChatRow()
    data class Msg(val m: SupportMessageDto) : ChatRow()
}

/**
 * Support chat, messenger-grade: image + voice notes (recorded in-app), day
 * separators, Tehran-time bubbles, and an input bar that always rides above
 * the keyboard AND the system navigation bar (`navigationBarsPadding` +
 * `imePadding` on the input container itself). Login is strictly required
 * (the server answers anonymous sends with a 401 envelope).
 */
@Composable
fun ChatScreen(
    app: PardavaApp,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val signedIn by app.signedIn.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ---- header ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Box(
                Modifier
                    .size(40.dp)
                    .background(BRAND_GRADIENT, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SupportAgent,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringResource(R.string.chat_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.chat_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!signedIn) {
            GuestGate(onOpenLogin)
        } else {
            ChatBody(app)
        }
    }
}

@Composable
private fun GuestGate(onOpenLogin: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.chat_login_required),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenLogin, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(stringResource(R.string.sign_in_cta))
        }
    }
}

@Composable
private fun ChatBody(app: PardavaApp) {
    val lang = app.currentLanguage()
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<SupportMessageDto>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // attachments
    var pickedImage by remember { mutableStateOf<Uri?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    val recorder = remember { ChatAudioRecorder(context) }
    val player = remember { ChatAudioPlayer() }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var viewerImage by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pickedImage = uri
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (recorder.start() != null) {
                recording = true
                recordSeconds = 0
            } else {
                errorMessage = context.getString(R.string.chat_record_failed)
            }
        }
    }

    player.onDone = { playingId = null }

    // initial load + light polling while visible
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val resp = app.api.call { app.api.api.supportMessages() }
                messages.clear()
                messages.addAll(resp.messages)
                failed = false
            } catch (_: Exception) {
                failed = true
            }
            delay(4_000)
        }
    }

    // recording timer
    LaunchedEffect(recording) {
        while (recording) {
            delay(1_000)
            recordSeconds += 1
        }
    }

    // stick to the newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun sendText() {
        val body = input.trim()
        if (body.isEmpty() || sending) return
        sending = true
        scope.launch {
            try {
                app.api.call { app.api.api.sendSupportMessage(SendSupportIn(body)) }
                input = ""
                refresh(app, messages) { failed = it }
            } catch (e: Exception) {
                failed = true
                errorMessage = e.message
            } finally {
                sending = false
            }
        }
    }

    fun sendImage(uri: Uri) {
        if (sending) return
        sending = true
        scope.launch {
            try {
                val media = prepareImage(context, uri)
                if (media == null) {
                    errorMessage = context.getString(R.string.chat_image_failed)
                } else {
                    val (bytes, mime) = media
                    if (bytes.size > MAX_IMAGE_BYTES) {
                        errorMessage = context.getString(R.string.chat_image_too_large)
                    } else {
                        val part = MultipartBody.Part.createFormData(
                            "file", "photo.jpg", bytes.toRequestBody(mime.toMediaType()),
                        )
                        val text = input.trim()
                        app.api.call {
                            app.api.api.sendSupportMessageWithFile(
                                part,
                                text.takeIf { it.isNotEmpty() }?.toRequestBody("text/plain".toMediaType()),
                                null,
                            )
                        }
                        input = ""
                        pickedImage = null
                        refresh(app, messages) { failed = it }
                    }
                }
            } catch (e: Exception) {
                failed = true
                errorMessage = e.message
            } finally {
                sending = false
            }
        }
    }

    fun sendVoice() {
        if (sending) return
        val file = recorder.stop()
        recording = false
        if (file == null) return
        sending = true
        scope.launch {
            try {
                val bytes = file.readBytes()
                if (bytes.size > MAX_AUDIO_BYTES) {
                    errorMessage = context.getString(R.string.chat_audio_too_large)
                } else {
                    val part = MultipartBody.Part.createFormData(
                        "file", "voice.m4a", bytes.toRequestBody("audio/mp4".toMediaType()),
                    )
                    val duration = recordSeconds.coerceAtLeast(1).toString().toRequestBody("text/plain".toMediaType())
                    app.api.call { app.api.api.sendSupportMessageWithFile(part, null, duration) }
                    refresh(app, messages) { failed = it }
                }
            } catch (e: Exception) {
                failed = true
                errorMessage = e.message
            } finally {
                file.delete()
                sending = false
            }
        }
    }

    // ---- rows with day separators ----
    val rows = remember(messages.size, lang) {
        buildList {
            var lastDay: String? = null
            for (m in messages) {
                val label = chatDayLabel(m.createdAt, lang)
                if (label != null && label != lastDay) add(ChatRow.Day(label))
                if (label != null) lastDay = label
                add(ChatRow.Msg(m))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty() && !failed) {
                    item { EmptyHint() }
                }
                items(rows, key = { row ->
                    when (row) {
                        is ChatRow.Day -> "day-" + row.label
                        is ChatRow.Msg -> row.m.id ?: -(row.m.body.hashCode() + row.m.createdAt.hashCode()).toLong()
                    }
                }) { row ->
                    when (row) {
                        is ChatRow.Day -> DayChip(row.label)
                        is ChatRow.Msg -> MessageBubble(
                            m = row.m,
                            lang = lang,
                            bearer = app.session.token,
                            absoluteUrl = { app.api.absoluteUrl(it) },
                            onOpenImage = { viewerImage = it },
                            playing = playingId != null && playingId == row.m.id,
                            onTogglePlay = {
                                val id = row.m.id
                                val url = row.m.attachment?.url ?: return@MessageBubble
                                player.toggle(context, app.api.absoluteUrl(url) ?: url, app.session.token)
                                playingId = if (playingId == id) null else id
                            },
                        )
                    }
                }
            }

            // ---- input area: rides above keyboard AND the navigation bar ----
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    if (sending) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    pickedImage?.let { uri ->
                        PickedImageChip(
                            uri = uri,
                            onClear = { pickedImage = null },
                            onSend = { sendImage(uri) },
                        )
                    }
                    if (recording) {
                        RecordingBar(
                            seconds = recordSeconds,
                            lang = lang,
                            onCancel = {
                                recorder.cancel()
                                recording = false
                            },
                            onSend = { sendVoice() },
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                enabled = !sending,
                                onClick = { pickImage.launch("image/*") },
                            ) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.chat_attach_photo),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                                modifier = Modifier.weight(1f),
                                maxLines = 4,
                                shape = RoundedCornerShape(22.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            if (input.isNotBlank()) {
                                SendButton(enabled = !sending) { sendText() }
                            } else {
                                IconButton(
                                    enabled = !sending,
                                    onClick = {
                                        if (hasMicPermission()) {
                                            if (recorder.start() != null) {
                                                recording = true
                                                recordSeconds = 0
                                            } else {
                                                errorMessage = context.getString(R.string.chat_record_failed)
                                            }
                                        } else {
                                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = stringResource(R.string.chat_record),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    errorMessage?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (failed) {
            Text(
                stringResource(R.string.chat_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
            )
        }
        viewerImage?.let { url ->
            FullscreenImage(
                url = url,
                bearer = app.session.token,
                absoluteUrl = { app.api.absoluteUrl(it) },
                onDismiss = { viewerImage = null },
            )
        }
    }
}

private suspend fun refresh(
    app: PardavaApp,
    messages: SnapshotStateList<SupportMessageDto>,
    setFailed: (Boolean) -> Unit,
) {
    try {
        val resp = app.api.call { app.api.api.supportMessages() }
        messages.clear()
        messages.addAll(resp.messages)
        setFailed(false)
    } catch (_: Exception) {
        setFailed(true)
    }
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.SupportAgent,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.chat_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    m: SupportMessageDto,
    lang: String,
    bearer: String?,
    absoluteUrl: (String?) -> String?,
    onOpenImage: (String) -> Unit,
    playing: Boolean,
    onTogglePlay: () -> Unit,
) {
    val att = m.attachment
    val imageUrl = att?.takeIf { it.type == "image" }?.url?.let { absoluteUrl(it) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (!m.mine) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.SupportAgent,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.widthIn(max = 292.dp)) {
                when {
                    imageUrl != null -> BubbleImage(imageUrl, bearer, onOpenImage)
                    att?.type == "audio" -> VoiceBubble(
                        duration = att.duration ?: 0,
                        lang = lang,
                        playing = playing,
                        onToggle = onTogglePlay,
                    )
                    else -> TextBubble(m.body, mine = m.mine)
                }
            }
        }
        m.createdAt?.takeIf { it.length >= 16 }?.let {
            Text(
                tehranClock(it, lang) ?: it.substring(11, 16),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun TextBubble(body: String, mine: Boolean) {
    Box(
        Modifier
            .background(
                if (mine) BRAND_GRADIENT else BRAND_GRAY,
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (mine) 16.dp else 3.dp,
                    bottomEnd = if (mine) 3.dp else 16.dp,
                ),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) Color.White else Color(0xFFE2E8F0),
        )
    }
}

@Composable
private fun BubbleImage(url: String, bearer: String?, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .addHeader("Authorization", "Bearer ${bearer ?: ""}")
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen(url) },
    )
}

@Composable
private fun VoiceBubble(duration: Int, lang: String, playing: Boolean, onToggle: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "voice")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        Modifier
            .background(BRAND_GRADIENT, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.22f),
            modifier = Modifier
                .size(34.dp)
                .then(if (playing) Modifier.graphicsLayer(scaleX = pulse, scaleY = pulse) else Modifier),
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                stringResource(R.string.chat_voice),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Text(
                Fmt.duration(duration, lang),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PickedImageChip(uri: Uri, onClear: () -> Unit, onSend: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.chat_ready_photo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel), modifier = Modifier.size(18.dp))
        }
        SendButton(enabled = true, onClick = onSend)
    }
}

@Composable
private fun RecordingBar(seconds: Int, lang: String, onCancel: () -> Unit, onSend: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "rec")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.chat_cancel),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Box(
            Modifier
                .size(12.dp)
                .graphicsLayer(alpha = pulse)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            Fmt.duration(seconds, lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        SendButton(enabled = true, onClick = onSend)
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .background(if (enabled) BRAND_GRADIENT else BRAND_GRAY, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.chat_send),
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Decodes the picked image, downscales it when needed and JPEG-compresses
 * until it is safely under the server cap. Returns (bytes, mime) or null.
 */
private fun prepareImage(context: Context, uri: Uri): Pair<ByteArray, String>? = try {
    val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val maxDim = 2048
            val w = info.size.width
            val h = info.size.height
            if (w > maxDim || h > maxDim) {
                val scale = maxDim.toFloat() / maxOf(w, h)
                decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            }
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val scaled = if (maxOf(bitmap.width, bitmap.height) > 2048) {
        val scale = 2048f / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    } else {
        bitmap
    }
    var quality = 88
    lateinit var bytes: ByteArray
    do {
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 12
    } while (bytes.size > MAX_IMAGE_BYTES && quality > 30)
    if (scaled !== bitmap) scaled.recycle()
    bitmap.recycle()
    bytes to "image/jpeg"
} catch (_: Exception) {
    null
}

@Composable
private fun FullscreenImage(
    url: String,
    bearer: String?,
    absoluteUrl: (String?) -> String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .addHeader("Authorization", "Bearer ${bearer ?: ""}")
                    .build(),
                contentDescription = null,
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
                    .padding(14.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
            }
        }
    }
}
