package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DreamiitDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueDeep,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = VoidBlack,
    secondaryContainer = ElevatedCardHigh,
    onSecondaryContainer = ElectricCyan,
    tertiary = SuccessEmerald,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = VoidBlack,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ElevatedCard,
    surfaceContainerHigh = ElevatedCardHigh,
    outline = BorderSubtle,
    outlineVariant = Color(0xFF152036),
    error = LiveCrimson,
    onError = Color.White
)

val DreamiitShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DreamiitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DreamiitDarkColorScheme,
        typography = DreamiitTypography,
        shapes = DreamiitShapes,
        content = content
    )
}
