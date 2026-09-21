package ir.pardava.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ir.pardava.mobile.R

val Blue600 = Color(0xFF2563EB)
val Blue50 = Color(0xFFEFF6FF)
val Amber500 = Color(0xFFF59E0B)
val Green600 = Color(0xFF16A34A)
val Red600 = Color(0xFFDC2626)
val Slate900 = Color(0xFF0F172A)
val Slate100 = Color(0xFFF1F5F9)

private val LightColors = lightColorScheme(
    primary = Blue600,
    secondary = Amber500,
    background = Color.White,
    surface = Blue50,
    error = Red600,
    onPrimary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    secondary = Amber500,
    background = Slate900,
    surface = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onPrimary = Slate900,
)

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

@Composable
fun PardavaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
