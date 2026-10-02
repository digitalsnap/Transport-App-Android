package com.ridevibe.feature.search.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.displayLabel
import com.ridevibe.core.domain.model.spaceNoun
import com.ridevibe.feature.search.viewmodel.DepartureWindow
import com.ridevibe.feature.search.viewmodel.OutboundSummary
import com.ridevibe.feature.search.viewmodel.ResultsViewModel
import com.ridevibe.feature.search.viewmodel.SortOption
import com.ridevibe.feature.search.viewmodel.TripAvailability
import com.ridevibe.feature.search.viewmodel.TripListItem

/** "Available Trips" results screen per the Visily design (page 3). */
@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    onTripSelected: (trip: Trip) -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Available Trips", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = charcoalTopBarColors(),
            )
        },
    ) { padding ->
        uiState.argsError?.let { message ->
            // A malformed link (deep link or a stale back-stack entry) lands here instead of crashing.
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Back to search") }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    uiState.legLabel?.let { label ->
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "${uiState.origin} to ${uiState.destination}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${formatDate(uiState.departureDateMillis)} • ${uiState.passengerSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!uiState.isLoading && uiState.errorMessage == null) {
                    val count = uiState.visibleResults.size
                    val countLabel = "$count of ${uiState.results.size} trips"
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            countLabel,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                // Screen readers announce the new count as filters change.
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    contentDescription = "Showing $countLabel"
                                },
                        )
                    }
                }
            }

            uiState.outbound?.let { outbound ->
                OutboundLegCard(
                    outbound = outbound,
                    runningTotalLabel = uiState.runningTotalLabel,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            FiltersRow(
                sort = uiState.sortOption,
                onSort = viewModel::onSortOptionChanged,
                rideKind = uiState.rideKindFilter,
                onRideKind = viewModel::onRideKindFilterChanged,
                window = uiState.departureWindow,
                onWindow = viewModel::onDepartureWindowChanged,
                operators = uiState.operators,
                operator = uiState.operatorFilter,
                onOperator = viewModel::onOperatorFilterChanged,
            )

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    uiState.errorMessage != null -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = viewModel::search) { Text("Retry") }
                    }

                    uiState.visibleResults.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (uiState.results.isEmpty()) {
                                "No trips found for that route and date."
                            } else {
                                "No trips match these filters."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.hasActiveFilters) {
                            Button(onClick = viewModel::onClearFilters) { Text("Clear filters") }
                        } else {
                            Button(onClick = onBack) { Text("Try another date") }
                        }
                        TextButton(onClick = viewModel::search) { Text("Retry") }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(uiState.visibleResults, key = { it.trip.id }) { item ->
                            TripResultCard(item = item, onSelect = { onTripSelected(item.trip) })
                        }
                    }
                }
            }
        }
    }
}

/** Compact reminder of the leg already in the cart while the rider picks the return. */
@Composable
private fun OutboundLegCard(
    outbound: OutboundSummary,
    runningTotalLabel: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "OUTBOUND IN CART",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "${outbound.origin} → ${outbound.destination} · ${formatDate(outbound.dateMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                outbound.seatsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            runningTotalLabel?.let { total ->
                Text(
                    total,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FiltersRow(
    sort: SortOption,
    onSort: (SortOption) -> Unit,
    rideKind: RideKind?,
    onRideKind: (RideKind?) -> Unit,
    window: DepartureWindow?,
    onWindow: (DepartureWindow?) -> Unit,
    operators: List<String>,
    operator: String?,
    onOperator: (String?) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortOption.entries.forEach { option ->
                ResultChip(option.label, sort == option) { onSort(option) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResultChip("All", rideKind == null) { onRideKind(null) }
            ResultChip("Buses", rideKind == RideKind.BUS) { onRideKind(RideKind.BUS) }
            ResultChip("Ferries", rideKind == RideKind.FERRY) { onRideKind(RideKind.FERRY) }
            ResultChip("Fastcrafts", rideKind == RideKind.FASTCRAFT) { onRideKind(RideKind.FASTCRAFT) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OperatorDropdown(operators = operators, selected = operator, onSelect = onOperator)
            DepartureWindow.entries.forEach { bucket ->
                // Tapping the active window again clears it.
                ResultChip(bucket.label, window == bucket) { onWindow(if (window == bucket) null else bucket) }
            }
        }
    }
}

@Composable
private fun OperatorDropdown(operators: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            enabled = operators.isNotEmpty(),
            label = { Text(selected ?: "Operator") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            shape = RoundedCornerShape(50),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Any operator") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            operators.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, fontWeight = if (name == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** One trip; shared with Explore. Everything shown is pre-computed in [TripListItem]. */
@Composable
internal fun TripResultCard(item: TripListItem, onSelect: () -> Unit) {
    val trip = item.trip
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(trip.operatorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        trip.busClass.displayLabel(trip.rideKind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trip.operatorRating?.let { rating ->
                    val ratingLabel = "%.1f".format(rating)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.semantics { contentDescription = "Operator rating $ratingLabel out of 5" },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                " $ratingLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        formatTime(trip.departureEpochMillis),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        trip.origin.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        durationLabel(trip.departureEpochMillis, trip.arrivalEpochMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatTime(trip.arrivalEpochMillis),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        trip.destination.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.availabilityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.availability) {
                        // Low-seat warnings are one of Gold's three sanctioned uses.
                        TripAvailability.LOW -> MaterialTheme.colorScheme.tertiary
                        TripAvailability.SHORT, TripAvailability.SOLD_OUT -> MaterialTheme.colorScheme.error
                        TripAvailability.OPEN -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        item.fareLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "per ${trip.rideKind.spaceNoun()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                item.totalLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            item.combinedTotalLabel?.let { combined ->
                Text(combined, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Student, senior and PWD discounts are applied at checkout.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (item.isBookable) {
                Button(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    // Sea services sell passage, not chosen seats — ferries board by
                    // berth/space, fastcraft seats are assigned at the port counter.
                    Text("${item.ctaLabel}  →", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        if (item.availability == TripAvailability.SOLD_OUT) "Sold out" else "Not enough ${trip.rideKind.spaceNoun(plural = true)}",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
