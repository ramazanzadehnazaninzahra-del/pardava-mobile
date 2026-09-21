package ir.pardava.mobile.ui.screens.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.AttemptQuestionOut
import ir.pardava.mobile.data.dto.PerQuestionResultOut
import ir.pardava.mobile.data.dto.SubmitResultOut
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    app: PardavaApp,
    courseSlug: String,
    lessonSlug: String,
    quizId: Long,
    onBack: () -> Unit,
) {
    val lang = app.currentLanguage()
    val vm: QuizViewModel = viewModel(key = "quiz-$quizId", factory = QuizVmFactory(app.api, quizId))
    val state by vm.state.collectAsState()
    val answers by vm.answers.collectAsState()
    val secondsLeft by vm.secondsLeft.collectAsState()
    var confirmSubmit by remember { mutableStateOf(false) }

    LaunchedEffect(quizId) { vm.start(lang) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lesson_quiz), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is QuizUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is QuizUiState.Failure -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.quiz_back_to_lesson)) }
                }
            }

            is QuizUiState.Answering -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Countdown + progress
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Fmt.digits(
                            "${answers.size} / ${s.questions.questions.size}",
                            lang,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    secondsLeft?.let { left ->
                        Text(
                            stringResource(R.string.quiz_time_left, Fmt.duration(left.toInt(), lang)),
                            fontWeight = FontWeight.Bold,
                            color = if (left <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                s.questions.questions.forEachIndexed { index, q ->
                    QuestionCard(
                        index = index + 1,
                        total = s.questions.questions.size,
                        question = q,
                        lang = lang,
                        selected = answers[q.id],
                        onChooseOption = { vm.setAnswer(q.id, it) },
                        onTrueFalse = { vm.setAnswer(q.id, it) },
                        onShortCode = { vm.setAnswer(q.id, it) },
                    )
                }

                Button(
                    onClick = { confirmSubmit = true },
                    enabled = answers.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.quiz_submit))
                }
            }

            is QuizUiState.Result -> ResultPanel(
                result = s.result,
                lang = lang,
                onBack = onBack,
                onRetake = { vm.start(lang) },
            )
        }
    }

    if (confirmSubmit) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text(stringResource(R.string.quiz_submit)) },
            text = { Text(stringResource(R.string.quiz_submit_confirm)) },
            confirmButton = {
                Button(onClick = { confirmSubmit = false; vm.submit(lang) }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmSubmit = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun QuestionCard(
    index: Int,
    total: Int,
    question: AttemptQuestionOut,
    lang: String,
    selected: Any?,
    onChooseOption: (Long) -> Unit,
    onTrueFalse: (Boolean) -> Unit,
    onShortCode: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.quiz_question_of, Fmt.int(index, lang), Fmt.int(total, lang)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.xp_earned, Fmt.int(question.points, lang)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(question.prompt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

            when (question.type) {
                "single_choice" -> question.options.forEach { opt ->
                    FilterChip(
                        selected = selected == opt.id,
                        onClick = { onChooseOption(opt.id) },
                        label = { Text(opt.text) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                "true_false" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selected == true,
                        onClick = { onTrueFalse(true) },
                        label = { Text(stringResource(R.string.quiz_true)) },
                    )
                    FilterChip(
                        selected = selected == false,
                        onClick = { onTrueFalse(false) },
                        label = { Text(stringResource(R.string.quiz_false)) },
                    )
                }

                "short_code" -> OutlinedTextField(
                    value = (selected as? String) ?: "",
                    onValueChange = onShortCode,
                    label = { Text(stringResource(R.string.quiz_answer_here)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ResultPanel(result: SubmitResultOut, lang: String, onBack: () -> Unit, onRetake: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (result.passed) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (result.passed) stringResource(R.string.quiz_passed_result)
                    else stringResource(R.string.quiz_failed_result),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.quiz_score, Fmt.int(result.score_percent.toInt(), lang)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.quiz_pass_line, Fmt.int(result.pass_score_percent, lang)))
                if (result.xp_awarded.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.xp_awarded.forEach { xp ->
                            Text(
                                stringResource(R.string.xp_earned, Fmt.int(xp.amount, lang)),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            if (xp != result.xp_awarded.last()) {
                                Text("+", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                result.new_achievements.forEach { a ->
                    Text(stringResource(R.string.new_achievement, a.name), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        result.per_question.forEach { r ->
            QuestionResultRow(r, lang)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text(stringResource(R.string.quiz_back_to_lesson)) }
            if (!result.passed) {
                OutlinedButton(onClick = onRetake) { Text(stringResource(R.string.quiz_retake)) }
            }
        }
    }
}

@Composable
private fun QuestionResultRow(r: PerQuestionResultOut, lang: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (r.correct) stringResource(R.string.quiz_correct) else stringResource(R.string.quiz_wrong),
                    fontWeight = FontWeight.Bold,
                    color = if (r.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.xp_earned, Fmt.int(r.points, lang)), style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
            val answerText = r.correct_answer["option_id"]?.toString()
                ?: r.correct_answer["answer"]?.toString()
                ?: r.correct_answer["accepted"]?.toString()
                ?: "—"
            Text(
                stringResource(R.string.quiz_correct_answer) + ": " + answerText.trim('"', '[', ']'),
                style = MaterialTheme.typography.bodySmall,
            )
            r.explanation?.let {
                Text(
                    stringResource(R.string.quiz_explanation) + ": " + it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

class QuizVmFactory(private val client: ir.pardava.mobile.core.ApiClient, private val quizId: Long) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        QuizViewModel(client, quizId) as T
}
