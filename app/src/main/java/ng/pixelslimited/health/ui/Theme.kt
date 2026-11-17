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

// Two base tones and one accent, all set in OKLCH so lightness steps match across themes.
// Borders are see-through ink, so they read the same on any surface.
private val Ink = oklch(0.185f, 0.012f, 235f)
private val Paper = oklch(0.960f, 0.011f, 88f)
/** The accent: lemon green. Fills carry ink text; text in the accent uses a deeper shade on light paper. */
private val Lemon = oklch(0.86f, 0.19f, 123f)
private val LemonDark = oklch(0.88f, 0.20f, 122f)
private val Olive = oklch(0.48f, 0.13f, 128f)
/** Warnings only (readings outside range, danger): not an accent. */
private val Warn = oklch(0.545f, 0.175f, 25f)
private val WarnDark = oklch(0.640f, 0.185f, 24f)

private val Light = lightColorScheme(
    primary = Olive, onPrimary = Paper, primaryContainer = Lemon, onPrimaryContainer = Ink,
    secondary = Ink, onSecondary = Paper,
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = oklch(0.920f, 0.014f, 88f), onSurfaceVariant = oklch(0.470f, 0.014f, 235f),
    secondaryContainer = Lemon.copy(alpha = 0.45f), onSecondaryContainer = Ink,
    surfaceContainer = oklch(0.920f, 0.014f, 88f), surfaceContainerHigh = oklch(0.975f, 0.008f, 88f),
    outline = Ink.copy(alpha = 0.35f), outlineVariant = Ink.copy(alpha = 0.13f), error = Warn, onError = Paper,
)

// Dark depth: each raised layer is a step lighter (page, card, dialog) instead of relying on shadows.
private val Dark = darkColorScheme(
    primary = LemonDark, onPrimary = Ink, primaryContainer = LemonDark, onPrimaryContainer = Ink,
    secondary = Paper, onSecondary = Ink,
    background = Ink, onBackground = Paper, surface = Ink, onSurface = Paper,
    surfaceVariant = oklch(0.245f, 0.014f, 235f), onSurfaceVariant = oklch(0.760f, 0.012f, 235f),
    secondaryContainer = LemonDark.copy(alpha = 0.25f), onSecondaryContainer = Paper,
    surfaceContainer = oklch(0.245f, 0.014f, 235f), surfaceContainerHigh = oklch(0.295f, 0.015f, 235f),
    outline = Paper.copy(alpha = 0.35f), outlineVariant = Paper.copy(alpha = 0.11f), error = WarnDark, onError = Paper,
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
