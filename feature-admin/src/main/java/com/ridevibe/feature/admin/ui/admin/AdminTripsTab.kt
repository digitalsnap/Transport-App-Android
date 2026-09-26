package com.ridevibe.feature.admin.ui.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.feature.admin.ui.components.ButtonSpinner
import com.ridevibe.feature.admin.ui.components.DateSelector
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.ErrorWithRetry
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.OpenSeatingSummary
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.SeatInspectDialog
import com.ridevibe.feature.admin.ui.components.StaffSeatGrid
import com.ridevibe.feature.admin.ui.components.StaffSeatLegend
import com.ridevibe.feature.admin.ui.components.TripOccupancyRow
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.viewmodel.AdminTripsViewModel

/** Trips: browse departures; seat maps apply to bus trips (ferries and fastcraft board open). */
@Composable
fun AdminTripsTab(
    sessionKey: String,
    onMessage: (String) -> Unit,
    viewModel: AdminTripsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(sessionKey) { viewModel.start(sessionKey) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader("Trips", "Browse departures; seat maps apply to bus trips (ferries and fastcraft board open)")
        }
        item {
            DateSelector(
                dateIso = state.dateIso,
                onPrevious = viewModel::previousDay,
                onNext = viewModel::nextDay,
                onPick = viewModel::setDate,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.operatorFilter,
                    onValueChange = viewModel::onOperatorFilterChanged,
                    label = { Text("Company") },
                    placeholder = { Text("any") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.placeFilter,
                    onValueChange = viewModel::onPlaceFilterChanged,
                    label = { Text("Origin / destination") },
                    placeholder = { Text("any") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Button(onClick = viewModel::load, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                if (state.isLoading) ButtonSpinner() else Text("Load", fontWeight = FontWeight.Bold)
            }
        }
        when {
            state.isLoading && state.trips.isEmpty() -> item { LoadingRow() }
            state.error != null && state.trips.isEmpty() -> item { ErrorWithRetry(state.error!!, onRetry = viewModel::load) }
            !state.hasLoaded -> item { HintText("Pick a date and filters, then Load.") }
            state.trips.isEmpty() -> item { EmptyText("No trips on ${state.dateIso} for those filters.") }
            else -> {
                state.error?.let { item { InlineError(it) } }
                item { HintText("${state.trips.size} departure${if (state.trips.size == 1) "" else "s"} on ${state.dateIso}") }
                items(state.trips, key = { it.id }) { trip ->
                    TripOccupancyRow(
                        trip = trip,
                        onClick = { viewModel.openTrip(trip) },
                        selected = state.selectedTrip?.id == trip.id,
                    )
                }
                if (state.canLoadMore) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::loadMore,
                            enabled = !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isLoadingMore) ButtonSpinner() else Text("Load more")
                        }
                    }
                }
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
                Text(
                    if (isBus) "Seat allocation" else "Occupancy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${trip.origin} → ${trip.destination} · ${formatPhDateTime(trip.departureEpochMillis)}" +
                        (trip.operatorName?.let { " · $it" } ?: ""),
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
                            StaffSeatLegend()
                        }
                    }
                } else {
                    OpenSeatingSummary(trip)
                }
            }
        }
    }

    state.inspecting?.let { seat -> SeatInspectDialog(seat = seat, onDismiss = viewModel::dismissInspect) }
}
