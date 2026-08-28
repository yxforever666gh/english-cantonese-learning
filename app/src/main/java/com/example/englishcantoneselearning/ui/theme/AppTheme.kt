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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val EditorialPaper = Color(0xFFF5F2EA)
val EditorialSurface = Color(0xFFFFFEFA)
val EditorialInk = Color(0xFF172524)
val EditorialPine = Color(0xFF173F3B)
val EditorialMint = Color(0xFFDDEAE4)
val EditorialTerracotta = Color(0xFFC96B43)
val EditorialOutline = Color(0xFFD8D3C8)

private val EditorialLightColors = lightColorScheme(
    primary = EditorialPine,
    onPrimary = Color.White,
    primaryContainer = EditorialMint,
    onPrimaryContainer = EditorialInk,
    secondary = EditorialTerracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E2D7),
    onSecondaryContainer = Color(0xFF522412),
    tertiary = Color(0xFF6D6253),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE6D9),
    onTertiaryContainer = Color(0xFF2C2821),
    error = Color(0xFF9C3B2C),
    onError = Color.White,
    errorContainer = Color(0xFFF8DDD7),
    onErrorContainer = Color(0xFF45150F),
    background = EditorialPaper,
    onBackground = EditorialInk,
    surface = EditorialSurface,
    onSurface = EditorialInk,
    surfaceVariant = Color(0xFFEAE6DD),
    onSurfaceVariant = Color(0xFF5C625D),
    outline = EditorialOutline,
    outlineVariant = Color(0xFFE7E2D8),
    inverseSurface = EditorialInk,
    inverseOnSurface = EditorialPaper,
    inversePrimary = Color(0xFFAAD2C5),
    surfaceDim = Color(0xFFE4E0D8),
    surfaceBright = EditorialSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF9F3),
    surfaceContainer = Color(0xFFF2EFE7),
    surfaceContainerHigh = Color(0xFFECE8DF),
    surfaceContainerHighest = Color(0xFFE5E1D8),
)

private val EditorialTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.35.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.65.sp,
    ),
)

private val EditorialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EnglishCantoneseLearningTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = EditorialLightColors,
        typography = EditorialTypography,
        shapes = EditorialShapes,
        content = content,
    )
}
