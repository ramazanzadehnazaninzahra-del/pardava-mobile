package ir.pardava.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import ir.pardava.mobile.R

/* ---- Pardava brand palette — exact values from pardava.ir/static/style.css ---- */
val BrandBlue = Color(0xFF2563EB)       /* light --accent */
val BrandBlueDark = Color(0xFF3B82F6)   /* dark --accent / theme-color */
val BrandTeal = Color(0xFF12C2B0)       /* site highlight accent */
val BrandMint = Color(0xFF4CEBB4)

/* light: --bg #f4f6fa · --bg-card #ffffff · --text #1a2332 · --text-dim #5a6d85 */
val SiteLightBg = Color(0xFFF4F6FA)
val SiteLightCard = Color(0xFFFFFFFF)
val SiteLightCardHover = Color(0xFFF0F4FA)
val SiteLightText = Color(0xFF1A2332)
val SiteLightDim = Color(0xFF5A6D85)
val SiteLightOutline = Color(0xFFDDE4EE)

/* dark: --bg #0a0e17 · --bg-card #151d2e · --bg-elevated #111827 · --text #e8edf5 · --text-dim #8b9cb5 */
val SiteDarkBg = Color(0xFF0A0E17)
val SiteDarkCard = Color(0xFF151D2E)
val SiteDarkElevated = Color(0xFF111827)
val SiteDarkCardHover = Color(0xFF1A2438)
val SiteDarkText = Color(0xFFE8EDF5)
val SiteDarkDim = Color(0xFF8B9CB5)
val SiteDarkOutline = Color(0xFF253149)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0B2A6B),
    secondary = BrandTeal,
    onSecondary = Color.White,
    tertiary = Color(0xFF0E9384),
    onTertiary = Color.White,
    background = SiteLightBg,
    onBackground = SiteLightText,
    surface = SiteLightCard,
    onSurface = SiteLightText,
    surfaceVariant = SiteLightCardHover,
    onSurfaceVariant = SiteLightDim,
    outline = SiteLightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF06122E),
    primaryContainer = Color(0xFF153064),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = BrandTeal,
    onSecondary = Color(0xFF06122E),
    tertiary = BrandMint,
    onTertiary = Color(0xFF06122E),
    background = SiteDarkBg,
    onBackground = SiteDarkText,
    surface = SiteDarkCard,
    onSurface = SiteDarkText,
    surfaceVariant = SiteDarkCardHover,
    onSurfaceVariant = SiteDarkDim,
    outline = SiteDarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

/** Extra colors used by custom cards/badges (exposed via MaterialTheme.colorScheme where possible). */
val LightSurfaceDim = Color(0xFFE2E8F0)
val DarkSurfaceDim = Color(0xFF0F172A)

/** Vazirmatn ships in the APK so Persian renders consistently across devices. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

val AppTypography = Typography().withDefaultFontFamily(Vazirmatn)

private fun Typography.withDefaultFontFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * App theme with two user-controlled knobs from Settings:
 * @param darkTheme forced light/dark, or system when the caller passes isSystemInDarkTheme().
 * @param fontScale multiplier (0.9 – 1.3) applied on top of the system font scale.
 */
@Composable
fun PardavaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    val scaled = Density(base.density, base.fontScale * fontScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
