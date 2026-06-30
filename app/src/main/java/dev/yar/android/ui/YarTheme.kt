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
    primary = Color(0xFF1DB954),
    onPrimary = Color(0xFF04130A),
    primaryContainer = Color(0xFF12351F),
    onPrimaryContainer = Color(0xFFD7FBE0),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF03131C),
    tertiary = Color(0xFFFF5C7A),
    onTertiary = Color(0xFF24040B),
    background = Color(0xFF050608),
    onBackground = Color(0xFFF7F7F8),
    surface = Color(0xFF101114),
    onSurface = Color(0xFFF7F7F8),
    surfaceVariant = Color(0xFF191B20),
    onSurfaceVariant = Color(0xFFC5CAD3),
    outline = Color(0xFF3A3D45),
    error = Color(0xFFFF5C7A),
    errorContainer = Color(0xFF3B0C16),
    onErrorContainer = Color(0xFFFFD9DF),
)

internal val AppBackgroundTop = Color(0xFF0C0D10)
internal val AppBackgroundMid = Color(0xFF08090B)
internal val AppBackgroundBottom = Color(0xFF050608)
internal val ElevatedPanel = Color(0xFF15171C)
internal val ElevatedPanelAlt = Color(0xFF1B1E24)
internal val GlassPanel = Color(0xF214161A)
internal val MutedText = Color(0xFFA7ADB8)
internal val LiveAccent = Color(0xFF38BDF8)
internal val TimefreeAccent = Color(0xFFFF5C7A)
internal val ActiveGreen = Color(0xFF1DB954)

private val YarShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
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
