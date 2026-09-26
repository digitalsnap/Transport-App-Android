package com.ridevibe.feature.search.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.LocationKind
import com.ridevibe.core.domain.model.PartyRules
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.TripType
import com.ridevibe.core.domain.model.displayLabel
import com.ridevibe.feature.search.R
import com.ridevibe.feature.search.viewmodel.ExploreSearchState
import com.ridevibe.feature.search.viewmodel.FeaturedRoute
import com.ridevibe.feature.search.viewmodel.SearchFormState
import com.ridevibe.feature.search.viewmodel.SearchRequest
import com.ridevibe.feature.search.viewmodel.SearchViewModel

/**
 * Home / "Where to next?" screen per the Visily design (page 2).
 *
 * [onSearchRequest], when supplied, receives the full [SearchRequest]
 * (including the ride kind from the transport tile) and takes precedence over
 * the older positional [onSearch], which stays so existing callers keep
 * compiling.
 */
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
    onSearchRequest: ((SearchRequest) -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    // Dialog and sheet visibility survives rotation and process death like the form itself.
    var showDepartPicker by rememberSaveable { mutableStateOf(false) }
    var showReturnPicker by rememberSaveable { mutableStateOf(false) }
    var showPassengerDialog by rememberSaveable { mutableStateOf(false) }
    var locationPickerTarget by rememberSaveable { mutableStateOf<LocationTarget?>(null) }
    var locationQuery by rememberSaveable { mutableStateOf("") }
    // Header search: picking a place routes to the cross-mode results page.
    var showExploreSearch by rememberSaveable { mutableStateOf(false) }
    var showExploreDepartPicker by rememberSaveable { mutableStateOf(false) }
    var showExploreReturnPicker by rememberSaveable { mutableStateOf(false) }
    // "Choose your Ride" tiles open the trip form as a bottom sheet.
    var showTripSheet by rememberSaveable { mutableStateOf(false) }

    if (showDepartPicker) {
        DateDialog(
            initialDayMillis = form.departureDateMillis,
            minDayMillis = form.minDateMillis,
            maxDayMillis = form.maxDateMillis,
            onPicked = viewModel::onDateSelected,
            onDismiss = { showDepartPicker = false },
        )
    }
    if (showReturnPicker) {
        DateDialog(
            initialDayMillis = form.returnDateMillis ?: form.departureDateMillis,
            // A return before departure is impossible, so the picker never offers one.
            minDayMillis = form.departureDateMillis ?: form.minDateMillis,
            maxDayMillis = form.maxDateMillis,
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
        val suggestions = remember(locationQuery, form.locations, form.rideKind) {
            viewModel.locationSuggestions(locationQuery)
        }
        LocationPickerDialog(
            title = if (target == LocationTarget.FROM) "Select origin" else "Select destination",
            query = locationQuery,
            onQueryChange = { locationQuery = it },
            suggestions = suggestions,
            isLoading = form.isLoadingOptions,
            error = form.optionsError,
            onRetry = viewModel::loadOptions,
            onSelected = { name ->
                when (target) {
                    LocationTarget.FROM -> viewModel.onOriginSelected(name)
                    LocationTarget.TO -> viewModel.onDestinationSelected(name)
                }
                locationQuery = ""
                locationPickerTarget = null
            },
            onDismiss = {
                locationQuery = ""
                locationPickerTarget = null
            },
        )
    }

    if (showExploreSearch) {
        if (showExploreDepartPicker) {
            DateDialog(
                initialDayMillis = form.explore.departMillis,
                minDayMillis = form.minDateMillis,
                maxDayMillis = form.maxDateMillis,
                onPicked = viewModel::onExploreDepartSelected,
                onDismiss = { showExploreDepartPicker = false },
            )
        }
        if (showExploreReturnPicker) {
            DateDialog(
                initialDayMillis = form.explore.returnMillis ?: form.explore.departMillis,
                minDayMillis = form.explore.departMillis ?: form.minDateMillis,
                maxDayMillis = form.maxDateMillis,
                onPicked = viewModel::onExploreReturnSelected,
                onDismiss = { showExploreReturnPicker = false },
            )
        }
        ExploreSearchDialog(
            state = form.explore,
            isLoading = form.isLoadingOptions,
            error = form.optionsError,
            onRetry = viewModel::loadOptions,
            onQueryChange = viewModel::onExploreQueryChanged,
            onPlaceSelected = viewModel::onExplorePlaceSelected,
            onRoundTripChanged = viewModel::onExploreRoundTripChanged,
            onPickDepart = { showExploreDepartPicker = true },
            onPickReturn = { showExploreReturnPicker = true },
            onDisplayResults = { query, dateMillis, returnDateMillis ->
                showExploreSearch = false
                viewModel.onExploreReset()
                onExplore(query, dateMillis, returnDateMillis)
            },
            onDismiss = {
                showExploreSearch = false
                viewModel.onExploreReset()
            },
        )
    }

    form.rideKind?.let { rideKind ->
        if (showTripSheet) {
            TripSearchSheet(
                rideKind = rideKind,
                form = form,
                onDismiss = { showTripSheet = false },
                onTripTypeChanged = viewModel::onTripTypeChanged,
                onPickFrom = { locationPickerTarget = LocationTarget.FROM },
                onPickTo = { locationPickerTarget = LocationTarget.TO },
                onSwap = viewModel::onSwapLocations,
                onPickDepartDate = { showDepartPicker = true },
                onPickReturnDate = { showReturnPicker = true },
                onPickPassengers = { showPassengerDialog = true },
                onBookingForSelfChanged = viewModel::onBookingForSelfChanged,
                onBusClassFilterChanged = viewModel::onBusClassFilterChanged,
                onSearch = {
                    val request = viewModel.submitSearch() ?: return@TripSearchSheet
                    showTripSheet = false
                    if (onSearchRequest != null) {
                        onSearchRequest(request)
                    } else {
                        onSearch(
                            request.origin,
                            request.destination,
                            request.dateMillis,
                            request.busClass,
                            request.adults,
                            request.children,
                            request.infants,
                            request.bookingForSelf,
                        )
                    }
                },
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Search-first header on Charcoal chrome: tapping the bar opens the
            // cross-mode search; profile lives top-right.
            Surface(color = ChromeCharcoal) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { showExploreSearch = true },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Search trips by place or region" },
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
                            // Always a prompt, never the last destination: this bar is
                            // the cross-mode search, not a readout of the trip form.
                            Text(
                                "Search trips",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                selected = form.rideKind,
                onSelect = { kind ->
                    viewModel.onRideKindSelected(kind)
                    showTripSheet = true
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
            FeaturedRoutesSection(
                routes = form.featuredRoutes,
                onSeeAll = { showExploreSearch = true },
                onRouteClick = { route ->
                    viewModel.onFeaturedRouteSelected(route)
                    showTripSheet = true
                },
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private enum class LocationTarget { FROM, TO }

/** How each transport tile presents its [RideKind]. */
private data class RideTile(val label: String, val searchLabel: String, val icon: ImageVector)

private fun RideKind.tile(): RideTile = when (this) {
    RideKind.BUS -> RideTile("Provincial Buses", "Search buses", Icons.Filled.DirectionsBus)
    RideKind.FERRY -> RideTile("Ferries", "Search ferries", Icons.Filled.DirectionsBoat)
    RideKind.FASTCRAFT -> RideTile("Fastcrafts", "Search fastcrafts", Icons.Filled.Speed)
}

@Composable
private fun ChooseRideCard(selected: RideKind?, onSelect: (RideKind) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Transport Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RideKind.entries.forEach { kind ->
                    ServiceTile(
                        kind = kind,
                        selected = kind == selected,
                        onClick = { onSelect(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceTile(
    kind: RideKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tile = kind.tile()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.semantics { contentDescription = tile.searchLabel },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
        ) {
            Icon(
                tile.icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                tile.label,
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
    rideKind: RideKind,
    form: SearchFormState,
    onDismiss: () -> Unit,
    onTripTypeChanged: (TripType) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    onPickDepartDate: () -> Unit,
    onPickReturnDate: () -> Unit,
    onPickPassengers: () -> Unit,
    onBookingForSelfChanged: (Boolean) -> Unit,
    onBusClassFilterChanged: (BusClass?) -> Unit,
    onSearch: () -> Unit,
) {
    val tile = rideKind.tile()
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
                    tile.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                SelectablePill("One-way", form.tripType == TripType.ONE_WAY) { onTripTypeChanged(TripType.ONE_WAY) }
                Spacer(modifier = Modifier.width(6.dp))
                SelectablePill("Round-trip", form.tripType == TripType.ROUND_TRIP) { onTripTypeChanged(TripType.ROUND_TRIP) }
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

            Surface(
                onClick = onPickPassengers,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Passengers: ${form.passengersLabel}" },
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
                }
            }
            form.partyError?.let { error ->
                Text(
                    error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }

            // Bus classes only: ferries and fastcrafts sell accommodation, not a bus class.
            if (form.showsBusClassFilter && form.busClasses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "CLASS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectablePill("Any", form.busClassFilter == null) { onBusClassFilterChanged(null) }
                    form.busClasses.forEach { busClass ->
                        SelectablePill(busClass.displayLabel(RideKind.BUS), form.busClassFilter == busClass) {
                            onBusClassFilterChanged(busClass)
                        }
                    }
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
                SelectablePill("I'm traveling", form.bookingForSelf) { onBookingForSelfChanged(true) }
                SelectablePill("Booking for someone else", !form.bookingForSelf) { onBookingForSelfChanged(false) }
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
                Text(tile.searchLabel, fontWeight = FontWeight.Bold)
            }
            // Say why the button is off instead of leaving the rider to guess.
            form.searchBlockedReason?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectablePill(label: String, selected: Boolean, onClick: () -> Unit) {
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Choose $label location", role = Role.Button, onClick = onClick),
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
        modifier = modifier.semantics { contentDescription = "$caption: $value" },
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
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<TerminalLocation>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search terminals & ports") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    error != null -> OptionsErrorBlock(error, onRetry)

                    isLoading && suggestions.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        if (query.isBlank()) {
                            item(key = "popular-header") {
                                Text(
                                    "POPULAR",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                        }
                        if (suggestions.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    "No matching locations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                        }
                        items(suggestions, key = { it.name }) { location ->
                            LocationRow(location, highlight = location.isCentralTerminal) { onSelected(location.name) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OptionsErrorBlock(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Header search: type → pick a place or whole region → the trip-type toggle,
 * date selector, and a "Display Results" pill append below the choice. State
 * lives in [SearchViewModel] so it survives rotation mid-search.
 */
@Composable
private fun ExploreSearchDialog(
    state: ExploreSearchState,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPlaceSelected: (String) -> Unit,
    onRoundTripChanged: (Boolean) -> Unit,
    onPickDepart: () -> Unit,
    onPickReturn: () -> Unit,
    onDisplayResults: (query: String, dateMillis: Long, returnDateMillis: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search trips", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search terminals & destinations") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                val selectedPlace = state.selectedPlace
                if (selectedPlace == null) {
                    when {
                        error != null -> OptionsErrorBlock(error, onRetry)

                        isLoading -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            when {
                                state.query.isBlank() -> item {
                                    Text(
                                        "Start typing to find terminals, ports, destinations, or a whole region.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp),
                                    )
                                }

                                state.suggestions.isEmpty() && state.regionSuggestions.isEmpty() -> item {
                                    Text(
                                        "No matching locations",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp),
                                    )
                                }

                                else -> {
                                    items(state.regionSuggestions, key = { "region-$it" }) { region ->
                                        RegionRow(region) { onPlaceSelected(region) }
                                    }
                                    items(state.suggestions, key = { it.name }) { location ->
                                        LocationRow(location, highlight = location.isCentralTerminal) {
                                            onPlaceSelected(location.name)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Stage 2: trip type + dates + Display Results.
                    Row {
                        SelectablePill("One-way", !state.roundTrip) { onRoundTripChanged(false) }
                        Spacer(modifier = Modifier.width(6.dp))
                        SelectablePill("Round-trip", state.roundTrip) { onRoundTripChanged(true) }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoTile(
                            icon = { Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) },
                            caption = "DEPART",
                            value = state.departMillis?.let(::formatDate) ?: "Pick a date",
                            modifier = Modifier.weight(1f),
                            onClick = onPickDepart,
                        )
                        InfoTile(
                            icon = {
                                Icon(
                                    Icons.Filled.CalendarMonth,
                                    null,
                                    tint = if (state.roundTrip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            caption = "RETURN",
                            value = if (state.roundTrip) state.returnMillis?.let(::formatDate) ?: "Pick a date" else "—",
                            modifier = Modifier.weight(1f),
                            onClick = if (state.roundTrip) onPickReturn else null,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val depart = state.departMillis ?: return@Button
                            onDisplayResults(selectedPlace, depart, state.returnMillis.takeIf { state.roundTrip })
                        },
                        enabled = state.canDisplay,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClickLabel = "Search all of $region", role = Role.Button, onClick = onClick),
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
    val caption = when (location.kind) {
        LocationKind.BUS_TERMINAL -> if (highlight) "Central terminal" else "Bus terminal"
        LocationKind.SEAPORT -> "Seaport"
        LocationKind.CITY -> ""
    }.let { kind -> listOf(kind, location.region).filter { it.isNotBlank() }.joinToString(" · ") }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClickLabel = "Select ${location.name}", role = Role.Button, onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    highlight -> Icons.Filled.Hub
                    location.kind == LocationKind.SEAPORT -> Icons.Filled.DirectionsBoat
                    else -> Icons.Filled.Place
                },
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    location.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                )
                if (caption.isNotBlank()) {
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
    var adultCount by rememberSaveable { mutableStateOf(adults) }
    var childCount by rememberSaveable { mutableStateOf(children) }
    var infantCount by rememberSaveable { mutableStateOf(infants) }
    val error = PartyRules.validate(adultCount, childCount, infantCount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passengers", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CounterRow(
                    label = "Adults",
                    caption = "12 years and above",
                    count = adultCount,
                    canDecrement = adultCount > 1,
                    canIncrement = PartyRules.validate(adultCount + 1, childCount, infantCount) == null,
                    onChange = { adultCount = it },
                )
                CounterRow(
                    label = "Children",
                    caption = "2–11 years, seat required",
                    count = childCount,
                    canDecrement = childCount > 0,
                    canIncrement = PartyRules.validate(adultCount, childCount + 1, infantCount) == null,
                    onChange = { childCount = it },
                )
                CounterRow(
                    label = "Infants",
                    caption = "Under 2, lap-held — free of charge",
                    count = infantCount,
                    canDecrement = infantCount > 0,
                    canIncrement = PartyRules.validate(adultCount, childCount, infantCount + 1) == null,
                    onChange = { infantCount = it },
                )
                Text(
                    error?.message ?: "Up to ${PartyRules.MAX_SEATS_PER_BOOKING} seats per booking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(adultCount, childCount, infantCount) }, enabled = error == null) {
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
    canDecrement: Boolean,
    canIncrement: Boolean,
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
        IconButton(onClick = { onChange(count - 1) }, enabled = canDecrement) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer $label")
        }
        Text(
            "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { contentDescription = "$count $label" },
        )
        IconButton(onClick = { onChange(count + 1) }, enabled = canIncrement) {
            Icon(Icons.Filled.Add, contentDescription = "More $label")
        }
    }
}

/**
 * Day picker over PH start-of-day millis. The Material picker itself works in
 * UTC midnights, so every value crosses [phDayToPickerMillis] / [pickerMillisToPhDay].
 */
@Composable
private fun DateDialog(
    initialDayMillis: Long?,
    minDayMillis: Long,
    maxDayMillis: Long,
    onPicked: (phDayMillis: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDayMillis?.let(::phDayToPickerMillis),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                pickerMillisToPhDay(utcTimeMillis) in minDayMillis..maxDayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(pickerMillisToPhDay(it)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FEATURED ROUTES (design page 2): curated demo corridors with sample photos.
// The badge and price line are campaign copy, not live fares; tapping a card
// prefills the trip form so the rider still runs a real search.
// ─────────────────────────────────────────────────────────────────────────────

/** Sample photography (Lorem Picsum) standing in for campaign shots, keyed by route id. */
private fun FeaturedRoute.imageRes(): Int = when (id) {
    "boracay" -> R.drawable.deal_boracay
    "tagaytay" -> R.drawable.deal_tagaytay
    else -> R.drawable.deal_baguio
}

@Composable
private fun FeaturedRoutesSection(
    routes: List<FeaturedRoute>,
    onSeeAll: () -> Unit,
    onRouteClick: (FeaturedRoute) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Featured routes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onSeeAll) {
            Text("See all", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        routes.forEach { route -> FeaturedRouteCard(route, onClick = { onRouteClick(route) }) }
    }
}

@Composable
private fun FeaturedRouteCard(route: FeaturedRoute, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                onClickLabel = "Search ${route.origin} to ${route.destination}",
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "${route.badge}. ${route.title}. ${route.priceLine}" },
    ) {
        Image(
            painter = painterResource(route.imageRes()),
            contentDescription = null,
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
                route.badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            // White on a photo gradient is the one place the theme's surface
            // colours can't be used: the photo is the surface.
            Text(route.tagline, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text(route.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                route.priceLine,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = ChromeTiffanySoft,
            )
        }
    }
}
