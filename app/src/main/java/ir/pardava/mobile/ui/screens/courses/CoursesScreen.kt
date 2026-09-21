package ir.pardava.mobile.ui.screens.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.ui.components.LanguageSwitchRow

@Composable
fun CoursesScreen(app: PardavaApp, onOpenCourse: (String) -> Unit) {
    val lang = app.currentLanguage()
    val vm: CoursesViewModel = viewModel(factory = SimpleVmFactory(app.api) { CoursesViewModel(it) })
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.courses_title), style = MaterialTheme.typography.titleLarge)
            LanguageSwitchRow(app = app)
        }

        when (val s = state) {
            is CoursesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is CoursesUiState.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.error_network), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                }
            }

            is CoursesUiState.Ready -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.courses, key = { it.id }) { course ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp),
                        onClick = { onOpenCourse(course.slug) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                course.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            course.description?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    stringResource(R.string.chapters_count, Fmt.int(course.chapters_count, lang)),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    stringResource(R.string.lessons_count, Fmt.int(course.lessons_count, lang)),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    levelLabel(course.level),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun levelLabel(level: String): String = when (level) {
    "beginner" -> stringResource(R.string.level_beginner)
    "intermediate" -> stringResource(R.string.level_intermediate)
    else -> stringResource(R.string.level_advanced)
}
