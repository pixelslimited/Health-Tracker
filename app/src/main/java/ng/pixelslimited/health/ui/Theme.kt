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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ng.pixelslimited.health.R
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

// Inter for text, Inter Display for headings and numerals (bundled, SIL Open Font License).
val Brand = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_black, FontWeight.Black),
)

// Nothing smaller than 16sp; tight, heavy headings.
private val Base = Typography()
private fun TextStyle.brand() = copy(fontFamily = Brand)
private val Type = Typography(
    displayLarge = Base.displayLarge.brand().copy(fontWeight = FontWeight.Bold),
    displayMedium = Base.displayMedium.brand().copy(fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.brand().copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineLarge = Base.headlineLarge.brand().copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.brand().copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineSmall = Base.headlineSmall.brand().copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.brand().copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.brand().copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.brand().copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.brand().copy(fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    bodyMedium = Base.bodyMedium.brand().copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = Base.bodySmall.brand().copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    labelLarge = Base.labelLarge.brand().copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.brand().copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelSmall = Base.labelSmall.brand().copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
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
