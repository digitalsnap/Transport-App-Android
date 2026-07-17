package com.ridevibe.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696B),
    secondary = Color(0xFF4A6363),
    tertiary = Color(0xFFF9A825),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD8DA),
    secondary = Color(0xFFB1CCCC),
    tertiary = Color(0xFFFFCC66),
)

@Composable
fun RideVibeTheme(useDarkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
