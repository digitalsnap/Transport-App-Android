package com.ridevibe.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.ridevibe.app.ui.theme.charcoalTopBarColors

/**
 * The five passenger tabs, each with the nav route it owns. This is the single
 * place that ties a route to a tab: the nav graph builds its route constants
 * from [route] and highlights the tab via [fromRoute].
 */
enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
    val testTag: String,
) {
    HOME("home", "Home", Icons.Filled.Home, "Home", "nav_home"),
    BOOKINGS("bookings", "Bookings", Icons.Filled.ConfirmationNumber, "Bookings", "nav_bookings"),
    ITINERARY("itinerary", "Itinerary", Icons.Filled.Map, "Trip itinerary planner", "nav_itinerary"),
    WALLET("wallet", "Wallet", Icons.Filled.AccountBalanceWallet, "Wallet", "nav_wallet"),
    CHAT("chat", "Chat", Icons.AutoMirrored.Filled.Chat, "Support chat", "nav_chat");

    companion object {
        /** The tab whose root is [route], or null for every other destination. */
        fun fromRoute(route: String?): BottomTab? = values().firstOrNull { it.route == route }
    }
}

/**
 * The app-wide bottom navigation, hosted by the root Scaffold on the tab roots
 * and Profile. Profile lives in the header (top-right), not here. [selectedTab]
 * is null on Profile, which belongs to no tab.
 */
@Composable
fun RideVibeBottomNav(
    selectedTab: BottomTab?,
    onTabClick: (BottomTab) -> Unit,
) {
    // Same charcoal as the top app bars so the chrome matches top and bottom;
    // active items take the primary accent, inactive the muted on-surface role.
    val itemColors = NavigationBarItemDefaults.colors(
        indicatorColor = Color.Transparent,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationBar(containerColor = charcoalTopBarColors().containerColor) {
        BottomTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabClick(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.contentDescription) },
                label = { Text(tab.label) },
                colors = itemColors,
                modifier = Modifier.testTag(tab.testTag),
            )
        }
    }
}
