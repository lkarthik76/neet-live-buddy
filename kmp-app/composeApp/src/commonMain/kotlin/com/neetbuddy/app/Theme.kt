package com.neetbuddy.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF0F172A)
val NavyLight = Color(0xFF1E293B)
val Blue = Color(0xFF2563EB)
val BlueDark = Color(0xFF1D4ED8)
val BlueLight = Color(0xFF3B82F6)
val Orange = Color(0xFFF97316)
val OrangeLight = Color(0xFFFB923C)
val OrangeDark = Color(0xFFEA580C)
val Gold = Color(0xFFFBBF24)
val SuccessGreen = Color(0xFF16A34A)
val ErrorRed = Color(0xFFDC2626)
val SurfaceLight = Color(0xFFF8FAFC)
val SurfaceDark = Color(0xFF0F172A)
val CardLight = Color.White
val CardDark = Color(0xFF1E293B)
val TextOnDark = Color(0xFFE2E8F0)
val SubtextLight = Color(0xFF64748B)
val SubtextDark = Color(0xFF94A3B8)
val BorderLight = Color(0xFFE2E8F0)
val BorderDark = Color(0xFF334155)

val LightColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Navy,
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF7ED),
    onSecondaryContainer = OrangeDark,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = Navy,
    surface = CardLight,
    onSurface = Navy,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SubtextLight,
    error = ErrorRed,
    onError = Color.White,
    outline = BorderLight,
)

val DarkColorScheme = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    primaryContainer = BlueDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = OrangeLight,
    onSecondary = Color.White,
    secondaryContainer = OrangeDark,
    onSecondaryContainer = Color(0xFFFFF7ED),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color.White,
    background = SurfaceDark,
    onBackground = TextOnDark,
    surface = CardDark,
    onSurface = TextOnDark,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = SubtextDark,
    error = Color(0xFFF87171),
    onError = Color.White,
    outline = BorderDark,
)

val NeetTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun NeetBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NeetTypography,
        content = content,
    )
}
