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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data class Ready(val lesson: LessonContentResponse) : LessonUiState
    data class Failure(val message: String) : LessonUiState
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

    /** One-shot event: completion succeeded → refresh the parent course. */
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed

    fun consumeMessage() { _message.value = null }

    /** In-place prev/next navigation within the same screen. */
    fun openLesson(newId: Long) {
        if (newId != lessonId) {
            lessonId = newId
            load()
        }
    }

    fun load() {
        _state.value = LessonUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = LessonUiState.Ready(client.call { client.api.lesson(slug, lessonId) })
            } catch (e: Exception) {
                _state.value = LessonUiState.Failure(e.message ?: "error")
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
}
