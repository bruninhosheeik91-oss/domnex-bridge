package com.domnex.cfi.bridge.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Sans = FontFamily.Default
val Mono = FontFamily.Monospace

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.6.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.8.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.9.sp
    )
)

val MonoDisplay = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.5).sp
)

val MonoNumber = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.2).sp
)

val MonoSmall = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 17.sp
)

val MicroCaps = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 2.2.sp
)
