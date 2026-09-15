package com.ridevibe.feature.admin.ui.partner

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.feature.admin.ui.components.ButtonSpinner
import com.ridevibe.feature.admin.ui.components.DateSelector
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.OpenSeatingSummary
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.SeatChips
import com.ridevibe.feature.admin.ui.components.SeatInspectDialog
import com.ridevibe.feature.admin.ui.components.SectionCard
import com.ridevibe.feature.admin.ui.components.StaffSeatGrid
import com.ridevibe.feature.admin.ui.components.StaffSeatLegend
import com.ridevibe.feature.admin.ui.components.TripOccupancyRow
import com.ridevibe.feature.admin.ui.components.CodeText
import com.ridevibe.feature.admin.ui.formatHours
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.ui.isValidTimeHm
import com.ridevibe.feature.admin.viewmodel.PartnerTripsViewModel

/** Trips: per-date departures, seat maps with passenger names, extra departures and on-site sales. */
@Composable
fun PartnerTripsTab(
    operatorId: Int?,
    readOnly: Boolean,
    onMessage: (String) -> Unit,
    viewModel: PartnerTripsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val writable = !readOnly
    LaunchedEffect(operatorId) { viewModel.start(operatorId, writable) }
    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                "Trips",
                if (writable) {
                    "Your departures per date with sold, held and available seats. Tap one for its seat map, " +
                        "the boarding manifest names, and on-site sales."
                } else {
                    "This operator's departures per date with sold, held and available seats."
                },
            )
        }
        item { DateSelector(dateIso = state.dateIso, onPrevious = viewModel::previousDay, onNext = viewModel::nextDay) }
        if (writable) {
            item {
                AddExtraTripCard(
                    services = state.services,
                    defaultDateIso = state.dateIso,
                    isAdding = state.isAddingTrip,
                    error = state.addTripError,
                    onAdd = viewModel::addExtraTrip,
                )
            }
        }
        state.error?.let { item { InlineError(it) } }
        when {
            state.isLoading && state.trips.isEmpty() -> item { LoadingRow() }
            state.trips.isEmpty() -> item { EmptyText("No departures on ${state.dateIso}.") }
            else -> {
                item { HintText("${state.trips.size} departure${if (state.trips.size == 1) "" else "s"} on ${state.dateIso}") }
                items(state.trips, key = { it.id }) { trip ->
                    TripOccupancyRow(
                        trip = trip,
                        onClick = { viewModel.openTrip(trip) },
                        selected = state.selectedTrip?.id == trip.id,
                    )
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
            TripSheet(
                trip = trip,
                writable = writable,
                viewModel = viewModel,
            )
        }
    }

    state.inspecting?.let { seat -> SeatInspectDialog(seat = seat, onDismiss = viewModel::dismissInspect) }

    state.saleIssued?.let { issued ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSale,
            title = { Text("On-site sale recorded", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Ticket issued as PAID · CASH_ON_BOARD. Rider apps viewing this trip have been updated.")
                    Spacer(modifier = Modifier.height(10.dp))
                    MicroLabel("Ticket id")
                    Spacer(modifier = Modifier.height(4.dp))
                    CodeText(issued.ticketId, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(10.dp))
                    MicroLabel("Seats")
                    Spacer(modifier = Modifier.height(4.dp))
                    SeatChips(issued.seatLabels)
                }
            },
            confirmButton = { Button(onClick = viewModel::dismissSale) { Text("Done") } },
        )
    }
}

@Composable
private fun TripSheet(
    trip: TripOccupancy,
    writable: Boolean,
    viewModel: PartnerTripsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val isBus = trip.rideKind == RideKind.BUS
    var passengerName by rememberSaveable(trip.id) { mutableStateOf("") }
    var count by rememberSaveable(trip.id) { mutableStateOf("1") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(if (isBus) "Seats" else "Occupancy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "${trip.origin} → ${trip.destination} · ${formatPhDateTime(trip.departureEpochMillis)} · ${trip.busClass.name}",
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
                    StaffSeatGrid(
                        seats = state.seats,
                        pickedLabels = state.pickedSeatLabels,
                        onSeatClick = { seat -> viewModel.onSeatTapped(seat, writable) },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StaffSeatLegend(pickable = writable, partnerView = true)
                }
            }
        } else {
            OpenSeatingSummary(trip, partnerView = true)
        }

        if (writable) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionCard("Record on-site sale") {
                HintText(
                    if (isBus) {
                        "Walk-up counter sale: tap the seats above, then confirm. Seats a rider is buying in the app " +
                            "right now are refused (409) — pick another."
                    } else {
                        "Walk-up counter sale for open seating: enter how many passengers."
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (isBus) {
                    MicroLabel("Selected seats")
                    Spacer(modifier = Modifier.height(4.dp))
                    SeatChips(state.pickedSeatLabels.sorted())
                } else {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { count = it.filter(Char::isDigit).take(2) },
                        label = { Text("Passengers") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = passengerName,
                    onValueChange = { passengerName = it },
                    label = { Text("Passenger name") },
                    placeholder = { Text("Walk-in passenger") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                InlineError(state.saleError)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.recordOnsiteSale(passengerName, count.toIntOrNull()) },
                    enabled = !state.isSelling && (if (isBus) state.pickedSeatLabels.isNotEmpty() else (count.toIntOrNull() ?: 0) > 0),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSelling) ButtonSpinner() else Text("Confirm sale (PAID · cash at counter)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AddExtraTripCard(
    services: List<OperatorService>,
    defaultDateIso: String,
    isAdding: Boolean,
    error: String?,
    onAdd: (serviceId: Int, dateIso: String, timeHm: String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var serviceId by rememberSaveable { mutableStateOf(-1) }
    var date by rememberSaveable { mutableStateOf(defaultDateIso) }
    var time by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = services.firstOrNull { it.id == serviceId }

    SectionCard("Add extra trip") {
        HintText("A one-off departure outside the regular hours. Seats generate immediately and the rider app can book it right away.")
        Spacer(modifier = Modifier.height(10.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.let { "${it.origin} → ${it.destination} · ${it.busClass.name} · ${formatHours(it.departureHours)}" } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Service") },
                placeholder = { Text(if (services.isEmpty()) "No services yet" else "Choose a service") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                services.forEach { service ->
                    DropdownMenuItem(
                        text = { Text("${service.origin} → ${service.destination} · ${service.busClass.name} · ${service.rideKind.name}") },
                        onClick = {
                            serviceId = service.id
                            expanded = false
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Date (yyyy-MM-dd)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                label = { Text("Time (HH:mm PH)") },
                placeholder = { Text("14:30") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        InlineError(localError ?: error)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                localError = when {
                    selected == null -> "Choose a service"
                    !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) -> "Date must be yyyy-MM-dd"
                    !isValidTimeHm(time) -> "Time must be HH:mm (24-hour, PH)"
                    else -> null
                }
                if (localError == null) onAdd(selected!!.id, date, time)
            },
            enabled = !isAdding && services.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isAdding) ButtonSpinner() else Text("Add departure", fontWeight = FontWeight.Bold)
        }
    }
}
