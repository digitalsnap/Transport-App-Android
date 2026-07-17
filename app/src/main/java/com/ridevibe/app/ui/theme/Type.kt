package com.ridevibe.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ridevibe.app.R

/**
 * Brand typeface: Inter (variable font, weight axis instanced per style).
 * On API < 26 variation settings are ignored and the font's default
 * instance renders — acceptable fallback for the minSdk 24 tail.
 */
private val Inter = FontFamily(
    interFont(FontWeight.Normal),
    interFont(FontWeight.Medium),
    interFont(FontWeight.SemiBold),
    interFont(FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun interFont(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Material 3 type scale re-based onto Inter, per the Visily design. */
val RideVibeTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Inter),
        displayMedium = base.displayMedium.copy(fontFamily = Inter),
        displaySmall = base.displaySmall.copy(fontFamily = Inter),
        headlineLarge = base.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
        bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
        bodySmall = base.bodySmall.copy(fontFamily = Inter),
        labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    )
}
