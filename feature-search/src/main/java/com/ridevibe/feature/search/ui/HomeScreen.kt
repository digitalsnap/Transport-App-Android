package com.ridevibe.feature.search.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.TripType
import com.ridevibe.feature.search.R
import com.ridevibe.feature.search.viewmodel.SearchFormState
import com.ridevibe.feature.search.viewmodel.SearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home / "Where to next?" screen per the Visily design (page 2). */
@Composable
fun HomeScreen(
    onSearch: (
        origin: String,
        destination: String,
        dateMillis: Long,
        busClass: BusClass?,
        adults: Int,
        children: Int,
        infants: Int,
        bookingForSelf: Boolean,
    ) -> Unit,
    onProfileClick: () -> Unit,
    onExplore: (query: String, dateMillis: Long, returnDateMillis: Long?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val form by viewModel.formState.collectAsState()
    var showDepartPicker by remember { mutableStateOf(false) }
    var showReturnPicker by remember { mutableStateOf(false) }
    var showPassengerDialog by remember { mutableStateOf(false) }
    var locationPickerTarget by remember { mutableStateOf<LocationTarget?>(null) }
    // Header search: picking a place routes to the cross-mode results page.
    var showExploreSearch by remember { mutableStateOf(false) }
    // "Choose your Ride" service tiles; the trip form opens as a bottom sheet.
    // Saveable so the chosen tile survives navigating to results and back.
    var selectedService by rememberSaveable { mutableStateOf<RideService?>(null) }
    var showTripSheet by remember { mutableStateOf(false) }
    // Presentation-only, like the deals rail — no loyalty program exists yet.
    var frequentTraveler by rememberSaveable { mutableStateOf(false) }

    if (showDepartPicker) {
        DateDialog(
            initial = form.departureDateMillis,
            onPicked = viewModel::onDateSelected,
            onDismiss = { showDepartPicker = false },
        )
    }
    if (showReturnPicker) {
        DateDialog(
            initial = form.returnDateMillis ?: form.departureDateMillis,
            onPicked = viewModel::onReturnDateSelected,
            onDismiss = { showReturnPicker = false },
        )
    }
    if (showPassengerDialog) {
        PassengerCountDialog(
            adults = form.adults,
            children = form.children,
            infants = form.infants,
            onConfirm = { a, c, i ->
                viewModel.onPassengersChanged(a, c, i)
                showPassengerDialog = false
            },
            onDismiss = { showPassengerDialog = false },
        )
    }
    locationPickerTarget?.let { target ->
        LocationPickerDialog(
            title = if (target == LocationTarget.FROM) "Select origin" else "Select destination",
            locations = form.locations,
            onSelected = { name ->
                when (target) {
                    LocationTarget.FROM -> viewModel.onOriginSelected(name)
                    LocationTarget.TO -> viewModel.onDestinationSelected(name)
                }
                locationPickerTarget = null
            },
            onDismiss = { locationPickerTarget = null },
        )
    }

    if (showExploreSearch) {
        ExploreSearchDialog(
            locations = form.locations,
            onDisplayResults = { query, dateMillis, returnDateMillis ->
                showExploreSearch = false
                onExplore(query, dateMillis, returnDateMillis)
            },
            onDismiss = { showExploreSearch = false },
        )
    }

    selectedService?.let { service ->
        if (showTripSheet) {
            TripSearchSheet(
                service = service,
                form = form,
                frequentTraveler = frequentTraveler,
                onFrequentTravelerChanged = { frequentTraveler = it },
                onDismiss = { showTripSheet = false },
                onTripTypeChanged = viewModel::onTripTypeChanged,
                onPickFrom = { locationPickerTarget = LocationTarget.FROM },
                onPickTo = { locationPickerTarget = LocationTarget.TO },
                onSwap = viewModel::onSwapLocations,
                onPickDepartDate = { showDepartPicker = true },
                onPickReturnDate = { showReturnPicker = true },
                onPickPassengers = { showPassengerDialog = true },
                onBookingForSelfChanged = viewModel::onBookingForSelfChanged,
                onSearch = {
                    viewModel.primeCart()
                    showTripSheet = false
                    onSearch(
                        form.origin,
                        form.destination,
                        form.departureDateMillis ?: return@TripSearchSheet,
                        form.busClassFilter,
                        form.adults,
                        form.children,
                        form.infants,
                        form.bookingForSelf,
                    )
                },
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Search-first header on Charcoal chrome: tapping the bar opens the
            // destination picker (predictive search); profile lives top-right.
            Surface(color = ChromeCharcoal) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showExploreSearch = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (form.destination.isBlank()) "Search trips" else form.destination,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (form.destination.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "Profile",
                            tint = ChromeTiffanySoft,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text("Where to next?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Buy your tickets now",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            ChooseRideCard(
                selected = selectedService,
                onSelect = { service ->
                    selectedService = service
                    showTripSheet = true
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
            HotDealsSection()

            Spacer(modifier = Modifier.height(20.dp))
            InviteFriendsCard()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private enum class LocationTarget { FROM, TO }

/** Bookable service verticals. Ferries/fastcrafts search the same mock inventory until the CRS grows them. */
private enum class RideService(val label: String, val searchLabel: String, val icon: ImageVector) {
    PROVINCIAL_BUSES("Provincial Buses", "Search buses", Icons.Filled.DirectionsBus),
    FERRIES("Ferries", "Search ferries", Icons.Filled.DirectionsBoat),
    FASTCRAFTS("Fastcrafts", "Search fastcrafts", Icons.Filled.Speed),
}

@Composable
private fun ChooseRideCard(selected: RideService?, onSelect: (RideService) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Transport Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RideService.entries.forEach { service ->
                    ServiceTile(
                        service = service,
                        selected = service == selected,
                        onClick = { onSelect(service) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceTile(
    service: RideService,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
        ) {
            Icon(
                service.icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                service.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The trip selector, moved off the home card into a modal sheet (opens from a
 * service tile; its own window renders above the bottom navigation). Location,
 * date, and passenger pickers are dialogs, so they stack above the sheet.
 */
@Composable
private fun TripSearchSheet(
    service: RideService,
    form: SearchFormState,
    frequentTraveler: Boolean,
    onFrequentTravelerChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTripTypeChanged: (TripType) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    onPickDepartDate: () -> Unit,
    onPickReturnDate: () -> Unit,
    onPickPassengers: () -> Unit,
    onBookingForSelfChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header: service title + compact trip-type pill pair
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    service.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TripTypePill("One-way", form.tripType == TripType.ONE_WAY) { onTripTypeChanged(TripType.ONE_WAY) }
                Spacer(modifier = Modifier.width(6.dp))
                TripTypePill("Round-trip", form.tripType == TripType.ROUND_TRIP) { onTripTypeChanged(TripType.ROUND_TRIP) }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocationField(
                        label = "From",
                        value = form.origin,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onPickFrom,
                    )
                    LocationField(
                        label = "To",
                        value = form.destination,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = onPickTo,
                    )
                }
                IconButton(
                    onClick = onSwap,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = "Swap origin and destination",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Depart + Return side by side; Return is disabled on one-way.
            val roundTrip = form.tripType == TripType.ROUND_TRIP
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(
                    icon = { Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) },
                    caption = "DEPART",
                    value = form.departureDateMillis?.let(::formatDate) ?: "Pick a date",
                    modifier = Modifier.weight(1f),
                    onClick = onPickDepartDate,
                )
                InfoTile(
                    icon = {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            null,
                            tint = if (roundTrip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    caption = "RETURN",
                    value = if (roundTrip) form.returnDateMillis?.let(::formatDate) ?: "Pick a date" else "—",
                    modifier = Modifier.weight(1f),
                    onClick = if (roundTrip) onPickReturnDate else null,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Passenger count + Frequent traveler switch (switch is presentation-only).
            Surface(
                onClick = onPickPassengers,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            "PASSENGERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(form.passengersLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Frequent traveler",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(checked = frequentTraveler, onCheckedChange = onFrequentTravelerChanged)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Who's traveling — decides how checkout treats the primary passenger.
            Text(
                "WHO'S TRAVELING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.bookingForSelf,
                    onClick = { onBookingForSelfChanged(true) },
                    label = { Text("I'm traveling") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                FilterChip(
                    selected = !form.bookingForSelf,
                    onClick = { onBookingForSelfChanged(false) },
                    label = { Text("Booking for someone else") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSearch,
                enabled = form.canSearch,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(service.searchLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TripTypePill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun LocationField(
    label: String,
    value: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = iconTint)
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.ifBlank { "Select location" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun InfoTile(
    icon: @Composable () -> Unit,
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Location picker with predictive search ───────────────────────────────────

@Composable
private fun LocationPickerDialog(
    title: String,
    locations: List<TerminalLocation>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Input-driven only: no default listing; matches appear as the user types.
    val filtered = remember(query, locations) {
        if (query.isBlank()) emptyList() else locations.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search terminals & destinations") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    when {
                        query.isBlank() -> item {
                            Text(
                                "Start typing to find terminals, ports, and destinations.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }

                        filtered.isEmpty() -> item {
                            Text(
                                "No matching locations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }

                        else -> items(filtered, key = { it.name }) { location ->
                            LocationRow(location, highlight = false) { onSelected(location.name) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Header search: type → pick a place or whole region → the trip-type toggle,
 * date selector, and a "Display Results" pill append below the choice.
 */
@Composable
private fun ExploreSearchDialog(
    locations: List<TerminalLocation>,
    onDisplayResults: (query: String, dateMillis: Long, returnDateMillis: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var roundTrip by remember { mutableStateOf(false) }
    var departMillis by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var returnMillis by remember { mutableStateOf<Long?>(null) }
    var showDepartPicker by remember { mutableStateOf(false) }
    var showReturnPicker by remember { mutableStateOf(false) }

    if (showDepartPicker) {
        DateDialog(
            initial = departMillis,
            onPicked = { departMillis = it },
            onDismiss = { showDepartPicker = false },
        )
    }
    if (showReturnPicker) {
        DateDialog(
            initial = returnMillis ?: departMillis,
            onPicked = { returnMillis = it },
            onDismiss = { showReturnPicker = false },
        )
    }

    val filtered = remember(query, locations) {
        if (query.isBlank()) emptyList() else locations.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val matchingRegions = remember(query, locations) {
        if (query.isBlank()) {
            emptyList()
        } else {
            locations.map { it.region }.filter { it.isNotBlank() }.distinct()
                .filter { it.contains(query.trim(), ignoreCase = true) }
        }
    }

    val canDisplay = selected != null && departMillis != null && (!roundTrip || returnMillis != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search trips", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selected = null // editing the text restarts the suggestion stage
                    },
                    placeholder = { Text("Search terminals & destinations") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (selected == null) {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        when {
                            query.isBlank() -> item {
                                Text(
                                    "Start typing to find terminals, ports, destinations, or a whole region.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }

                            filtered.isEmpty() && matchingRegions.isEmpty() -> item {
                                Text(
                                    "No matching locations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }

                            else -> {
                                items(matchingRegions, key = { "region-$it" }) { region ->
                                    RegionRow(region) {
                                        selected = region
                                        query = region
                                    }
                                }
                                items(filtered, key = { it.name }) { location ->
                                    LocationRow(location, highlight = false) {
                                        selected = location.name
                                        query = location.name
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Stage 2: trip type + dates + Display Results.
                    Row {
                        TripTypePill("One-way", !roundTrip) { roundTrip = false }
                        Spacer(modifier = Modifier.width(6.dp))
                        TripTypePill("Round-trip", roundTrip) { roundTrip = true }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoTile(
                            icon = { Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) },
                            caption = "DEPART",
                            value = departMillis?.let(::formatDate) ?: "Pick a date",
                            modifier = Modifier.weight(1f),
                            onClick = { showDepartPicker = true },
                        )
                        InfoTile(
                            icon = {
                                Icon(
                                    Icons.Filled.CalendarMonth,
                                    null,
                                    tint = if (roundTrip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            caption = "RETURN",
                            value = if (roundTrip) returnMillis?.let(::formatDate) ?: "Pick a date" else "—",
                            modifier = Modifier.weight(1f),
                            onClick = if (roundTrip) {
                                { showReturnPicker = true }
                            } else {
                                null
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val place = selected ?: return@Button
                            val depart = departMillis ?: return@Button
                            onDisplayResults(place, depart, returnMillis.takeIf { roundTrip })
                        },
                        enabled = canDisplay,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text("Display Results", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A whole-region suggestion (all hubs in Luzon/Visayas/Mindanao at once). */
@Composable
private fun RegionRow(region: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Hub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    region,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "All terminals & ports in this region",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun LocationRow(location: TerminalLocation, highlight: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (highlight) Icons.Filled.Hub else Icons.Filled.Place,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                location.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

// ── Passenger count dialog ───────────────────────────────────────────────────

@Composable
private fun PassengerCountDialog(
    adults: Int,
    children: Int,
    infants: Int,
    onConfirm: (adults: Int, children: Int, infants: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var adultCount by remember { mutableStateOf(adults) }
    var childCount by remember { mutableStateOf(children) }
    var infantCount by remember { mutableStateOf(infants) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passengers", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CounterRow("Adults", "12 years and above", adultCount, min = 1) { adultCount = it }
                CounterRow("Children", "2–11 years, seat required", childCount, min = 0) { childCount = it }
                CounterRow("Infants", "Under 2, lap-held — free of charge", infantCount, min = 0, max = 5) { infantCount = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(adultCount, childCount, infantCount) }) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CounterRow(
    label: String,
    caption: String,
    count: Int,
    min: Int,
    max: Int = 10,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { if (count > min) onChange(count - 1) }, enabled = count > min) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer $label")
        }
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = { if (count < max) onChange(count + 1) }, enabled = count < max) {
            Icon(Icons.Filled.Add, contentDescription = "More $label")
        }
    }
}

@Composable
private fun DateDialog(initial: Long?, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        selectableDates = object : SelectableDates {
            // Block past dates. The picker works in UTC-midnight millis, so a
            // one-day grace window keeps "today" selectable in UTC+8 (PH time).
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= System.currentTimeMillis() - 86_400_000L
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onPicked)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

internal fun BusClass.displayLabel(): String = when (this) {
    BusClass.ORDINARY -> "Ordinary"
    BusClass.DELUXE -> "Deluxe"
    BusClass.LUXURY -> "Luxury"
}

internal fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(epochMillis))

internal fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(epochMillis))

// ─────────────────────────────────────────────────────────────────────────────
// PRESENTATION-ONLY SECTIONS (design page 2): static demo content, not wired
// to any backend. Deals, referral credits, and the Bookings/Wallet tabs
// require CRS features that don't exist yet — remove or wire up later.
// ─────────────────────────────────────────────────────────────────────────────

private data class DemoDeal(
    val badge: String,
    val tagline: String,
    val title: String,
    val priceLine: String,
    val imageRes: Int, // sample photography (Lorem Picsum) standing in for campaign shots
)

private val demoDeals = listOf(
    DemoDeal("20% OFF", "SUMMER GETAWAY", "Boracay via Batangas", "Starting at ₱850", R.drawable.deal_boracay),
    DemoDeal("15% OFF", "CITY ESCAPE", "Tagaytay Weekender", "Starting at ₱320", R.drawable.deal_tagaytay),
    DemoDeal("10% OFF", "NORTH EXPRESS", "Baguio Night Trip", "Starting at ₱690", R.drawable.deal_baguio),
)

@Composable
private fun HotDealsSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Hot Deals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "See All",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        demoDeals.forEach { deal -> DealSlide(deal) }
    }
}

@Composable
private fun DealSlide(deal: DemoDeal) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Image(
            painter = painterResource(deal.imageRes),
            contentDescription = deal.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.1f),
                        1f to Color.Black.copy(alpha = 0.65f),
                    ),
                ),
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) {
            Text(
                deal.badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(deal.tagline, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text(deal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                deal.priceLine,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = ChromeTiffanySoft,
            )
        }
    }
}

@Composable
private fun InviteFriendsCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Invite Friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Earn ₱50 credit for every friend who joins RideVibe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { /* presentation only — referral program not built */ }) {
                    Text("Share Now", fontWeight = FontWeight.Bold)
                }
            }
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

