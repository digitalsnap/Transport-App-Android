package com.ridevibe.feature.admin.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.displayLabel
import com.ridevibe.feature.admin.ui.components.ButtonSpinner
import com.ridevibe.feature.admin.ui.components.ConfirmDialog
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.ErrorWithRetry
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.SectionCard
import com.ridevibe.feature.admin.ui.components.ServiceRow
import com.ridevibe.feature.admin.ui.components.ToggleChip
import com.ridevibe.feature.admin.ui.formatHour
import com.ridevibe.feature.admin.ui.humanize
import com.ridevibe.feature.admin.viewmodel.PartnerServicesViewModel

/** "My services": the bus types the operator inputs, with create and fare/hours/duration edit. */
@Composable
fun PartnerServicesTab(
    sessionKey: String,
    operatorId: Int?,
    readOnly: Boolean,
    onMessage: (String) -> Unit,
    viewModel: PartnerServicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(sessionKey, operatorId) { viewModel.start(sessionKey, operatorId) }
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
                "My services",
                "The bus types you input: origin, destination, class, fare and departure hours. " +
                    "Seat allocation follows the class (2×2 · 44 seats, LUXURY 2×1 · 30).",
            )
        }
        if (!readOnly) {
            item {
                AddServiceCard(
                    isSaving = state.isSaving,
                    error = if (state.editing == null) state.formError else null,
                    resetKey = state.createdCount,
                    locations = state.locations,
                    isLocationsLoading = state.isLocationsLoading,
                    locationsError = state.locationsError,
                    onRetryLocations = viewModel::loadLocations,
                    capacityFor = { busClass, rideKind -> viewModel.capacityFor(busClass, rideKind) },
                    isDefaultSeaCapacity = { rideKind -> viewModel.isDefaultSeaCapacity(rideKind) },
                    onCreate = viewModel::createService,
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
            MicroLabel("My services (${state.services.size})")
        }
        when {
            state.isLoading && state.services.isEmpty() -> item { LoadingRow() }
            state.error != null && state.services.isEmpty() -> item { ErrorWithRetry(state.error!!, onRetry = viewModel::load) }
            state.services.isEmpty() -> item { EmptyText("No services yet — add one above and it becomes searchable in the rider app.") }
            else -> {
                state.error?.let { item { InlineError(it) } }
                items(state.services, key = { it.id }) { service ->
                    ServiceRow(
                        service = service,
                        trailing = if (readOnly) {
                            null
                        } else {
                            { TextButton(onClick = { viewModel.openEdit(service) }) { Text("Edit fare · hours · duration") } }
                        },
                    )
                }
            }
        }
    }

    state.editing?.let { service ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeEdit,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            key(service.id) {
                EditServiceSheet(
                    service = service,
                    isSaving = state.isSaving,
                    error = state.formError,
                    onSave = viewModel::requestSaveEdit,
                    onCancel = viewModel::closeEdit,
                )
            }
        }
        state.pendingUpdate?.let {
            ConfirmDialog(
                title = "Remove departure hours?",
                text = "Dropping ${state.pendingRemovedHours.joinToString(", ") { formatHour(it) }} deletes future " +
                    "${service.origin} → ${service.destination} trips at those hours that have no sales and no live holds. " +
                    "Departures with sold seats are kept.",
                confirmLabel = "Save changes",
                destructive = true,
                onConfirm = viewModel::confirmPendingEdit,
                onDismiss = viewModel::cancelPendingEdit,
            )
        }
    }
}

