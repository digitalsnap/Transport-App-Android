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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.NewService
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.ServiceUpdate
import com.ridevibe.feature.admin.ui.components.ButtonSpinner
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.SectionCard
import com.ridevibe.feature.admin.ui.components.ServiceRow
import com.ridevibe.feature.admin.ui.components.ToggleChip
import com.ridevibe.feature.admin.ui.formatHour
import com.ridevibe.feature.admin.viewmodel.PartnerServicesViewModel

/** "My services": the bus types the operator inputs, with create and fare/hours/duration edit. */
@Composable
fun PartnerServicesTab(
    operatorId: Int?,
    readOnly: Boolean,
    onMessage: (String) -> Unit,
    viewModel: PartnerServicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(operatorId) { viewModel.start(operatorId) }
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
                    onCreate = viewModel::createService,
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
            MicroLabel("My services (${state.services.size})")
        }
        state.error?.let { item { InlineError(it) } }
        when {
            state.isLoading && state.services.isEmpty() -> item { LoadingRow() }
            state.services.isEmpty() -> item { EmptyText("No services yet — add one above and it becomes searchable in the rider app.") }
            else -> items(state.services, key = { it.id }) { service ->
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
                    onSave = viewModel::saveEdit,
                    onCancel = viewModel::closeEdit,
                )
            }
        }
    }
}

@Composable
private fun AddServiceCard(
    isSaving: Boolean,
    error: String?,
    resetKey: Int,
    onCreate: (NewService) -> Unit,
) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var rideKind by rememberSaveable { mutableStateOf(RideKind.BUS) }
    var busClass by rememberSaveable { mutableStateOf(BusClass.ORDINARY) }
    var fare by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    // A successful create clears the form, like the dashboard's form.reset().
    LaunchedEffect(resetKey) {
        if (resetKey > 0) {
            origin = ""; destination = ""; fare = ""; duration = ""; hours = emptySet(); localError = null
        }
    }

    SectionCard("Add a service") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = origin,
                onValueChange = { origin = it },
                label = { Text("Origin") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Destination") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        MicroLabel("Ride kind")
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RideKind.values().forEach { kind ->
                ToggleChip(kind.name, selected = rideKind == kind, onClick = { rideKind = kind })
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        MicroLabel("Bus type (class)")
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BusClass.values().forEach { cls ->
                ToggleChip(cls.name, selected = busClass == cls, onClick = { busClass = cls })
            }
        }
        HintText(
            if (rideKind == RideKind.BUS) {
                if (busClass == BusClass.LUXURY) "Allocates a 2×1 layout, 30 seats per trip." else "Allocates a 2×2 layout, 44 seats per trip."
            } else {
                "Open seating (roll-on/roll-off): ${if (busClass == BusClass.LUXURY) 30 else 44} passenger capacity per sailing."
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
        InlineError(localError ?: error)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val fareValue = fare.toDoubleOrNull()
                val durationValue = duration.toIntOrNull()
                localError = when {
                    origin.isBlank() || destination.isBlank() -> "Origin and destination are required"
                    fareValue == null || fareValue <= 0 -> "Enter the fare in PHP"
                    durationValue == null || durationValue < 15 -> "Duration must be at least 15 minutes"
                    hours.isEmpty() -> "Pick at least one departure hour"
                    else -> null
                }
                if (localError == null) {
                    onCreate(
                        NewService(
                            origin = origin.trim(),
                            destination = destination.trim(),
                            busClass = busClass,
                            rideKind = rideKind,
                            farePhp = fareValue!!,
                            departureHours = hours.sorted(),
                            durationMinutes = durationValue!!,
                        ),
                    )
                }
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) ButtonSpinner() else Text("Create service", fontWeight = FontWeight.Bold)
        }
        HintText("Trips for the next 14 days generate on the spot — riders can book right away.", modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun EditServiceSheet(
    service: OperatorService,
    isSaving: Boolean,
    error: String?,
    onSave: (ServiceUpdate) -> Unit,
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
            "Edit — ${service.origin} → ${service.destination} (${service.busClass.name})",
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
                onClick = {
                    val fareValue = fare.toDoubleOrNull()
                    val durationValue = duration.toIntOrNull()
                    onSave(
                        ServiceUpdate(
                            farePhp = fareValue?.takeIf { it > 0 && it != service.farePhp },
                            durationMinutes = durationValue?.takeIf { it >= 15 && it != service.durationMinutes },
                            departureHours = hours.sorted().takeIf { it != service.departureHours.sorted() },
                        ),
                    )
                },
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
