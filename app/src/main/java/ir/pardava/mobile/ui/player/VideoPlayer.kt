package ir.pardava.mobile.ui.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import kotlinx.coroutines.delay

private val SPEEDS = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Professional lesson video player (Media3/ExoPlayer):
 * - streams the site lesson video with the session Bearer header
 * - resumes from the server-saved watch position, reports progress while playing
 * - custom controls: play/pause, ±10s, speed cycle, fullscreen, localized digits
 * - keep-screen-on while playing, auto-hiding controls, immersive fullscreen
 */
@OptIn(UnstableApi::class)
@Composable
fun LessonVideoPlayer(
    url: String,
    bearerToken: String?,
    resumePositionSec: Double,
    lang: String,
    onProgressTick: (positionSec: Double, durationSec: Double) -> Unit,
    onEvent: (name: String, detail: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var speedIndex by remember { mutableStateOf(1) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    var errorName by remember { mutableStateOf<String?>(null) }
    val activity = context as? android.app.Activity

    // ---- player lifecycle ----
    DisposableEffect(url) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        if (!bearerToken.isNullOrBlank()) {
            dataSourceFactory.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $bearerToken"))
        }
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = false
        player = exo

        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // surface failures visibly (and measure them) instead of a silent black box
                isBuffering = false
                errorName = error.errorCodeName
                onEvent("app_video_error", mapOf("code" to error.errorCodeName.take(60)))
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    onEvent("app_video_play", mapOf("url" to url.takeLast(60)))
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && !resumed) {
                    resumed = true
                    if (resumePositionSec > 1.0) {
                        exo.seekTo((resumePositionSec * 1000).toLong())
                        onEvent("app_video_resume", mapOf("position" to resumePositionSec.toInt().toString()))
                    }
                }
                if (state == Player.STATE_ENDED) {
                    val d = exo.duration.coerceAtLeast(0) / 1000.0
                    onEvent("app_video_complete", mapOf("duration" to d.toInt().toString()))
                    onProgressTick(d, d)
                }
            }

            override fun onPlaybackParametersChanged(params: PlaybackParameters) {
                val idx = SPEEDS.indexOfFirst { it == params.speed }
                if (idx >= 0) speedIndex = idx
            }
        }
        exo.addListener(listener)

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val tickerRunnable = object : Runnable {
            override fun run() {
                val p = exo.currentPosition
                val d = exo.duration
                positionMs = p
                if (d > 0) durationMs = d
                if (exo.isPlaying && d > 0) {
                    onProgressTick(p / 1000.0, d / 1000.0)
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(tickerRunnable)

        onDispose {
            handler.removeCallbacks(tickerRunnable)
            exo.removeListener(listener)
            exo.release()
            player = null
        }
    }

    // ---- keep screen on while playing ----
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }

    // ---- fullscreen orientation + immersive system bars ----
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (isFullscreen) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // auto-hide controls while playing
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { pv -> pv.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (isBuffering && errorName == null) {
            CircularProgressIndicator(modifier = Modifier.size(38.dp), color = Color.White, strokeWidth = 3.dp)
        }

        if (errorName != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.player_error),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.clickable {
                        val p = player ?: return@clickable
                        errorName = null
                        isBuffering = true
                        p.seekToDefaultPosition()
                        p.prepare()
                    },
                ) {
                    Text(
                        stringResource(R.string.player_retry),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
                // center: replay / play / forward
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { player?.seekTo((positionMs - 10_000).coerceAtLeast(0)) }) {
                        Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.player_back_10), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                        IconButton(onClick = {
                            val p = player ?: return@IconButton
                            if (p.isPlaying) {
                                p.pause()
                                onEvent("app_video_pause", mapOf("position" to (positionMs / 1000).toInt().toString()))
                            } else {
                                p.play()
                            }
                        }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.player_play_pause),
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    IconButton(onClick = { player?.seekTo((positionMs + 10_000).coerceAtMost(durationMs)) }) {
                        Icon(Icons.Filled.Forward10, contentDescription = stringResource(R.string.player_forward_10), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                // speed + fullscreen (top corner)
                Row(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.45f)) {
                        Row(
                            Modifier.clickable {
                                speedIndex = (speedIndex + 1) % SPEEDS.size
                                val speed = SPEEDS[speedIndex]
                                player?.setPlaybackSpeed(speed)
                                onEvent("app_video_speed", mapOf("speed" to speed.toString()))
                            }.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                Fmt.digits(SPEEDS[speedIndex].let { if (it == it.toLong().toFloat()) "${it.toLong()}x" else "${it}x" }, lang),
                                color = Color.White,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.45f)) {
                        IconButton(onClick = { isFullscreen = !isFullscreen }) {
                            Icon(
                                if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = stringResource(R.string.player_fullscreen),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // bottom: seek bar + times
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Slider(
                        value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        onValueChange = { frac ->
                            if (durationMs > 0) player?.seekTo((frac * durationMs).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth().height(26.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            Fmt.digits(fmtTime(positionMs), lang),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Text(
                            Fmt.digits(fmtTime(durationMs), lang),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** mm:ss (or h:mm:ss). */
private fun fmtTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
