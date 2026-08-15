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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.model.JourneyLeg
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.ItineraryRepository
import com.ridevibe.core.domain.usecase.FindJourneysUseCase
import com.ridevibe.core.domain.usecase.SearchRelatedTripsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    /** Curated multi-leg tourist routes matching the query (e.g. Manila → Siargao). */
    val journeys: List<Journey> = emptyList(),
    val dateMillis: Long = 0L,
    /** One-shot confirmation after saving a route to the itinerary. */
    val savedMessage: String? = null,
    val errorMessage: String? = null,
) {
    val buses: List<Trip> get() = trips.filter { it.rideKind == RideKind.BUS }
    val ferries: List<Trip> get() = trips.filter { it.rideKind == RideKind.FERRY }
    val fastcrafts: List<Trip> get() = trips.filter { it.rideKind == RideKind.FASTCRAFT }
}

// ItineraryRepository is injected directly (no use-case layer): a plain save
// with no domain logic, consistent with the other CRUD-only screens.
@HiltViewModel
class ExploreViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRelatedTripsUseCase: SearchRelatedTripsUseCase,
    private val findJourneysUseCase: FindJourneysUseCase,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val query: String = checkNotNull(savedStateHandle["query"])
    private val departureDateMillis: Long =
        savedStateHandle.get<String>("date")?.toLongOrNull()?.takeIf { it > 0 }
            ?: System.currentTimeMillis()
    private val returnDateMillis: Long? =
        savedStateHandle.get<String>("returnDate")?.toLongOrNull()?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(ExploreUiState(query = query, dateMillis = departureDateMillis))
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val journeys = findJourneysUseCase(query).getOrDefault(emptyList())
            searchRelatedTripsUseCase(query, departureDateMillis, returnDateMillis)
                .onSuccess { trips ->
                    _uiState.update { it.copy(isLoading = false, trips = trips, journeys = journeys) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            journeys = journeys,
                            errorMessage = throwable.message ?: "Search failed. Check your connection and try again.",
                        )
                    }
                }
        }
    }

    fun addToItinerary(journey: Journey) {
        viewModelScope.launch {
            itineraryRepository.addItinerary(journey, departureDateMillis)
            _uiState.update { it.copy(savedMessage = "Saved to your Itinerary tab") }
        }
    }

    fun onSavedMessageShown() = _uiState.update { it.copy(savedMessage = null) }
}

private data class RideSection(val kind: RideKind, val title: String, val icon: ImageVector, val trips: List<Trip>)

/** Sentinel: the user collapsed every category on purpose. */
private const val ALL_COLLAPSED = "NONE"

/**
 * Cross-mode search results grouped by ride type. One category expands at a
 * time; the others dock as floating pills above the bottom navigation so long
 * lists stay scannable.
 */
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onTripSelected: (trip: Trip) -> Unit,
    onLegSelected: (from: String, to: String, dateMillis: Long) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onSavedMessageShown()
        }
    }
    // Region searches return large result sets — land collapsed, pills only.
    val isRegionQuery = listOf("luzon", "visayas", "mindanao")
        .any { uiState.query.contains(it, ignoreCase = true) }
    // null = auto-expand the first non-empty category; ALL_COLLAPSED = show pills only.
    var expandedKind by rememberSaveable(uiState.query) {
        mutableStateOf<String?>(if (isRegionQuery) ALL_COLLAPSED else null)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.query, fontWeight = FontWeight.Bold) },
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
                Button(onClick = viewModel::load) { Text("Retry") }
            }

            uiState.trips.isEmpty() && uiState.journeys.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No trips found for \"${uiState.query}\". Try a terminal, port, or destination name.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            else -> {
                val sections = listOf(
                    RideSection(RideKind.BUS, "Provincial Buses", Icons.Filled.DirectionsBus, uiState.buses),
                    RideSection(RideKind.FERRY, "Ferries", Icons.Filled.DirectionsBoat, uiState.ferries),
                    RideSection(RideKind.FASTCRAFT, "Fastcrafts", Icons.Filled.Speed, uiState.fastcrafts),
                ).filter { it.trips.isNotEmpty() }

                val expandedSection = when (expandedKind) {
                    ALL_COLLAPSED -> null
                    else -> sections.firstOrNull { it.kind.name == expandedKind } ?: sections.firstOrNull()
                }

                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Room for the floating category pills docked at the bottom.
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Curated door-to-door routes first — the thing a tourist
                        // fresh off a plane actually needs.
                        if (uiState.journeys.isNotEmpty()) {
                            item(key = "journeys-header") {
                                Text(
                                    "SUGGESTED ROUTES",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            items(uiState.journeys, key = { "journey-${it.title}" }) { journey ->
                                JourneyCard(
                                    journey = journey,
                                    onLegSelected = { leg -> onLegSelected(leg.from, leg.to, uiState.dateMillis) },
                                    onAddToItinerary = { viewModel.addToItinerary(journey) },
                                )
                            }
                        }

                        if (expandedSection == null) {
                            item {
                                Text(
                                    "Pick a category below to see its trips.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                )
                            }
                        } else {
                            item(key = "header-${expandedSection.kind}") {
                                SectionHeader(
                                    section = expandedSection,
                                    expanded = true,
                                    onClick = { expandedKind = ALL_COLLAPSED },
                                )
                            }
                            items(expandedSection.trips, key = { it.id }) { trip ->
                                Column {
                                    Text(
                                        "${trip.origin} → ${trip.destination}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                    TripResultCard(trip = trip, onViewSeats = { onTripSelected(trip) })
                                }
                            }
                        }
                    }

                    // Collapsed categories float as pills just above the bottom navigation.
                    val collapsed = sections.filter { it != expandedSection }
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
                                CategoryPill(section = section, onClick = { expandedKind = section.kind.name })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A curated multi-leg itinerary card. Every leg is tappable and jumps straight
 * to live trips for that segment on the traveller's chosen date.
 */
@Composable
private fun JourneyCard(
    journey: Journey,
    onLegSelected: (JourneyLeg) -> Unit,
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
                "~${journey.totalDurationMinutes / 60}h travel · from ₱%,.0f".format(journey.totalFarePhp),
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
                        .clickable { onLegSelected(leg) }
                        .padding(vertical = 6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                when (leg.kind) {
                                    RideKind.BUS -> Icons.Filled.DirectionsBus
                                    RideKind.FERRY -> Icons.Filled.DirectionsBoat
                                    RideKind.FASTCRAFT -> Icons.Filled.Speed
                                },
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
                            "$kindLabel · ${leg.durationMinutes / 60}h %02dm · ~₱%,.0f"
                                .format(leg.durationMinutes % 60, leg.indicativeFarePhp),
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
                        contentDescription = "Find trips for this leg",
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

@Composable
private fun SectionHeader(section: RideSection, expanded: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 2.dp),
    ) {
        Icon(
            section.icon,
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
            "${section.trips.size} trip${if (section.trips.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse ${section.title}" else "Expand ${section.title}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryPill(section: RideSection, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "${section.title} · ${section.trips.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
