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

/* ---- Brand palette (indigo → violet, amber accents) ---- */
val Indigo700 = Color(0xFF4338CA)
val Indigo600 = Color(0xFF4F46E5)
val Indigo200 = Color(0xFFC7D2FE)
val Violet500 = Color(0xFF8B5CF6)
val Amber500 = Color(0xFFF59E0B)
val Emerald500 = Color(0xFF10B981)
val Rose600 = Color(0xFFDC2626)
val Sky500 = Color(0xFF0EA5E9)

private val LightColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Indigo200,
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Violet500,
    onSecondary = Color.White,
    tertiary = Emerald500,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF2FF),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Rose600,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Indigo200,
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1E1B4B),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF064E3B),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111A2E),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
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
