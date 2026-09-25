package ir.pardava.mobile.ui.screens.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.MobileArticleDto
import ir.pardava.mobile.ui.components.EmptyState
import ir.pardava.mobile.ui.components.ErrorState
import ir.pardava.mobile.ui.components.SkeletonBlock
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory

/** Article list — cover-first cards, minimal text (blog of pardava.ir). */
@Composable
fun ArticlesScreen(
    app: PardavaApp,
    onOpenArticle: (MobileArticleDto) -> Unit,
) {
    val vm: ArticlesViewModel = viewModel(factory = SimpleVmFactory(app.api) { ArticlesViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    when (val s = state) {
        is ArticlesUiState.Loading -> ArticlesSkeleton()
        is ArticlesUiState.Failure -> ErrorState(message = s.message, onRetry = { vm.load(force = true) })
        is ArticlesUiState.Ready -> {
            if (s.articles.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Article,
                    title = stringResource(R.string.articles_empty),
                    subtitle = null,
                )
                return
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(s.articles, key = { it.slug ?: it.id?.toString() ?: "?" }) { article ->
                    ArticleCard(
                        article = article,
                        lang = lang,
                        coverUrl = app.api.absoluteUrl(article.coverUrl),
                    ) { onOpenArticle(article) }
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(article: MobileArticleDto, lang: String, coverUrl: String?, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2.4f)) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Article,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    article.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!article.excerpt.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        article.excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!article.tag.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                            contentColor = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                article.tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        article.createdAt?.let {
                            Text(
                                Fmt.digits(it.take(10), lang),
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

@Composable
private fun ArticlesSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(3) {
            Column {
                SkeletonBlock(Modifier.fillMaxWidth().height(150.dp), corner = 18.dp)
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(18.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(14.dp))
            }
        }
    }
}
