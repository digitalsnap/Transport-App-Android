package com.ridevibe.feature.search.ui

import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Charcoal chrome tokens (design: Charcoal anchors all bars). Feature modules
// can't reach the :app theme, so the two chrome constants are mirrored here.
internal val ChromeCharcoal = Color(0xFF2C363F)
internal val ChromeTiffanySoft = Color(0xFF81D8D0)

@Composable
internal fun charcoalTopBarColors(): TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(
    containerColor = ChromeCharcoal,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)
