package ir.pardava.mobile.ui.screens.lesson

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.LessonDetailOut
import ir.pardava.mobile.data.dto.MessageOut
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data class Ready(val detail: LessonDetailOut) : LessonUiState
    data class Failure(val message: String) : LessonUiState
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Running : DownloadState
    data class Done(val file: File, val mime: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

class LessonViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val state: StateFlow<LessonUiState> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    private var slug: String = ""
    private var lessonId: Long = 0

    fun bind(slug: String, lessonId: Long) {
        if (this.slug != slug || this.lessonId != lessonId) {
            this.slug = slug
            this.lessonId = lessonId
            _download.value = DownloadState.Idle
            load()
        }
    }

    fun load() {
        if (slug.isEmpty() || lessonId == 0L) return
        _state.value = LessonUiState.Loading
        viewModelScope.launch {
            try {
                val detail = apiCall { client.api().lesson(slug, lessonId) }
                _state.value = LessonUiState.Ready(detail)
            } catch (e: Exception) {
                _state.value = LessonUiState.Failure(e.message ?: "error")
            }
        }
    }

    /** Mark the lesson complete and earn points (server enforces access). */
    fun complete() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val out: MessageOut = apiCall { client.api().completeLesson(slug, lessonId) }
                _notice.value = out.message
                    ?: (out.points_awarded?.takeIf { it > 0 }?.let { "+$it" })
                load()
            } catch (e: Exception) {
                _notice.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Download the lesson file with the session token (the endpoint requires
     * Bearer auth) into cacheDir/downloads, ready to be opened via FileProvider.
     */
    fun downloadFile(context: Context) {
        if (_download.value == DownloadState.Running) return
        _download.value = DownloadState.Running
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val body = client.api().lessonFile(slug, lessonId)
                    val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
                    val lesson = (_state.value as? LessonUiState.Ready)?.detail?.lesson
                    val rawName = lesson?.file?.name?.takeIf { it.isNotBlank() }
                        ?: "lesson-$lessonId"
                    val safeName = rawName.replace(Regex("[/\\\\]"), "_")
                    val mime = body.contentType()?.toString()
                        ?.substringBefore(';')?.trim().orEmpty()
                    val file = File(dir, safeName)
                    body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    DownloadState.Done(file, mime)
                }
                _download.value = result
                (result as? DownloadState.Done)?.let { openFile(context, it) }
            } catch (e: Exception) {
                _download.value = DownloadState.Failed(e.message ?: "error")
            }
        }
    }

    private fun openFile(context: Context, done: DownloadState.Done) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", done.file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, done.mime.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            _notice.value = it.message
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    fun consumeDownloadResult() {
        val d = _download.value
        if (d is DownloadState.Failed) _download.value = DownloadState.Idle
    }
}
