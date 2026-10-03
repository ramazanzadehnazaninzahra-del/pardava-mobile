package ir.pardava.mobile.ui.screens.quiz

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.ApiException
import ir.pardava.mobile.data.dto.QuizDto
import ir.pardava.mobile.data.dto.QuizQuestionDto
import ir.pardava.mobile.data.dto.QuizResultDto
import ir.pardava.mobile.data.dto.QuizSubmitIn
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface QuizUiState {
    data object Loading : QuizUiState
    data class Ready(val quiz: QuizDto) : QuizUiState
    data class Failure(val message: String) : QuizUiState
}

class QuizViewModel(private val client: ApiClient, private val slug: String) : ViewModel() {

    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() { _message.value = null }

    fun load() {
        _state.value = QuizUiState.Loading
        viewModelScope.launch {
            try {
                val res = client.call { client.api.quiz(slug) }
                val quiz = res.quiz
                if (quiz == null || quiz.questions.isEmpty()) {
                    _state.value = QuizUiState.Failure(res.error ?: "")
                } else {
                    _state.value = QuizUiState.Ready(quiz)
                }
            } catch (e: Exception) {
                _state.value = QuizUiState.Failure(e.message ?: "error")
            }
        }
    }

    /** Submits answers {questionId: "a".."d"} and delivers the graded result. */
    fun submit(answers: Map<Long, String>, onResult: (QuizResultDto) -> Unit) {
        if (_submitting.value) return
        _submitting.value = true
        viewModelScope.launch {
            try {
                val payload = answers.entries.associate { it.key.toString() to it.value }
                val res = client.call { client.api.submitQuiz(slug, QuizSubmitIn(payload)) }
                res.result?.let(onResult)
            } catch (e: ApiException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = e.message ?: "error"
            } finally {
                _submitting.value = false
            }
        }
    }
}

/**
 * Course quiz: one screen with all questions, radio options, submit → graded
 * result card with score / best record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    app: PardavaApp,
    slug: String,
    onBack: () -> Unit,
) {
    val vm: QuizViewModel = viewModel(
        key = slug,
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                QuizViewModel(app.api, slug) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val submitting by vm.submitting.collectAsStateWithLifecycle()

    LaunchedEffect(slug) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quiz_title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        when (val s = state) {
            is QuizUiState.Loading -> LoadingBox()
            is QuizUiState.Failure -> ErrorState(message = s.message.ifBlank { stringResource(R.string.error_generic) }, onRetry = { vm.load() })
            is QuizUiState.Ready -> QuizContent(
                quiz = s.quiz,
                submitting = submitting,
                onSubmit = { answers, onResult -> vm.submit(answers, onResult) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun QuizContent(
    quiz: QuizDto,
    submitting: Boolean,
    onSubmit: (Map<Long, String>, (QuizResultDto) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val answers = remember { mutableStateMapOf<Long, String>() }
    var result by remember { mutableStateOf<QuizResultDto?>(null) }
    val allAnswered = quiz.questions.all { q -> q.id != null && answers[q.id] != null }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            quiz.title ?: "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.quiz_questions_count, quiz.questions.size) +
                " · " + stringResource(R.string.quiz_pass_line, quiz.passPercent ?: 60) +
                (quiz.best?.let { " · " + stringResource(R.string.quiz_best, it) } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        result?.let { res ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (res.passed == true)
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (res.passed == true) Icons.Filled.EmojiEvents else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = if (res.passed == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.quiz_score, res.score ?: 0),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (res.passed == true) stringResource(R.string.quiz_passed)
                        else stringResource(R.string.quiz_failed, res.passPercent ?: 60),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.quiz_correct_count, res.correct ?: 0, res.total ?: 0) +
                            (res.best?.let { " · " + stringResource(R.string.quiz_best, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        quiz.questions.forEach { q ->
            QuizQuestion(q, answers)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { onSubmit(answers.toMap()) { result = it } },
            enabled = !submitting && allAnswered,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (submitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.quiz_submit))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun QuizQuestion(q: QuizQuestionDto, answers: MutableMap<Long, String>) {
    val qid = q.id ?: return
    val labels = listOf("a" to "الف", "b" to "ب", "c" to "ج", "d" to "د")
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                (q.position?.toString() ?: "") + ". " + (q.text ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            labels.forEach { (key, label) ->
                val optionText = q.options[key].orEmpty()
                if (optionText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (answers[qid] == key)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else
                                    androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(10.dp),
                            ),
                    ) {
                        RadioButton(
                            selected = answers[qid] == key,
                            onClick = { answers[qid] = key },
                        )
                        Text("$label) $optionText", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
