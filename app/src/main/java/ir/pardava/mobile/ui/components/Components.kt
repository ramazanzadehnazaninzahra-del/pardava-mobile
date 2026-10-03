package ir.pardava.mobile.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Centered full-screen loader. */
@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Friendly full-screen error with a retry action. */
@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🛠️", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) {
                Text(androidx.compose.ui.res.stringResource(ir.pardava.mobile.R.string.retry))
            }
        }
    }
}

/** Friendly full-screen empty state. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small rounded info chip (category, level, meta …). */
@Composable
fun InfoChip(text: String, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Soft-gradient banner used behind section headers and the login brand block. */
@Composable
fun BrandBanner(height: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
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
        content()
    }
}

/** Labeled settings section container. */
@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

/** One selectable row inside a [SettingsSection]. */
@Composable
fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailing?.invoke()
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).size(18.dp),
                )
            }
        }
    }
}

/** Softly pulsing placeholder block used by skeleton loaders. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = 12.dp) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), RoundedCornerShape(corner)),
    )
}

/** Skeleton mimic of the catalog course card, shown while the list loads. */
@Composable
fun SkeletonCourseCard() {
    Column {
        SkeletonBlock(Modifier.fillMaxWidth().height(132.dp), corner = 20.dp)
        Spacer(Modifier.height(12.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.72f).height(18.dp))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(14.dp))
        Spacer(Modifier.height(6.dp))
    }
}
