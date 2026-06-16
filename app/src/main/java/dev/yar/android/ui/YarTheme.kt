package dev.yar.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val YarColors = darkColorScheme(
    primary = Color(0xFFFF6A9D),
    onPrimary = Color(0xFF24000E),
    primaryContainer = Color(0xFF4C1831),
    onPrimaryContainer = Color(0xFFFFD8E6),
    secondary = Color(0xFF80D8FF),
    onSecondary = Color(0xFF001F2A),
    tertiary = Color(0xFFFFC65C),
    onTertiary = Color(0xFF271700),
    background = Color(0xFF08090D),
    onBackground = Color(0xFFF8F2F7),
    surface = Color(0xFF101117),
    onSurface = Color(0xFFF8F2F7),
    surfaceVariant = Color(0xFF1B1C24),
    onSurfaceVariant = Color(0xFFD7D1DB),
    outline = Color(0xFF595260),
)

internal val AppBackgroundTop = Color(0xFF191020)
internal val AppBackgroundMid = Color(0xFF0F1118)
internal val AppBackgroundBottom = Color(0xFF07080B)
internal val ElevatedPanel = Color(0xFF181923)
internal val ElevatedPanelAlt = Color(0xFF21202C)
internal val GlassPanel = Color(0xCC171822)
internal val MutedText = Color(0xFFC5BECB)
internal val LiveRed = Color(0xFFFF4D6D)

private val YarShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
)

@Composable
internal fun YarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YarColors,
        shapes = YarShapes,
        content = content,
    )
}

@Composable
internal fun YarBackground(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppBackgroundTop,
                            AppBackgroundMid,
                            AppBackgroundBottom,
                        ),
                    ),
                ),
        ) {
            content()
        }
    }
}
