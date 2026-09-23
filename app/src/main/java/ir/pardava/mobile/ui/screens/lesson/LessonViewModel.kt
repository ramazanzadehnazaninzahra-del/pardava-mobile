package ir.pardava.mobile.ui.screens.lesson

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.ApiException
import ir.pardava.mobile.data.dto.LessonContentResponse
import ir.pardava.mobile.data.dto.WatchIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data class Ready(val lesson: LessonContentResponse) : LessonUiState

    /** [action] mirrors the server envelope (enroll/login/purchase) so the UI can offer a fix. */
    data class Failure(val message: String, val action: String? = null) : LessonUiState
}

class LessonViewModel(
    private val client: ApiClient,
    private val slug: String,
    private var lessonId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val state: StateFlow<LessonUiState> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** Server-saved playback position to resume from (null → start from scratch). */
    private val _resumeSec = MutableStateFlow<Double?>(null)
    val resumeSec: StateFlow<Double?> = _resumeSec

    /** One-shot event: completion succeeded → refresh the parent course. */
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed

    // watch-progress save throttle: at most one POST every SAVE_INTERVAL_MS
    private var lastSavedAtMs = 0L
    private var lastSavedPosSec = -1.0

    fun consumeMessage() { _message.value = null }

    /** Absolute streaming URL for the lesson video (Range-capable endpoint). */
    fun videoUrl(): String? =
        client.session.baseUrl.trimEnd('/').let { "$it/api/courses/$slug/lessons/$lessonId/video" }

    /** In-place prev/next navigation within the same screen. */
    fun openLesson(newId: Long) {
        if (newId != lessonId) {
            lessonId = newId
            _resumeSec.value = null
            _completed.value = false
            lastSavedAtMs = 0L
            lastSavedPosSec = -1.0
            load()
        }
    }

    fun load() {
        _state.value = LessonUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = LessonUiState.Ready(client.call { client.api.lesson(slug, lessonId) })
                // resume position for signed-in users (fire-and-forget)
                if (client.session.isSignedIn) {
                    viewModelScope.launch {
                        runCatching {
                            val progress = client.call { client.api.watchProgress(slug, lessonId) }
                            progress.watch?.position?.let { pos ->
                                if (pos > 1.0) _resumeSec.value = pos
                            }
                        }
                    }
                }
            } catch (e: ApiException) {
                _state.value = LessonUiState.Failure(e.message, e.action)
            } catch (e: Exception) {
                _state.value = LessonUiState.Failure(e.message ?: "error")
            }
        }
    }

    /**
     * Free-course enrollment straight from the lesson gate (server answers
     * action=purchase for paid courses — that message is surfaced verbatim).
     * Guests are routed to the login screen.
     */
    fun enroll(signedIn: Boolean, onNeedLogin: () -> Unit) {
        if (!signedIn) {
            onNeedLogin()
            return
        }
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val res = client.call { client.api.enroll(slug) }
                _message.value = res.message ?: "ثبت‌نام انجام شد."
                load()
            } catch (e: ApiException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = e.message ?: "خطا"
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Periodic watch-progress save from the player ticker (fire-and-forget,
     * server-throttled). Guests are skipped — there is no identity to attach.
     *
     * Auto-completion: the server marks the lesson complete (and awards the
     * points, idempotently) once watch time reaches 90% or the video ends —
     * the flag arrives in this response, no extra request is needed.
     */
    fun saveProgress(positionSec: Double, durationSec: Double) {
        if (!client.session.isSignedIn) return
        if (positionSec <= 0) return
        val atEnd = durationSec > 0 && positionSec >= durationSec - 0.75
        val now = System.currentTimeMillis()
        if (!atEnd) {
            if (now - lastSavedAtMs < SAVE_INTERVAL_MS) return
            if ((positionSec - lastSavedPosSec).let { it >= 0 && it < 1.0 } && lastSavedPosSec >= 0) return
        }
        lastSavedAtMs = now
        lastSavedPosSec = positionSec
        viewModelScope.launch {
            runCatching {
                val res = client.call {
                    client.api.saveWatchProgress(slug, lessonId, WatchIn(positionSec, durationSec, completed = atEnd))
                }
                if (res.completed == true && !_completed.value) {
                    _completed.value = true
                    val points = res.points ?: 0
                    _message.value =
                        if (points > 0) "درس تکمیل شد و $points امتیاز دریافت کردید." else "درس تکمیل شد."
                    load() // lesson state (✓ و باز شدن درس بعدی) تازه شود
                }
            }
        }
    }

    /** Marks the lesson complete and awards points. Guests are asked to log in first. */
    fun complete(signedIn: Boolean, onNeedLogin: () -> Unit) {
        if (!signedIn) {
            _message.value = "برای ثبت پیشرفت و دریافت امتیاز ابتدا وارد حساب شوید."
            onNeedLogin()
            return
        }
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val res = client.call { client.api.complete(slug, lessonId) }
                _message.value = res.message
                    ?: (res.pointsAwarded?.let { "$it امتیاز گرفتید!" }
                        ?: "جلسه تکمیل شد. آفرین!")
                _completed.value = true
                load()
            } catch (e: ApiException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = e.message ?: "خطا"
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Streams the lesson attachment to the public Downloads folder via
     * MediaStore (no storage permission needed on API 29+; direct file on older).
     */
    fun downloadFile(context: Context) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val url = client.absoluteUrl("api/courses/$slug/lessons/$lessonId/file")!!
                val saved = withContext(Dispatchers.IO) { saveToDownloads(context, url) }
                _message.value = if (saved) "فایل در پوشهٔ Downloads ذخیره شد." else "فایلی برای این جلسه موجود نیست."
            } catch (e: ApiException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = "دانلود ناموفق بود: ${e.message ?: "خطای نامشخص"}"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun saveToDownloads(context: Context, url: String): Boolean {
        val http = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", client.session.token?.let { "Bearer $it" } ?: "")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val contentType = resp.header("Content-Type") ?: ""
            if (contentType.contains("text/html") || contentType.contains("application/json")) return false
            val fileName = fileNameFrom(url, contentType)
            val body = resp.body ?: return false
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, contentType.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) } ?: return false
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return true
            }
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.outputStream().use { out -> body.byteStream().copyTo(out) }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return true
        }
    }

    private fun fileNameFrom(url: String, contentType: String): String {
        val fromUrl = url.substringAfterLast('/').substringBefore('?')
        if (fromUrl.isNotBlank() && fromUrl.contains('.')) return "pardava-$fromUrl"
        val ext = when {
            contentType.contains("pdf") -> "pdf"
            contentType.contains("zip") -> "zip"
            contentType.contains("text") -> "txt"
            else -> "bin"
        }
        return "pardava-lesson-$lessonId.$ext"
    }

    companion object {
        private const val SAVE_INTERVAL_MS = 10_000L
    }
}
