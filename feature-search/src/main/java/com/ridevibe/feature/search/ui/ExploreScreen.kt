package com.ridevibe.feature.search.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.format.formatPhp
import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.model.JourneyLeg
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.session.CartLeg
import com.ridevibe.feature.search.viewmodel.ExploreLeg
import com.ridevibe.feature.search.viewmodel.ExploreSection
import com.ridevibe.feature.search.viewmodel.ExploreViewModel
import com.ridevibe.feature.search.viewmodel.SortOption

/** Sentinel: the user collapsed every category on purpose. */
private const val ALL_COLLAPSED = "NONE"

/**
 * Cross-mode search results grouped by ride type. One category expands at a
 * time; the others dock as floating pills above the bottom navigation so long
 * lists stay scannable. A round-trip search shows an Outbound and a Return
 * group, each with its own date.
 *
 * [onTripSelectedForLeg], when supplied, receives which cart leg the tapped
 * trip starts ([CartLeg.OUTBOUND] for the first leg of a round trip) and takes
 * precedence over [onTripSelected], which stays for existing callers.
 */
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onTripSelected: (trip: Trip) -> Unit,
    onLegSelected: (from: String, to: String, dateMillis: Long) -> Unit,
    onTripSelectedForLeg: ((trip: Trip, leg: CartLeg) -> Unit)? = null,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarShown()
        }
    }
    // null = auto-expand the first non-empty category; ALL_COLLAPSED = show pills only.
    // Region searches return large result sets — land collapsed, pills only.
    var expandedKey by rememberSaveable(uiState.query, uiState.isRegionQuery) {
        mutableStateOf<String?>(if (uiState.isRegionQuery) ALL_COLLAPSED else null)
    }

    fun select(trip: Trip, leg: ExploreLeg) {
        val cartLeg = viewModel.onTripChosen(trip, leg, callerSupportsLegs = onTripSelectedForLeg != null)
        if (onTripSelectedForLeg != null) onTripSelectedForLeg(trip, cartLeg) else onTripSelected(trip)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.query.ifBlank { "Explore" }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            uiState.errorMessage != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    uiState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.isArgsError) {
                    Button(onClick = onBack) { Text("Back") }
                } else {
                    Button(onClick = viewModel::load) { Text("Retry") }
                }
            }

            uiState.isEmpty -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No trips found for \"${uiState.query}\" on ${formatDate(uiState.dateMillis)}. " +
                        "Try a terminal, port, or destination name.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Change date") }
                TextButton(onClick = viewModel::load) { Text("Retry") }
            }

            else -> {
                val allSections = uiState.outboundSections + uiState.returnSections
                val expandedSection = when (expandedKey) {
                    ALL_COLLAPSED -> null
                    else -> allSections.firstOrNull { it.key == expandedKey }
                        ?: allSections.firstOrNull { it.items.isNotEmpty() }
                        ?: allSections.firstOrNull()
                }

                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Room for the floating category pills docked at the bottom.
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item(key = "sort") {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SortOption.entries.forEach { option ->
                                    SortChip(option.label, uiState.sortOption == option) { viewModel.onSortOptionChanged(option) }
                                }
                            }
                        }

                        // Curated door-to-door routes first — the thing a tourist
                        // fresh off a plane actually needs.
                        if (uiState.journeys.isNotEmpty()) {
                            item(key = "journeys-header") {
                                GroupHeader("SUGGESTED ROUTES")
                            }
                            items(uiState.journeys, key = { "journey-${it.title}" }) { journey ->
                                JourneyCard(
                                    journey = journey,
                                    onLegSelected = { index, leg ->
                                        onLegSelected(leg.from, leg.to, viewModel.legDateMillis(journey, index))
                                    },
                                    onAddToItinerary = { viewModel.addToItinerary(journey) },
                                )
                            }
                        }

                        if (uiState.isRoundTrip) {
                            item(key = "outbound-header") {
                                GroupHeader("OUTBOUND · ${formatDate(uiState.dateMillis)}")
                            }
                        }
                        expandedGroup(
                            expanded = expandedSection?.takeIf { it.leg == ExploreLeg.OUTBOUND },
                            showPrompt = expandedSection == null,
                            onCollapse = { expandedKey = ALL_COLLAPSED },
                            onSelect = ::select,
                        )

                        if (uiState.isRoundTrip) {
                            item(key = "return-header") {
                                GroupHeader("RETURN · ${uiState.returnDateMillis?.let(::formatDate).orEmpty()}")
                            }
                            expandedGroup(
                                expanded = expandedSection?.takeIf { it.leg == ExploreLeg.RETURN },
                                showPrompt = false,
                                onCollapse = { expandedKey = ALL_COLLAPSED },
                                onSelect = ::select,
                            )
                        }
                    }

                    // Collapsed categories float as pills just above the bottom navigation.
                    val collapsed = allSections.filter { it != expandedSection }
                    if (collapsed.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                        ) {
                            collapsed.forEach { section ->
                                CategoryPill(
                                    section = section,
                                    showLeg = uiState.isRoundTrip,
                                    onClick = { expandedKey = section.key },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The one expanded category (or the "pick a category" prompt) inside the list. */
private fun LazyListScope.expandedGroup(
    expanded: ExploreSection?,
    showPrompt: Boolean,
    onCollapse: () -> Unit,
    onSelect: (Trip, ExploreLeg) -> Unit,
) {
    if (expanded == null) {
        if (showPrompt) {
            item(key = "prompt") {
                Text(
                    "Pick a category below to see its trips.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        }
        return
    }
    item(key = "header-${expanded.key}") {
        SectionHeader(section = expanded, expanded = true, onClick = onCollapse)
    }
    if (expanded.items.isEmpty()) {
        item(key = "empty-${expanded.key}") {
            Text(
                expanded.emptyMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        }
    }
    items(expanded.items, key = { "${expanded.key}-${it.trip.id}" }) { item ->
        Column {
            Text(
                "${item.trip.origin} → ${item.trip.destination}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            TripResultCard(item = item, onSelect = { onSelect(item.trip, expanded.leg) })
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

/**
 * A curated multi-leg itinerary card. Every leg is tappable and jumps straight
 * to live trips for that segment on the day the rider would reach it.
 */
@Composable
private fun JourneyCard(
    journey: Journey,
    onLegSelected: (index: Int, leg: JourneyLeg) -> Unit,
    onAddToItinerary: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TOURIST ROUTE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${journey.transferCount} transfer${if (journey.transferCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${journey.from} → ${journey.to}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                journey.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "~${journey.totalDurationMinutes / 60}h travel · from ${formatPhp(journey.totalFarePhp, showCentavos = false)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            journey.legs.forEachIndexed { index, leg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Find trips from ${leg.from} to ${leg.to}",
                            role = Role.Button,
                        ) { onLegSelected(index, leg) }
                        .padding(vertical = 6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                leg.kind.icon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            "${index + 1}. ${leg.from} → ${leg.to}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val kindLabel = when (leg.kind) {
                            RideKind.BUS -> "Bus"
                            RideKind.FERRY -> "Ferry"
                            RideKind.FASTCRAFT -> "Fastcraft"
                        }
                        Text(
                            "$kindLabel · ${leg.durationMinutes / 60}h %02dm · ~%s"
                                .format(leg.durationMinutes % 60, formatPhp(leg.indicativeFarePhp, showCentavos = false)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        leg.note?.let { note ->
                            Text(
                                note,
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddToItinerary,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Add to Itinerary", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun RideKind.icon(): ImageVector = when (this) {
    RideKind.BUS -> Icons.Filled.DirectionsBus
    RideKind.FERRY -> Icons.Filled.DirectionsBoat
    RideKind.FASTCRAFT -> Icons.Filled.Speed
}

@Composable
private fun SectionHeader(section: ExploreSection, expanded: Boolean, onClick: () -> Unit) {
    val count = section.items.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = if (expanded) "Collapse ${section.title}" else "Expand ${section.title}",
                role = Role.Button,
                onClick = onClick,
            )
            .padding(top = 6.dp, bottom = 2.dp),
    ) {
        Icon(
            section.kind.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            section.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text(
            "$count trip${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryPill(section: ExploreSection, showLeg: Boolean, onClick: () -> Unit) {
    val legPrefix = if (showLeg) (if (section.leg == ExploreLeg.OUTBOUND) "Out · " else "Return · ") else ""
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                section.kind.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "$legPrefix${section.title} · ${section.items.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
