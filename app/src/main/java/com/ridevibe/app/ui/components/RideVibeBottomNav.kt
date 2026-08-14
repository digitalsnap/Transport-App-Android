package com.ridevibe.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ridevibe.app.ui.theme.Charcoal
import com.ridevibe.app.ui.theme.SlateSage
import com.ridevibe.app.ui.theme.Tiffany

enum class BottomTab { HOME, BOOKINGS, SCAN, WALLET, CHAT }

/**
 * The app-wide bottom navigation, hosted by the root Scaffold so every screen
 * past the welcome page shares it. Profile lives in the header (top-right),
 * not here. [selectedTab] is null on booking-flow screens — no tab claims them.
 */
@Composable
fun RideVibeBottomNav(
    selectedTab: BottomTab?,
    onHomeClick: () -> Unit,
    onBookingsClick: () -> Unit,
    onScanClick: () -> Unit,
    onWalletClick: () -> Unit,
    onChatClick: () -> Unit,
) {
    // Charcoal chrome: active items Tiffany, inactive Slate Sage, no indicator pill.
    val itemColors = NavigationBarItemDefaults.colors(
        indicatorColor = Color.Transparent,
        selectedIconColor = Tiffany,
        selectedTextColor = Tiffany,
        unselectedIconColor = SlateSage,
        unselectedTextColor = SlateSage,
    )
    NavigationBar(containerColor = Charcoal) {
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
            selected = selectedTab == BottomTab.SCAN,
            onClick = onScanClick,
            icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan a ticket QR") },
            label = { Text("Scan") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = selectedTab == BottomTab.WALLET,
            onClick = onWalletClick,
            icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Wallet") },
            label = { Text("Wallet") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = selectedTab == BottomTab.CHAT,
            onClick = onChatClick,
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Support chat") },
            label = { Text("Chat") },
            colors = itemColors,
        )
    }
}

