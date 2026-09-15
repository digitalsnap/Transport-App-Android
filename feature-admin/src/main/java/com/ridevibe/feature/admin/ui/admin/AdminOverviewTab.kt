package com.ridevibe.feature.admin.ui.admin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.DayCount
import com.ridevibe.feature.admin.ui.components.BookingRow
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.KeyValueRow
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.SectionCard
import com.ridevibe.feature.admin.ui.components.StatTileGrid
import com.ridevibe.feature.admin.ui.components.StatTileSpec
import com.ridevibe.feature.admin.ui.formatCount
import com.ridevibe.feature.admin.ui.weekdayLabel
import com.ridevibe.feature.admin.viewmodel.AdminOverviewViewModel

/** Operations overview: live pulse of the RideVibe network. */
@Composable
fun AdminOverviewTab(
    onMessage: (String) -> Unit,
    viewModel: AdminOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val overview = state.overview

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                PageHeader(
                    "Operations overview",
                    "Live pulse of the RideVibe network",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh overview")
                }
            }
        }

        if (state.isLoading && overview == null) {
            item { LoadingRow() }
        }

        state.error?.let { error ->
            item {
                Column {
                    InlineError(error)
                    if (overview == null) Button(onClick = viewModel::load) { Text("Retry") }
                }
            }
        }

        if (overview != null) {
            item {
                StatTileGrid(
                    listOf(
                        StatTileSpec("Total bookings", formatCount(overview.bookings)),
                        StatTileSpec("Upcoming trips", formatCount(overview.upcomingTrips)),
                        StatTileSpec("Active holds", formatCount(overview.activeHolds)),
                        StatTileSpec("Cancelled", formatCount(overview.cancelledBookings), alert = overview.cancelledBookings > 0),
                        StatTileSpec("Refunds pending", formatCount(overview.refundsPending), accent = true),
                        StatTileSpec("Partners", formatCount(overview.operators)),
                    ),
                )
            }
            item {
                SectionCard("Bookings — last 7 days") { BookingsBarChart(overview.bookingsByDay) }
            }
            item {
                SectionCard("Network data") {
                    KeyValueRow("Terminals", formatCount(overview.terminals))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow("Directed routes", formatCount(overview.routes))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow("Services", formatCount(overview.services))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow("Journeys", formatCount(overview.journeys))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow("Seats (inventory)", formatCount(overview.seats))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow("Users", formatCount(overview.users))
                }
            }
            item { MicroLabel("Recent bookings", modifier = Modifier.padding(top = 4.dp)) }
            if (state.recentBookings.isEmpty()) {
                item { EmptyText("No bookings match.") }
            }
            items(state.recentBookings, key = { it.id }) { booking -> BookingRow(booking) }
        }
    }
}

/** Compose-drawn bar chart: one bar per day, count above, weekday below. */
@Composable
private fun BookingsBarChart(byDay: List<DayCount>) {
    if (byDay.isEmpty()) {
        EmptyText("No bookings in the last 7 days.")
        return
    }
    val max = maxOf(1, byDay.maxOf { it.count })
    val barArea = 110.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        byDay.forEach { day ->
            val fraction = (day.count.toFloat() / max).coerceIn(0.02f, 1f)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (day.count > 0) day.count.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barArea * fraction)
                        .background(
                            if (day.count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        ),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    weekdayLabel(day.day),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
