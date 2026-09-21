package ir.pardava.mobile.ui.screens.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.core.apiCall
import ir.pardava.mobile.data.dto.AttemptQuestionsOut
import ir.pardava.mobile.data.dto.SubmitIn
import ir.pardava.mobile.data.dto.SubmitResultOut
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

sealed interface QuizUiState {
    data object Loading : QuizUiState
    data class Answering(val questions: AttemptQuestionsOut, val expiresAt: String?) : QuizUiState
    data class Result(val result: SubmitResultOut) : QuizUiState
    data class Failure(val message: String, val code: String? = null) : QuizUiState
}

class QuizViewModel(private val client: ApiClient, private val quizId: Long) : ViewModel() {

    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state

    /** question_id → answer (Long for option id, Boolean, or String for short_code). */
    val answers = MutableStateFlow<Map<Long, Any>>(emptyMap())

    /** Live countdown, seconds left; null when the attempt has no deadline. */
    private val _secondsLeft = MutableStateFlow<Long?>(null)
    val secondsLeft: StateFlow<Long?> = _secondsLeft

    fun start(lang: String) {
        _state.value = QuizUiState.Loading
        viewModelScope.launch {
            try {
                // Starts a new attempt or resumes the in-progress one (server-side).
                val attempt = apiCall { client.api().startAttempt(quizId) }
                val questions = apiCall { client.api().attemptQuestions(attempt.attempt_id) }
                answers.value = emptyMap()
                _state.value = QuizUiState.Answering(questions, attempt.expires_at)
                runCountdown(attempt.expires_at, lang)
            } catch (e: ir.pardava.mobile.core.ApiException) {
                _state.value = QuizUiState.Failure(e.error.message(lang), e.error.code)
            } catch (e: Exception) {
                _state.value = QuizUiState.Failure(e.message ?: "error")
            }
        }
    }

    private fun runCountdown(expiresAt: String?, lang: String) {
        if (expiresAt == null) return
        viewModelScope.launch {
            while (true) {
                val exp = ir.pardava.mobile.core.QuizTimer.parse(expiresAt) ?: break
                val left = ir.pardava.mobile.core.QuizTimer.secondsLeft(exp)
                _secondsLeft.value = left
                if (left <= 0L) {
                    submit(lang)
                    break
                }
                delay(1_000)
            }
        }
    }

    fun setAnswer(questionId: Long, answer: Any) {
        answers.value = answers.value + (questionId to answer)
    }

    fun submit(lang: String) {
        val current = _state.value as? QuizUiState.Answering ?: return
        viewModelScope.launch {
            try {
                val payload = answers.value.mapKeys { it.key.toString() }.mapValues { (_, v) ->
                    when (v) {
                        is Boolean -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    }
                }
                val result = apiCall { client.api().submitAttempt(current.questions.attempt_id, SubmitIn(payload)) }
                _state.value = QuizUiState.Result(result)
                _secondsLeft.value = null
            } catch (e: ir.pardava.mobile.core.ApiException) {
                _state.value = QuizUiState.Failure(e.error.message(lang), e.error.code)
            } catch (e: Exception) {
                _state.value = QuizUiState.Failure(e.message ?: "error")
            }
        }
    }
}
