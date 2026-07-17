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

// RideVibe brand palette (from the Visily design):
// indigo primary, lavender containers, pink promo accent, amber pending accent.
private val Indigo = Color(0xFF6366F1)
private val IndigoDark = Color(0xFF4F46E5)
private val Lavender = Color(0xFFE0E7FF)
private val LavenderSurface = Color(0xFFEEF0FB)
private val Pink = Color(0xFFEC4899)
private val PinkContainer = Color(0xFFFCE7F3)
private val Amber = Color(0xFFF59E0B)
private val AmberContainer = Color(0xFFFEF3C7)
private val Ink = Color(0xFF111827)
private val InkMuted = Color(0xFF6B7280)
private val PageBackground = Color(0xFFF7F7FB)
private val CardOutline = Color(0xFFE5E7EB)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Lavender,
    onPrimaryContainer = Color(0xFF3730A3),
    secondary = Pink,
    onSecondary = Color.White,
    secondaryContainer = PinkContainer,
    onSecondaryContainer = Color(0xFF9D174D),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFF92400E),
    background = PageBackground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = LavenderSurface,
    onSurfaceVariant = InkMuted,
    outline = CardOutline,
    outlineVariant = CardOutline,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Lavender,
    secondary = Color(0xFFF472B6),
    secondaryContainer = Color(0xFF831843),
    onSecondaryContainer = PinkContainer,
    tertiary = Color(0xFFFBBF24),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AmberContainer,
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF262A35),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF374151),
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
