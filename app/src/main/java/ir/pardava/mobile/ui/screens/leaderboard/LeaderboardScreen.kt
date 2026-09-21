package ir.pardava.mobile.ui.screens.leaderboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

private val SCOPES = listOf("weekly", "monthly", "all")

@Composable
fun LeaderboardScreen(app: PardavaApp) {
    val lang = app.currentLanguage()
    val vm: LeaderboardViewModel = viewModel(factory = SimpleVmFactory(app.api) { LeaderboardViewModel(it) })
    val state by vm.state.collectAsState()
    var scope by rememberSaveable { mutableStateOf("weekly") }

    LaunchedEffect(scope) { vm.load(scope) }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.leaderboard_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        TabRow(selectedTabIndex = SCOPES.indexOf(scope)) {
            SCOPES.forEachIndexed { i, sc ->
                Tab(
                    selected = scope == sc,
                    onClick = { scope = sc },
                    text = {
                        Text(
                            when (sc) {
                                "weekly" -> stringResource(R.string.scope_weekly)
                                "monthly" -> stringResource(R.string.scope_monthly)
                                else -> stringResource(R.string.scope_all)
                            }
                        )
                    },
                )
            }
        }

        when (val s = state) {
            is LeaderboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is LeaderboardUiState.Failure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            }

            is LeaderboardUiState.Ready -> {
                val me = s.board.me
                if (me != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "#${Fmt.int(me.rank, lang)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text(me.display_name, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.leaderboard_points, Fmt.int(me.xp, lang)))
                        }
                    }
                }
                if (s.board.entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.leaderboard_empty))
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(s.board.entries, key = { it.user_id }) { e ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "#${Fmt.int(e.rank, lang)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Text(
                                    e.display_name + if (me?.user_id == e.user_id) " (${stringResource(R.string.leaderboard_you)})" else "",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(R.string.leaderboard_points, Fmt.int(e.xp, lang)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
