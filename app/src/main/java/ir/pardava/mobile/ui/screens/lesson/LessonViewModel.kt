package ir.pardava.mobile.ui.screens.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.LessonDetail
import ir.pardava.mobile.data.dto.ProgressIn
import ir.pardava.mobile.data.dto.SubtitleUrlOut
import ir.pardava.mobile.data.dto.VideoUrlOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data class Ready(val lesson: LessonDetail) : LessonUiState
    data class Failure(val message: String) : LessonUiState
}

/** Media bundle resolved through signed URLs. */
data class LessonMedia(
    val videoUrl: VideoUrlOut,
    val subtitle: SubtitleUrlOut?,
)

class LessonViewModel(private val client: ApiClient, private val courseSlug: String, private val lessonSlug: String) :
    ViewModel() {

    private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val state: StateFlow<LessonUiState> = _state

    private val _media = MutableStateFlow<LessonMedia?>(null)
    val media: StateFlow<LessonMedia?> = _media

    private val _progressMsg = MutableStateFlow<String?>(null)
    val progressMsg: StateFlow<String?> = _progressMsg

    fun load(lang: String) {
        _state.value = LessonUiState.Loading
        _media.value = null
        viewModelScope.launch {
            try {
                val lesson = apiCall { client.api().lesson(courseSlug, lessonSlug) }
                _state.value = LessonUiState.Ready(lesson)
                if (!lesson.locked) resolveMedia(lesson, lang)
            } catch (e: ir.pardava.mobile.core.ApiException) {
                _state.value = LessonUiState.Failure(e.error.message(lang))
            } catch (e: Exception) {
                _state.value = LessonUiState.Failure(e.message ?: "error")
            }
        }
    }

    /** Pick the video matching the app language (fallback: any available). */
    private suspend fun resolveMedia(lesson: LessonDetail, lang: String) {
        val video = lesson.videos.firstOrNull { it.lang == lang } ?: lesson.videos.firstOrNull() ?: return
        try {
            val videoUrl = apiCall { client.api().videoUrl(video.id) }
            val sub = lesson.subtitles.firstOrNull { it.lang == video.lang } ?: lesson.subtitles.firstOrNull()
            val subUrl = sub?.let { apiCall { client.api().subtitleUrl(it.id) } }
            _media.value = LessonMedia(videoUrl, subUrl)
        } catch (_: Exception) {
            _media.value = null
        }
    }

    /** Report watch progress; the server clamps and decides completion + XP. */
    fun reportProgress(lesson: LessonDetail, videoLang: String, watchedSeconds: Int, watchedPercent: Int) {
        viewModelScope.launch {
            try {
                val res = apiCall {
                    client.api().reportProgress(
                        lesson.id,
                        ProgressIn(
                            video_lang = videoLang,
                            watched_seconds = watchedSeconds,
                            watched_percent = watchedPercent.coerceIn(0, 100),
                        ),
                    )
                }
                val completed = res["completed"]?.toString()?.toBooleanStrictOrNull() ?: false
                if (completed) {
                    val lesson2 = apiCall { client.api().lesson(courseSlug, lessonSlug) }
                    _state.value = LessonUiState.Ready(lesson2)
                }
            } catch (_: Exception) {
                // progress reporting is best-effort; the next tick retries
            }
        }
    }
}
