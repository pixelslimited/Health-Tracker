package ng.pixelslimited.health.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

// Nothing smaller than 16sp; tight, heavy headings.
private val Base = Typography()
private val Type = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = Base.headlineMedium.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    titleLarge = Base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
)

@Composable
fun AppTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) Dark else Light
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = scheme.background.toArgb()
        window.navigationBarColor = scheme.background.toArgb()
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = !dark
        bars.isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
