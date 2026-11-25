package com.smartexpense.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.R

/** True inside a dark [TrackerTheme]; used for the few tints that are not Material roles. */
internal val LocalDarkTheme = staticCompositionLocalOf { false }

/** Picks the light or dark version of a decorative tint. */
@Composable internal fun tint(light: Color, dark: Color): Color = if (LocalDarkTheme.current) dark else light

/** Soft badge backgrounds and their foregrounds, following the redesign's status colours. */
internal object Tints {
    val blueSoft @Composable get() = tint(Color(0xFFE4EEFB), Color(0xFF0F2A4A))
    val lavenderSoft @Composable get() = tint(Color(0xFFE4EEFB), Color(0xFF0F2A4A))
    val lavender @Composable get() = tint(Color(0xFF0A66D8), Color(0xFF6AACFF))
    val peachSoft @Composable get() = tint(Color(0xFFFFEFE2), Color(0xFF3D2412))
    val peach @Composable get() = tint(Color(0xFFC93400), Color(0xFFFF9F6B))
    val pinkSoft @Composable get() = tint(Color(0xFFFFE6EA), Color(0xFF3D1520))
    val pink @Composable get() = tint(Color(0xFFD70015), Color(0xFFFF7A85))
    val graySoft @Composable get() = tint(Color(0xFFE9E9EE), Color(0xFF3A3A3C))
    val warningSoft @Composable get() = tint(Color(0xFFFFF4D6), Color(0xFF3A2C0A))
    val warning @Composable get() = tint(Color(0xFF946200), Color(0xFFF2C04F))
}

/** Solid icon-square colours from the redesign. Glyphs on them are always white. */
internal object CategoryColors {
    val brand @Composable get() = tint(Color(0xFF0A66D8), Color(0xFF0A84FF))
    val food @Composable get() = tint(Color(0xFFD15A00), Color(0xFFD15A00))
    val transport @Composable get() = tint(Color(0xFF0A66D8), Color(0xFF0A66D8))
    val groceries @Composable get() = tint(Color(0xFF6B8E00), Color(0xFF7FA30A))
    val bills @Composable get() = tint(Color(0xFF5048C8), Color(0xFF5E5CE6))
    val shopping @Composable get() = tint(Color(0xFFD6104A), Color(0xFFD6104A))
    val wallet @Composable get() = tint(Color(0xFF8C6C48), Color(0xFF8C6C48))
    val moneyIn @Composable get() = tint(Color(0xFF1A6E30), Color(0xFF30A14E))
    val gray @Composable get() = tint(Color(0xFF6C6C70), Color(0xFF6C6C70))
}

/** Semantic colours for every screen. Values come from the light or dark scheme. */
object Tokens {
    val background @Composable get() = MaterialTheme.colorScheme.background
    val paper @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
    val surface @Composable get() = MaterialTheme.colorScheme.surface
    val secondarySurface @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val tonal @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val inputSurface @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
    val border @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val text @Composable get() = MaterialTheme.colorScheme.onSurface
    val muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val accent @Composable get() = MaterialTheme.colorScheme.primary
    /** Filled buttons keep the same blue in both themes, always under white text. */
    val accentFill = Color(0xFF0A66D8)
    val warning @Composable get() = tint(Color(0xFFB04600), Color(0xFFFF9F0A))
    val expense @Composable get() = text
    val positive @Composable get() = MaterialTheme.colorScheme.tertiary
    val owed @Composable get() = Tints.peach
    val negative @Composable get() = MaterialTheme.colorScheme.error
    val blue @Composable get() = accent
    val chartTones @Composable get() = listOf(CategoryColors.food, CategoryColors.groceries, CategoryColors.transport, CategoryColors.bills, CategoryColors.gray)
    val card = RoundedCornerShape(26.dp)
    val tile = RoundedCornerShape(22.dp)
    val field = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(percent = 50)
    val pageInset = 16.dp
    val sectionGap = 16.dp
}

/** Inter stands in for SF Pro: an open-licence face with the same neutral, tabular figures. */
internal val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = InterFamily, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, fontFeatureSettings = "tnum", letterSpacing = tracking.sp
)

