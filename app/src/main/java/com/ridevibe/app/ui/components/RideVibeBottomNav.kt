package com.ridevibe.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class BottomTab { HOME, BOOKINGS, WALLET, PROFILE }

/**
 * The app-wide bottom navigation, hosted by the root Scaffold so every screen
 * past the welcome page shares it. [selectedTab] is null on booking-flow
 * screens (results/seats/checkout/ticket) — no tab claims them.
 */
@Composable
fun RideVibeBottomNav(
    selectedTab: BottomTab?,
    onHomeClick: () -> Unit,
    onBookingsClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    val itemColors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = selectedTab == BottomTab.HOME,
            onClick = onHomeClick,
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = selectedTab == BottomTab.BOOKINGS,
            onClick = onBookingsClick,
            icon = { Icon(Icons.Filled.ConfirmationNumber, contentDescription = "Bookings") },
            label = { Text("Bookings") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = selectedTab == BottomTab.WALLET,
            onClick = { /* presentation only — wallet not built */ },
            icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Wallet") },
            label = { Text("Wallet") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = selectedTab == BottomTab.PROFILE,
            onClick = onProfileClick,
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = itemColors,
        )
    }
}
