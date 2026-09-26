package com.ridevibe.app.ui.bookings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.app.ui.theme.charcoalTopBarColors
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.spaceNoun

/** Booking history: upcoming trips first, then completed past trips. */
@Composable
fun BookingsScreen(
    onBack: () -> Unit,
    onOpenTicket: (ticketId: String) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: BookingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // A ticket bought since this tab was last seen must show up without a manual refresh.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Bookings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Filled.Person, contentDescription = "Profile")
                    }
                },
                colors = charcoalTopBarColors(),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    uiState.error.orEmpty(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = viewModel::load) { Text("Retry", fontWeight = FontWeight.Bold) }
            }

            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.isOffline) {
                        item { OfflineBanner() }
                    }

                    item { SectionHeader("Upcoming") }

                    if (uiState.upcoming.isEmpty()) {
                        item {
                            Text(
                                "No upcoming trips yet — book your next ride from the Home tab!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(uiState.upcoming, key = { it.id }) { ticket ->
                            BookingCard(ticket = ticket, isPast = false, onClick = { onOpenTicket(ticket.id) })
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SectionHeader("Past bookings")
                    }

                    if (uiState.past.isEmpty()) {
                        item {
                            Text(
                                "No past bookings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(uiState.past, key = { it.id }) { ticket ->
                            BookingCard(ticket = ticket, isPast = true, onClick = { onOpenTicket(ticket.id) })
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Offline — showing saved tickets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun BookingCard(ticket: Ticket, isPast: Boolean, onClick: () -> Unit) {
    val rideKind = ticket.trip.rideKind
    val inactive = isPast || ticket.status != BookingStatus.CONFIRMED
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (inactive) 0.dp else 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        if (rideKind == RideKind.BUS) Icons.Filled.DirectionsBus else Icons.Filled.DirectionsBoat,
                        contentDescription = when (rideKind) {
                            RideKind.BUS -> "Bus"
                            RideKind.FERRY -> "Ferry"
                            RideKind.FASTCRAFT -> "Fastcraft"
                        },
                        tint = if (inactive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        ticket.trip.operatorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    )
                }
                StatusBadge(ticket = ticket, isPast = isPast)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${ticket.trip.origin} → ${ticket.trip.destination}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val plural = ticket.seatLabels.size != 1
            Text(
                "${PhTime.formatDateTime(ticket.trip.departureEpochMillis, "EEE, MMM d, yyyy • h:mm a")} • " +
                    "${rideKind.spaceNoun(plural = plural).replaceFirstChar { it.uppercase() }} " +
                    "${ticket.seatLabels.joinToString(", ")} • Ticket ${ticket.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ticket.paymentStatus == PaymentStatus.CASH_ON_BOARD && !inactive) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Pay the conductor on board",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(ticket: Ticket, isPast: Boolean) {
    val (label, container, content) = when {
        ticket.status == BookingStatus.CANCELLED -> Triple(
            "CANCELLED",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        ticket.status == BookingStatus.REFUNDED -> Triple(
            "REFUNDED",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        isPast -> Triple(
            "COMPLETED",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ticket.paymentStatus == PaymentStatus.PAID -> Triple(
            "PAID",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
        )

        else -> Triple(
            "RESERVED",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