// Sizes follow the iOS text styles: Large Title 34, Title 1 28, Title 2 22, Title 3 20, Headline 17, Body 17, Callout 16, Subhead 15, Footnote 13, Caption 12.
private val appTypography = Typography(
    displaySmall = type(40, 46, FontWeight.Bold, -0.6f),
    headlineLarge = type(34, 41, FontWeight.Bold, -0.4f),
    headlineMedium = type(28, 34, FontWeight.Bold, -0.3f),
    headlineSmall = type(22, 28, FontWeight.Bold, -0.2f),
    titleLarge = type(20, 25, FontWeight.SemiBold),
    titleMedium = type(17, 22, FontWeight.SemiBold),
    titleSmall = type(16, 21, FontWeight.Medium),
    bodyLarge = type(17, 22), bodyMedium = type(15, 20), bodySmall = type(13, 18),
    labelLarge = type(17, 22, FontWeight.SemiBold),
    labelMedium = type(13, 18, FontWeight.Medium), labelSmall = type(11, 13, FontWeight.Medium)
)

private val lightColors = lightColorScheme(
    primary = Color(0xFF0A66D8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EEFB), onPrimaryContainer = Color(0xFF0A66D8),
    secondary = Color(0xFF6C6C70), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9EE), onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFF1F7A35), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2F1E6), onTertiaryContainer = Color(0xFF0E3F1B),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF000000),
    surface = Color(0xFFF2F2F7), onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE9E9EE), onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF2F2F7), surfaceContainerHigh = Color(0xFFE9E9EE), surfaceContainerHighest = Color(0xFFE3E3E8),
    surfaceTint = Color.Transparent, outline = Color(0xFF8E8E93), outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFC9001B), onError = Color.White,
    errorContainer = Color(0xFFFFE5E7), onErrorContainer = Color(0xFF8A000D),
    inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color.White, inversePrimary = Color(0xFF4C9BFF)
)

// Dark follows the redesign: a black page, #1C1C1E grouped cards and a brighter blue tint.
private val darkColors = darkColorScheme(
    primary = Color(0xFF4C9AFF), onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF0F2A4A), onPrimaryContainer = Color(0xFF6AACFF),
    secondary = Color(0xFF98989F), onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color.White,
    tertiary = Color(0xFF30D158), onTertiary = Color(0xFF00290C),
    tertiaryContainer = Color(0xFF133A1E), onTertiaryContainer = Color(0xFFC4F2CF),
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF3A3A3C), onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceContainerLowest = Color(0xFF1C1C1E), surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color.Black, surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceTint = Color.Transparent, outline = Color(0xFF8E8E93), outlineVariant = Color(0xFF3A3A3C),
    error = Color(0xFFFF6961), onError = Color(0xFF4A0006),
    errorContainer = Color(0xFF47141A), onErrorContainer = Color(0xFFFFD9DB),
    inverseSurface = Color(0xFFF2F2F7), inverseOnSurface = Color(0xFF1C1C1E), inversePrimary = Color(0xFF0A66D8)
)

/** Narrowest width the layouts are composed for; smaller phones are scaled to fit it. */
internal const val COMPACT_LAYOUT_WIDTH_DP = 360f

/** Narrowest width every screen is verified at; layouts are never composed narrower than this. */
internal const val MINIMUM_LAYOUT_WIDTH_DP = 320f

/**
 * Layout scale for a window [widthDp] wide. Phones narrower than 360dp shrink proportionally,
 * normally no further than 82% so text stays comfortable. Extremely narrow windows (a compact
 * phone at its largest Display size, or a narrow split-screen pane) shrink further when needed,
 * so the layout always gets at least [MINIMUM_LAYOUT_WIDTH_DP] and nothing is cut off.
 */
internal fun adaptiveLayoutScale(widthDp: Int): Float =
    if (widthDp in 1 until COMPACT_LAYOUT_WIDTH_DP.toInt())
        maxOf(widthDp / COMPACT_LAYOUT_WIDTH_DP, minOf(.82f, widthDp / MINIMUM_LAYOUT_WIDTH_DP))
    else 1f

/**
 * Fits every phone width. Phones narrower than 360dp (small screens, or a large Display size
 * setting) shrink the whole layout proportionally (see [adaptiveLayoutScale]) so nothing is cut
 * off. Wider phones keep their native density and gain room instead of growing. The user's
 * font-size setting is preserved as a ratio, so text scaling still works on every screen.
 */
@Composable internal fun AdaptiveDensity(content: @Composable () -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val scale = adaptiveLayoutScale(width)
    if (scale == 1f) content()
    else CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale), content = content)
}

@Composable fun TrackerTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColors else lightColors, typography = appTypography,
            shapes = Shapes(small = Tokens.field, medium = Tokens.card, large = Tokens.card, extraLarge = RoundedCornerShape(28.dp)),
        ) { AdaptiveDensity(content) }
    }
}
