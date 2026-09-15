package com.ridevibe.feature.admin.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.OpenSeatingSummary
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.SeatInspectDialog
import com.ridevibe.feature.admin.ui.components.StaffSeatGrid
import com.ridevibe.feature.admin.ui.components.StaffSeatLegend
import com.ridevibe.feature.admin.ui.components.StatTileGrid
import com.ridevibe.feature.admin.ui.components.StatTileSpec
import com.ridevibe.feature.admin.ui.components.TripOccupancyRow
import com.ridevibe.feature.admin.ui.formatCount
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.ui.formatPhp
import com.ridevibe.feature.admin.viewmodel.PartnerOverviewViewModel

/** Overview: the operator's headline numbers and today's departures. */
@Composable
fun PartnerOverviewTab(
    operatorId: Int?,
    viewModel: PartnerOverviewViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(operatorId) { viewModel.start(operatorId) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader("Overview", "Your services, upcoming departures and seats sold") }
        state.error?.let { item { InlineError(it) } }
        when {
            state.isLoading && state.overview == null -> item { LoadingRow() }
            state.overview != null -> {
                val overview = state.overview!!
                item {
                    StatTileGrid(
                        listOf(
                            StatTileSpec("Services", formatCount(overview.services)),
                            StatTileSpec("Routes served", formatCount(overview.routes)),
                            StatTileSpec("Upcoming trips", formatCount(overview.upcomingTrips)),
                            StatTileSpec("Next 24h departures", formatCount(overview.departuresNext24h)),
                            StatTileSpec("Seats sold (upcoming)", formatCount(overview.seatsSoldUpcoming)),
                            StatTileSpec("Upcoming sold value", formatPhp(overview.upcomingRevenuePhp), accent = true),
                        ),
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
            MicroLabel("Today's departures")
        }
        when {
            state.isLoading && state.todaysTrips.isEmpty() -> Unit
            state.todaysTrips.isEmpty() -> item { EmptyText("No departures today.") }
            else -> items(state.todaysTrips, key = { it.id }) { trip ->
                TripOccupancyRow(
                    trip = trip,
                    onClick = { viewModel.openTrip(trip) },
                    selected = state.selectedTrip?.id == trip.id,
                )
            }
        }
    }

    state.selectedTrip?.let { trip ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeTrip,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                val isBus = trip.rideKind == RideKind.BUS
                Text(if (isBus) "Seats" else "Occupancy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${trip.origin} → ${trip.destination} · ${formatPhDateTime(trip.departureEpochMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (isBus) {
                    when {
                        state.isSeatsLoading -> LoadingRow()
                        state.seatsError != null -> InlineError(state.seatsError)
                        state.seats.isEmpty() -> EmptyText("No seat inventory for this departure.")
                        else -> {
                            StaffSeatGrid(seats = state.seats, onSeatClick = viewModel::inspectSeat)
                            Spacer(modifier = Modifier.height(12.dp))
                            StaffSeatLegend(partnerView = true)
                        }
                    }
                } else {
                    OpenSeatingSummary(trip, partnerView = true)
                }
            }
        }
    }

    state.inspecting?.let { seat -> SeatInspectDialog(seat = seat, onDismiss = viewModel::dismissInspect) }
}
