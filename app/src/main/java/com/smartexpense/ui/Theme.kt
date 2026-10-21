package com.smartexpense.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One light palette for every screen, including sheets and Android dark-mode devices. */
object Tokens {
    val background @Composable get() = MaterialTheme.colorScheme.background
    val paper @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
    val surface @Composable get() = MaterialTheme.colorScheme.surface
    val secondarySurface @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val inputSurface @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
    val border @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val text @Composable get() = MaterialTheme.colorScheme.onSurface
    val muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val accent @Composable get() = MaterialTheme.colorScheme.primary
    val expense @Composable get() = text
    val positive @Composable get() = accent
    val negative @Composable get() = MaterialTheme.colorScheme.error
    val blue @Composable get() = accent
    val chartTones @Composable get() = listOf(accent, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.outline, secondarySurface)
    val card = RoundedCornerShape(18.dp)
    val field = RoundedCornerShape(12.dp)
    val pageInset = 20.dp
    val sectionGap = 16.dp
}

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, fontFeatureSettings = "tnum", letterSpacing = 0.sp
)

private val lightTypography = Typography(
    displaySmall = type(46, 54, FontWeight.SemiBold),
    headlineLarge = type(34, 41, FontWeight.Bold),
    headlineMedium = type(28, 35, FontWeight.SemiBold),
    headlineSmall = type(24, 31, FontWeight.SemiBold),
    titleLarge = type(20, 27, FontWeight.SemiBold),
    titleMedium = type(17, 24, FontWeight.SemiBold),
    titleSmall = type(15, 22, FontWeight.Medium),
    bodyLarge = type(16, 23), bodyMedium = type(14, 21), bodySmall = type(12, 18),
    labelLarge = type(16, 22, FontWeight.SemiBold),
    labelMedium = type(13, 19, FontWeight.Medium), labelSmall = type(11, 16, FontWeight.Medium)
)

private val lightColors = lightColorScheme(
    primary = Color(0xFF0066FF), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF0FF), onPrimaryContainer = Color(0xFF183F94),
    secondary = Color(0xFF626E9B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F3F9), onSecondaryContainer = Color(0xFF080B12),
    tertiary = Color(0xFF7561C4), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0EBFF), onTertiaryContainer = Color(0xFF473487),
    background = Color(0xFFF6F7F9), onBackground = Color(0xFF080B12),
    surface = Color(0xFFF6F7F9), onSurface = Color(0xFF080B12),
    surfaceVariant = Color(0xFFF0F3F9), onSurfaceVariant = Color(0xFF596577),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFF3F4F8), surfaceContainerHigh = Color(0xFFEDF0F7), surfaceContainerHighest = Color(0xFFE4E8F1),
    surfaceTint = Color.Transparent, outline = Color(0xFF767E8E), outlineVariant = Color(0xFFE0E4EB),
    error = Color(0xFFB3263D), onError = Color.White,
    errorContainer = Color(0xFFFFEBEF), onErrorContainer = Color(0xFF8B1830),
    inverseSurface = Color(0xFF252C3E), inverseOnSurface = Color.White, inversePrimary = Color(0xFFBBCDFF)
)

/** Source-compatible with existing preview fixtures; the approved product design is light-only. */
@Suppress("UNUSED_PARAMETER")
@Composable fun TrackerTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColors, typography = lightTypography,
        shapes = Shapes(small = Tokens.field, medium = Tokens.card, large = Tokens.card, extraLarge = RoundedCornerShape(28.dp)),
        content = content
    )
}
