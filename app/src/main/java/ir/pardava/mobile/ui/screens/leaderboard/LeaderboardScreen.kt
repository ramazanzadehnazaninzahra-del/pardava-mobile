package ir.pardava.mobile.ui.screens.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.LeaderRowDto
import ir.pardava.mobile.ui.components.EmptyState
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.LoadingBox
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

/**
 * League standings (public). All-courses view or per-course via filter chips;
 * ranks 1–3 get medal badges. Empty state invites the learner to be first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(app: PardavaApp, onBack: (() -> Unit)? = null) {
    val vm: LeagueViewModel = viewModel(factory = SimpleVmFactory(app.api) { LeagueViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()

    LaunchedEffect(Unit) { vm.load() }

    when (val s = state) {
        is LeagueUiState.Loading -> LoadingBox()
        is LeagueUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
        is LeagueUiState.Ready -> Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
                Text(
                    stringResource(R.string.leaderboard_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = if (onBack != null) 0.dp else 16.dp, top = 12.dp),
                )
            }
            s.scopeTitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, end = 16.dp),
                )
            }
            // ---- course filter chips ----
            if (s.courses.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = s.selected == null,
                        onClick = { vm.selectCourse(null) },
                        label = { Text(stringResource(R.string.league_all_courses)) },
                    )
                    s.courses.forEach { c ->
                        FilterChip(
                            selected = s.selected != null && c.slug == s.selected,
                            onClick = { c.slug?.let { vm.selectCourse(it) } },
                            label = {
                                Text(c.title ?: c.slug ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                        )
                    }
                }
            }
            if (s.leaders.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.EmojiEvents,
                    title = stringResource(R.string.league_empty_title),
                    subtitle = stringResource(R.string.league_empty_subtitle),
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    androidx.compose.material3.IconButton(onClick = { vm.load(force = true) }, enabled = !refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.retry))
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.leaders.size) { idx ->
                        LeaderRow(s.leaders[idx], idx + 1, lang)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderRow(leader: LeaderRowDto, fallbackRank: Int, lang: String) {
    val rank = leader.rank ?: fallbackRank
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (leader.isYou == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ---- rank badge ----
            Box(
                Modifier
                    .size(34.dp)
                    .background(rankContainer(rank), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (rank <= 3) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = rankColor(rank),
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        Fmt.digits("$rank", lang),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // ---- avatar ----
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    leader.displayName + if (leader.isYou == true) " (${stringResource(R.string.leaderboard_you)})" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                leader.lessonsCompleted?.let {
                    Text(
                        Fmt.digits("$it " + stringResource(R.string.lessons_word), lang),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                Fmt.digits("${leader.effectivePoints}", lang) + " " + stringResource(R.string.points_short),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun rankContainer(rank: Int): Color = when (rank) {
    1 -> Color(0xFFFEF3C7)
    2 -> Color(0xFFE2E8F0)
    3 -> Color(0xFFFFEDD5)
    else -> Color.Transparent
}

private fun rankColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFF59E0B)
    2 -> Color(0xFF64748B)
    3 -> Color(0xFFEA580C)
    else -> Color.Transparent
}
