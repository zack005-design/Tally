package com.smartexpense.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** True inside a dark [TrackerTheme]; used for the few tints that are not Material roles. */
internal val LocalDarkTheme = staticCompositionLocalOf { false }

/** Picks the light or dark version of a decorative tint. */
@Composable internal fun tint(light: Color, dark: Color): Color = if (LocalDarkTheme.current) dark else light

/** Decorative icon and badge tints. Light values are the original design; dark values keep the hue at low brightness. */
internal object Tints {
    val blueSoft @Composable get() = tint(Color(0xFFEAF1FF), Color(0xFF1B2F57))
    val lavenderSoft @Composable get() = tint(Color(0xFFF0EAFF), Color(0xFF2E2650))
    val lavender @Composable get() = tint(Color(0xFF7052C4), Color(0xFFC3B4FF))
    val peachSoft @Composable get() = tint(Color(0xFFFFEEE3), Color(0xFF3D2719))
    val peach @Composable get() = tint(Color(0xFFC35A26), Color(0xFFFFB38A))
    val pinkSoft @Composable get() = tint(Color(0xFFFFEBF1), Color(0xFF3D1B28))
    val pink @Composable get() = tint(Color(0xFFB53061), Color(0xFFFF9DBE))
    val graySoft @Composable get() = tint(Color(0xFFEEF0F5), Color(0xFF242934))
    val warningSoft @Composable get() = tint(Color(0xFFFFF3DD), Color(0xFF3A2C12))
    val warning @Composable get() = tint(Color(0xFF865315), Color(0xFFF2C27A))
}

/** Semantic colours for every screen. Values come from the light or dark scheme. */
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

// Dark surfaces step up in lightness instead of using shadows; cards sit one step above the page.
private val darkColors = darkColorScheme(
    primary = Color(0xFF8DB2FF), onPrimary = Color(0xFF07275F),
    primaryContainer = Color(0xFF1B2F57), onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Color(0xFFB4BEDF), onSecondary = Color(0xFF1E2640),
    secondaryContainer = Color(0xFF1E232E), onSecondaryContainer = Color(0xFFE6EAF3),
    tertiary = Color(0xFFC3B4FF), onTertiary = Color(0xFF2E1F6B),
    tertiaryContainer = Color(0xFF2E2650), onTertiaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFF0E1116), onBackground = Color(0xFFE8EBF2),
    surface = Color(0xFF0E1116), onSurface = Color(0xFFE8EBF2),
    surfaceVariant = Color(0xFF1E232E), onSurfaceVariant = Color(0xFFA6AFC0),
    surfaceContainerLowest = Color(0xFF181C24), surfaceContainerLow = Color(0xFF14181F),
    surfaceContainer = Color(0xFF1A1E27), surfaceContainerHigh = Color(0xFF21262F), surfaceContainerHighest = Color(0xFF292F3A),
    surfaceTint = Color.Transparent, outline = Color(0xFF808A9C), outlineVariant = Color(0xFF2B313D),
    error = Color(0xFFFF8A9B), onError = Color(0xFF5C0A1B),
    errorContainer = Color(0xFF4A1420), onErrorContainer = Color(0xFFFFD9DF),
    inverseSurface = Color(0xFFE8EBF2), inverseOnSurface = Color(0xFF14181F), inversePrimary = Color(0xFF0066FF)
)

@Composable fun TrackerTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColors else lightColors, typography = lightTypography,
            shapes = Shapes(small = Tokens.field, medium = Tokens.card, large = Tokens.card, extraLarge = RoundedCornerShape(28.dp)),
            content = content
        )
    }
}
