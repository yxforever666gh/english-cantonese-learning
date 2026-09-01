package com.example.englishcantoneselearning.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AppBackground = Color(0xFFF6F7F3)
val AppSurface = Color(0xFFFFFFFF)
val AppSurfaceSubtle = Color(0xFFF0F3EF)
val AppPine = Color(0xFF174A43)
val AppPineDeep = Color(0xFF103832)
val AppMint = Color(0xFFDDEBE5)
val AppInk = Color(0xFF18211F)
val AppTextSecondary = Color(0xFF66706C)
val AppTextMuted = Color(0xFF89918E)
val AppDivider = Color(0xFFE2E6E1)
val AppTerracotta = Color(0xFFC66A45)
val AppSuccess = Color(0xFF2F6B52)
val AppWarning = Color(0xFF93621F)
val AppError = Color(0xFFA43D32)

// Compatibility aliases kept while feature screens migrate to the neutral design tokens.
val EditorialPaper = AppBackground
val EditorialSurface = AppSurface
val EditorialInk = AppInk
val EditorialPine = AppPine
val EditorialMint = AppMint
val EditorialTerracotta = AppTerracotta
val EditorialOutline = AppDivider

internal object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val page = 20.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 40.dp
}

internal object AppDimensions {
    val minimumTouchTarget = 48.dp
    val primaryButtonHeight = 50.dp
    val navigationBarHeight = 72.dp
    val miniPlayerHeight = 80.dp
    val pageMaxWidth = 720.dp
    val divider = 1.dp
    val activeIndicator = 3.dp
    val icon = 24.dp
}

internal object AppMotion {
    const val fast = 150
    const val standard = 180
    const val deliberate = 200
}

internal object AppRadii {
    val label: Dp = 10.dp
    val control: Dp = 12.dp
    val card: Dp = 16.dp
    val sheet: Dp = 24.dp
}

private val AppLightColors = lightColorScheme(
    primary = AppPine,
    onPrimary = Color.White,
    primaryContainer = AppMint,
    onPrimaryContainer = AppPineDeep,
    secondary = AppPineDeep,
    onSecondary = Color.White,
    secondaryContainer = AppSurfaceSubtle,
    onSecondaryContainer = AppInk,
    tertiary = AppTerracotta,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E2D8),
    onTertiaryContainer = Color(0xFF572517),
    error = AppError,
    onError = Color.White,
    errorContainer = Color(0xFFF8DEDA),
    onErrorContainer = Color(0xFF521A14),
    background = AppBackground,
    onBackground = AppInk,
    surface = AppSurface,
    onSurface = AppInk,
    surfaceVariant = AppSurfaceSubtle,
    onSurfaceVariant = AppTextSecondary,
    outline = AppDivider,
    outlineVariant = Color(0xFFEDF0EC),
    inverseSurface = AppPineDeep,
    inverseOnSurface = AppBackground,
    inversePrimary = Color(0xFFA7D5C8),
    surfaceDim = Color(0xFFE7EAE5),
    surfaceBright = AppSurface,
    surfaceContainerLowest = AppSurface,
    surfaceContainerLow = Color(0xFFF9FAF7),
    surfaceContainer = AppSurfaceSubtle,
    surfaceContainerHigh = Color(0xFFEAEDE9),
    surfaceContainerHighest = Color(0xFFE4E8E3),
)

private val AppTypography = Typography(
    displayLarge = appTextStyle(34, 40, FontWeight.SemiBold, -0.3f),
    headlineLarge = appTextStyle(28, 34, FontWeight.SemiBold, -0.2f),
    headlineMedium = appTextStyle(24, 30, FontWeight.SemiBold, -0.1f),
    headlineSmall = appTextStyle(20, 26, FontWeight.SemiBold),
    titleLarge = appTextStyle(20, 26, FontWeight.SemiBold),
    titleMedium = appTextStyle(16, 22, FontWeight.SemiBold),
    titleSmall = appTextStyle(15, 21, FontWeight.SemiBold),
    bodyLarge = appTextStyle(16, 24, FontWeight.Normal),
    bodyMedium = appTextStyle(15, 22, FontWeight.Normal),
    bodySmall = appTextStyle(13, 19, FontWeight.Normal),
    labelLarge = appTextStyle(14, 20, FontWeight.SemiBold, 0.1f),
    labelMedium = appTextStyle(12, 16, FontWeight.Medium, 0.2f),
    labelSmall = appTextStyle(11, 16, FontWeight.Medium, 0.3f),
)

private fun appTextStyle(
    size: Int,
    height: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadii.label),
    small = RoundedCornerShape(AppRadii.control),
    medium = RoundedCornerShape(AppRadii.card),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(AppRadii.sheet),
)

@Composable
fun EnglishCantoneseLearningTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppLightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
