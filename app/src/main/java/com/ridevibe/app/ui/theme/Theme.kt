package com.ridevibe.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// RideVibe brand palette — Tiffany blue color pass (design: ridevibe-ui-design-redesign):
// Charcoal is the only dark anchor (bars, chrome); Tiffany is reserved for actions
// and live data; Tiffany Soft for brand surfaces and holds; Vapour is the canvas;
// Gold is limited to hold timers, low-seat warnings and the ticket trim.
val Charcoal = Color(0xFF2C363F)
val Tiffany = Color(0xFF0ABAB5)
val TiffanySoft = Color(0xFF81D8D0)
val Vapour = Color(0xFFF2F8F7)
val SlateSage = Color(0xFF7C8C8B)
val Gold = Color(0xFFE3C77A)
val Divider = Color(0xFFDCE6E5)
val DividerSoft = Color(0xFFEDF3F2)

/** Text on Tiffany Soft surfaces (design rule; not a background token). */
val InkOnSoft = Color(0xFF12302E)

/** Unavailable seats on the seat map. */
val SeatTaken = Color(0xFFC9D2D1)

private val LightColors = lightColorScheme(
    primary = Tiffany,
    onPrimary = Color.White,
    primaryContainer = TiffanySoft,
    onPrimaryContainer = InkOnSoft,
    secondary = Tiffany,
    onSecondary = Color.White,
    secondaryContainer = TiffanySoft,
    onSecondaryContainer = InkOnSoft,
    tertiary = Gold,
    onTertiary = Charcoal,
    tertiaryContainer = Gold,
    onTertiaryContainer = Charcoal,
    background = Vapour,
    onBackground = Charcoal,
    surface = Color.White,
    onSurface = Charcoal,
    surfaceVariant = Vapour,
    onSurfaceVariant = SlateSage,
    outline = Divider,
    outlineVariant = DividerSoft,
    inverseSurface = Charcoal,
    inverseOnSurface = Color.White,
)

// Dark mode keeps the same accent roles on charcoal-derived grounds.
private val DarkColors = darkColorScheme(
    primary = Tiffany,
    onPrimary = Color.White,
    primaryContainer = TiffanySoft,
    onPrimaryContainer = InkOnSoft,
    secondary = TiffanySoft,
    onSecondary = InkOnSoft,
    secondaryContainer = TiffanySoft,
    onSecondaryContainer = InkOnSoft,
    tertiary = Gold,
    onTertiary = Charcoal,
    tertiaryContainer = Gold,
    onTertiaryContainer = Charcoal,
    background = Color(0xFF1D242B),
    onBackground = Color(0xFFE7EDEF),
    surface = Charcoal,
    onSurface = Color(0xFFE7EDEF),
    surfaceVariant = Color(0xFF39444E),
    onSurfaceVariant = Color(0xFFA5B3B2),
    outline = Color(0xFF46525C),
    outlineVariant = Color(0xFF39444E),
    inverseSurface = Vapour,
    inverseOnSurface = Charcoal,
)

// Generously rounded corners throughout, per the Visily design.
private val RideVibeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Sets the status- and navigation-bar icon colour for as long as the caller is
 * composed. [lightIcons] true = white icons (for charcoal chrome); false = dark
 * icons (for a screen drawn straight on the light canvas). Restored on dispose.
 */
@Composable
fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, lightIcons) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

/** Charcoal chrome for center-aligned top app bars (design rule: Charcoal anchors all bars). */
@Composable
fun charcoalTopBarColors(): TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(
    containerColor = Charcoal,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)

@Composable
fun RideVibeTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Edge-to-edge: the status bar sits on the charcoal strip the root Scaffold
    // draws and the navigation bar on the charcoal bottom nav / scrim. Both are
    // Charcoal in either scheme (design rule: Charcoal anchors all bars), so the
    // system icons are light in either scheme too. A screen that needs otherwise
    // can call [SystemBarIcons] itself.
    SystemBarIcons(lightIcons = true)
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = RideVibeTypography,
        shapes = RideVibeShapes,
        content = content,
    )
}