@Composable
private fun AddServiceCard(
    isSaving: Boolean,
    error: String?,
    resetKey: Int,
    locations: List<String>,
    isLocationsLoading: Boolean,
    locationsError: String?,
    onRetryLocations: () -> Unit,
    capacityFor: (BusClass, RideKind) -> Int,
    isDefaultSeaCapacity: (RideKind) -> Boolean,
    onCreate: (
        origin: String,
        destination: String,
        rideKind: RideKind,
        busClass: BusClass,
        fareText: String,
        durationText: String,
        hours: Set<Int>,
    ) -> Unit,
) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var rideKind by rememberSaveable { mutableStateOf(RideKind.BUS) }
    var busClass by rememberSaveable { mutableStateOf(BusClass.ORDINARY) }
    var fare by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf(setOf<Int>()) }
    // The create counter this form last reacted to. Restored with the fields after rotation, so a
    // restored form is not wiped by a create that happened before the config change.
    var resetSeen by rememberSaveable { mutableStateOf(resetKey) }

    LaunchedEffect(resetKey) {
        if (resetKey != resetSeen) {
            resetSeen = resetKey
            origin = ""; destination = ""; fare = ""; duration = ""; hours = emptySet()
        }
    }

    SectionCard("Add a service") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LocationPicker(
                label = "Origin",
                value = origin,
                options = locations,
                enabled = locations.isNotEmpty(),
                onPick = { origin = it },
                modifier = Modifier.weight(1f),
            )
            LocationPicker(
                label = "Destination",
                value = destination,
                options = locations,
                enabled = locations.isNotEmpty(),
                onPick = { destination = it },
                modifier = Modifier.weight(1f),
            )
        }
        when {
            isLocationsLoading -> HintText("Loading terminals…", modifier = Modifier.padding(top = 4.dp))
            locationsError != null -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                InlineError(locationsError, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetryLocations) { Text("Retry") }
            }
            else -> HintText("Pick from RideVibe's terminals and ports — new places are added by RideVibe admins.", modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        MicroLabel("Ride kind")
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RideKind.values().forEach { kind ->
                ToggleChip(humanize(kind.name), selected = rideKind == kind, onClick = { rideKind = kind })
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        MicroLabel(if (rideKind == RideKind.BUS) "Bus type (class)" else "Accommodation class")
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BusClass.values().forEach { cls ->
                ToggleChip(cls.displayLabel(rideKind), selected = busClass == cls, onClick = { busClass = cls })
            }
        }
        val capacity = capacityFor(busClass, rideKind)
        HintText(
            if (rideKind == RideKind.BUS) {
                if (busClass == BusClass.LUXURY) "Allocates a 2×1 layout, $capacity seats per trip." else "Allocates a 2×2 layout, $capacity seats per trip."
            } else {
                "Open seating (roll-on/roll-off): $capacity passenger capacity per sailing" +
                    if (isDefaultSeaCapacity(rideKind)) " (RideVibe default until the vessel capacity is set)." else "."
            },
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = fare,
                onValueChange = { fare = it },
                label = { Text("Fare (PHP)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter(Char::isDigit) },
                label = { Text("Duration (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        MicroLabel("Departure hours (PH)")
        Spacer(modifier = Modifier.height(4.dp))
        HourPicker(selected = hours, onToggle = { hour -> hours = if (hour in hours) hours - hour else hours + hour })
        InlineError(error)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onCreate(origin, destination, rideKind, busClass, fare, duration, hours) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) ButtonSpinner() else Text("Create service", fontWeight = FontWeight.Bold)
        }
        HintText("Trips for the next 14 days generate on the spot — riders can book right away.", modifier = Modifier.padding(top = 6.dp))
    }
}

/** Read-only dropdown over the terminal corpus so operators cannot invent a location. */
@Composable
private fun LocationPicker(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(if (enabled) "Choose" else "Loading…") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onPick(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EditServiceSheet(
    service: OperatorService,
    isSaving: Boolean,
    error: String?,
    onSave: (fareText: String, durationText: String, hours: Set<Int>) -> Unit,
    onCancel: () -> Unit,
) {
    var fare by rememberSaveable { mutableStateOf(service.farePhp.toString()) }
    var duration by rememberSaveable { mutableStateOf(service.durationMinutes.toString()) }
    var hours by rememberSaveable { mutableStateOf(service.departureHours.toSet()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            "Edit — ${service.origin} → ${service.destination} (${service.busClass.displayLabel(service.rideKind)})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        HintText(
            "Changes apply only to future departures with no sold seats — sold departures keep what was bought. " +
                "Removing an hour deletes only future trips with no sales and no live holds. " +
                "The bus type isn't editable: it defines the physical seat map, so add a new service instead.",
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = fare,
                onValueChange = { fare = it },
                label = { Text("Fare (PHP)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter(Char::isDigit) },
                label = { Text("Duration (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        MicroLabel("Departure hours (PH)")
        Spacer(modifier = Modifier.height(4.dp))
        HourPicker(selected = hours, onToggle = { hour -> hours = if (hour in hours) hours - hour else hours + hour })
        InlineError(error)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onSave(fare, duration, hours) },
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            ) {
                if (isSaving) ButtonSpinner() else Text("Save changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 24 toggle chips, one per hour of the day. */
@Composable
private fun HourPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..23).forEach { hour ->
            ToggleChip(formatHour(hour), selected = hour in selected, onClick = { onToggle(hour) })
        }
    }
}
