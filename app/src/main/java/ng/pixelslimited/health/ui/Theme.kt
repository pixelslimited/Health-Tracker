package ng.pixelslimited.health.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Two base tones and one accent.
private val Ink = Color(0xFF14181B)
private val Paper = Color(0xFFF6F4EF)
private val Accent = Color(0xFFE5484D)

private val Light = lightColorScheme(
    primary = Accent, onPrimary = Paper, secondary = Ink, onSecondary = Paper,
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFECE8DF), onSurfaceVariant = Ink.copy(alpha = 0.72f),
    secondaryContainer = Accent.copy(alpha = 0.16f), onSecondaryContainer = Ink,
    surfaceContainer = Color(0xFFECE8DF), surfaceContainerHigh = Color(0xFFECE8DF),
    outline = Ink.copy(alpha = 0.35f), error = Accent, onError = Paper,
)

private val Dark = darkColorScheme(
    primary = Accent, onPrimary = Paper, secondary = Paper, onSecondary = Ink,
    background = Ink, onBackground = Paper, surface = Ink, onSurface = Paper,
    surfaceVariant = Color(0xFF20262B), onSurfaceVariant = Paper.copy(alpha = 0.72f),
    secondaryContainer = Accent.copy(alpha = 0.24f), onSecondaryContainer = Paper,
    surfaceContainer = Color(0xFF20262B), surfaceContainerHigh = Color(0xFF20262B),
    outline = Paper.copy(alpha = 0.35f), error = Accent, onError = Paper,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
