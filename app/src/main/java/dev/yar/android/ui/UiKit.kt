package dev.yar.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun SectionHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = eyebrow.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (highlight) ElevatedPanelAlt else ElevatedPanel),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlight) 3.dp else 1.dp),
    ) {
        content()
    }
}

@Composable
internal fun ListSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickableModifier = if (onClick != null && enabled) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = clickableModifier.alpha(if (enabled) 1f else 0.58f),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else ElevatedPanel,
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            content()
        }
    }
}

@Composable
internal fun PlaybackImage(
    url: String?,
    label: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.take(18),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
internal fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) color.copy(alpha = 0.16f) else ElevatedPanelAlt,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = if (selected) color else MutedText,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun PlayerPrimaryButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = if (compact) Modifier.size(42.dp) else Modifier.size(60.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) 18.dp else 24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            val icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow
            val description = if (isPlaying) "Pause" else "Play"
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(if (compact) 22.dp else 30.dp),
            )
        }
    }
}

@Composable
internal fun PlayerSecondaryButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Icon(imageVector = imageVector, contentDescription = contentDescription)
    }
}

@Composable
internal fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MutedText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun NoticeCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AccentDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
internal fun InlineMetaRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
