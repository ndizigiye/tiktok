package com.ndizigiye.loop.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Loop is dark-only: video reads best on black, and the chrome stays out of the way. */
private val LoopColorScheme = darkColorScheme(
    primary = LoopViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF34266F),
    onPrimaryContainer = Color(0xFFE4DDFF),
    inversePrimary = Color(0xFF5A3FD9),
    secondary = Color(0xFFB9A9FF),
    onSecondary = Color(0xFF1C1240),
    secondaryContainer = Color(0xFF2C2547),
    onSecondaryContainer = Color(0xFFE4DDFF),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF3D2400),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceTint = Color(0xFF9E9E9E),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF121212),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2A2A2A),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2E2E2E),
    surfaceDim = Color(0xFF0C0C0C),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2A2A2A),
)

private val LoopShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun LoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoopColorScheme,
        typography = LoopTypography,
        shapes = LoopShapes,
        content = content,
    )
}
