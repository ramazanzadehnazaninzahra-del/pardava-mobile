package ir.pardava.mobile.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(app: PardavaApp) {
    val lang = app.currentLanguage()
    val vm: ProfileViewModel = viewModel(factory = SimpleVmFactory(app.api) { ProfileViewModel(it) })
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = {
                scope.launch { app.api.logout() }
            }) {
                Icon(Icons.Filled.Logout, contentDescription = stringResource(R.string.logout), Modifier.size(18.dp))
                Text(stringResource(R.string.logout))
            }
        }

        when (val s = state) {
            is ProfileUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is ProfileUiState.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!app.session.isSignedIn) {
                        Text(stringResource(R.string.sign_in_prompt))
                        Spacer(Modifier.height(12.dp))
                    } else {
                        Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                    }
                }
            }

            is ProfileUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.stats_level, Fmt.int(s.stats.level, lang)),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.stats_xp, Fmt.int(s.stats.xp_total, lang)),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { (s.stats.level_progress.percent / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(
                                    R.string.stats_level_progress,
                                    Fmt.int(s.stats.level_progress.current_level_xp, lang),
                                    Fmt.int(s.stats.level_progress.next_level_xp, lang),
                                    Fmt.int(s.stats.level + 1, lang),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    stringResource(R.string.stats_streak, Fmt.int(s.stats.current_streak, lang)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(R.string.stats_rank, Fmt.int(s.stats.rank, lang)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.stats_longest_streak, Fmt.int(s.stats.longest_streak, lang)) +
                                    " · " +
                                    stringResource(R.string.stats_lessons_completed, Fmt.int(s.stats.lessons_completed, lang)) +
                                    " · " +
                                    stringResource(R.string.stats_quizzes_passed, Fmt.int(s.stats.quizzes_passed, lang)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.achievements_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                items(s.achievements, key = { it.code }) { a ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (a.earned_at != null) {
                                Icon(
                                    Icons.Filled.EmojiEvents,
                                    contentDescription = a.name,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = stringResource(R.string.achievements_locked),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                a.description?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(R.string.xp_earned, Fmt.int(a.xp_reward, lang)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (a.earned_at != null) {
                                    Text(
                                        a.earned_at!!.take(10),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
