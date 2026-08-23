package com.domnex.cfi.bridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DomnexBridgeColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = BridgeBlack,
    primaryContainer = GoldHighlight,
    onPrimaryContainer = BridgeBlack,
    secondary = SuccessGreen,
    onSecondary = BridgeBlack,
    secondaryContainer = NavySurface,
    onSecondaryContainer = SuccessGreen,
    tertiary = WarningAmber,
    onTertiary = BridgeBlack,
    tertiaryContainer = NavySurfaceHigh,
    onTertiaryContainer = WarningAmber,
    error = FailureRose,
    onError = Color.White,
    errorContainer = NavySurfaceHigh,
    onErrorContainer = FailureRose,
    background = BridgeBlack,
    onBackground = TextPrimary,
    surface = BridgeBlack,
    onSurface = TextPrimary,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondary,
    inverseSurface = NavyCard,
    inverseOnSurface = TextPrimary,
    outline = Color.White.copy(alpha = 0.15f),
    outlineVariant = Color.White.copy(alpha = 0.05f),
    scrim = Color.Black,
    surfaceBright = NavySurfaceAlt,
    surfaceDim = BridgeBlack,
    surfaceContainerLowest = BridgeBlack,
    surfaceContainerLow = NavySecondary,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavyCard,
    surfaceContainerHighest = NavySurfaceAlt
)

@Composable
fun CFIBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DomnexBridgeColorScheme,
        typography = AppTypography,
        shapes = BridgeShapes,
        content = content
    )
}
