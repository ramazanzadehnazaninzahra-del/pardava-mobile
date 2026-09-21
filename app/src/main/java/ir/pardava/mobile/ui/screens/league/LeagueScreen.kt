package ir.pardava.mobile.ui.screens.league

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

@Composable
fun LeagueScreen(app: PardavaApp) {
    val lang = app.currentLanguage()
    val vm: LeagueViewModel = viewModel(factory = SimpleVmFactory(app.api) { LeagueViewModel(it) })
    val state by vm.state.collectAsState()
    val selected by vm.selectedCourse.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.nav_league),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when (val s = state) {
            is LeagueUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is LeagueUiState.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                }
            }

            is LeagueUiState.Ready -> {
                if (s.data.courses.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selected == null,
                                onClick = { vm.selectCourse(null) },
                                label = { Text(stringResource(R.string.league_scope_site)) },
                            )
                        }
                        items(s.data.courses, key = { it.slug }) { c ->
                            FilterChip(
                                selected = selected == c.slug,
                                onClick = { vm.selectCourse(c.slug) },
                                label = { Text(c.title, maxLines = 1) },
                            )
                        }
                    }
                }

                if (s.data.leaders.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.league_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(s.data.leaders, key = { it.user_id }) { leader ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        Fmt.digits("#${leader.rank}", lang),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = when (leader.rank) {
                                            1 -> MaterialTheme.colorScheme.tertiary
                                            2, 3 -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(
                                            leader.name.ifBlank { stringResource(R.string.league_anonymous) },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.league_points, Fmt.int(leader.points, lang)),
                                        style = MaterialTheme.typography.labelLarge,
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
}
