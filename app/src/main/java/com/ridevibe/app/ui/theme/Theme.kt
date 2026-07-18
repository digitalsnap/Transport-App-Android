package com.ridevibe.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// RideVibe brand palette — "Tiffany edition" identity kit:
// Tiffany Blue primary, Deep Teal support, Gold accent, Ink text.
private val TiffanyBlue = Color(0xFF0ABAB5)
private val TiffanyLight = Color(0xFF3AD9D3)
private val DeepTeal = Color(0xFF0A8F8A)
private val Gold = Color(0xFFE3C77A)
private val GoldInk = Color(0xFF5A4A1E)
private val Ink = Color(0xFF12302E)
private val InkMuted = Color(0xFF6F9490)
private val GlassLight = Color(0xFFCFF3F0)
private val GlassMid = Color(0xFFA7E9E5)
private val AquaBright = Color(0xFF7FE3DE)
private val WheelInk = Color(0xFF0C3B39)
private val PageBackground = Color(0xFFEAF3F2)
private val CardOutline = Color(0xFFCBE2DF)
private val DarkBackground = Color(0xFF0B1F1E)
private val DarkSurface = Color(0xFF0F2E2C)
private val DarkSurfaceVariant = Color(0xFF1B403D)

private val LightColors = lightColorScheme(
    primary = TiffanyBlue,
    onPrimary = Color.White,
    primaryContainer = GlassLight,
    onPrimaryContainer = WheelInk,
    secondary = DeepTeal,
    onSecondary = Color.White,
    secondaryContainer = GlassMid,
    onSecondaryContainer = WheelInk,
    tertiary = Gold,
    onTertiary = GoldInk,
    tertiaryContainer = Color(0xFFF6ECCB),
    onTertiaryContainer = GoldInk,
    background = PageBackground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFDFEFED),
    onSurfaceVariant = InkMuted,
    outline = CardOutline,
    outlineVariant = CardOutline,
)

private val DarkColors = darkColorScheme(
    primary = AquaBright,
    onPrimary = WheelInk,
    primaryContainer = DeepTeal,
    onPrimaryContainer = GlassLight,
    secondary = TiffanyLight,
    onSecondary = WheelInk,
    secondaryContainer = Color(0xFF14524E),
    onSecondaryContainer = GlassLight,
    tertiary = Gold,
    onTertiary = GoldInk,
    tertiaryContainer = Color(0xFF4A3D18),
    onTertiaryContainer = Color(0xFFF6ECCB),
    background = DarkBackground,
    onBackground = Color(0xFFE4F1EF),
    surface = DarkSurface,
    onSurface = Color(0xFFE4F1EF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF8FB5B1),
    outline = Color(0xFF2C544F),
)

// Generously rounded corners throughout, per the Visily design.
private val RideVibeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RideVibeTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = RideVibeTypography,
        shapes = RideVibeShapes,
        content = content,
    )
}
