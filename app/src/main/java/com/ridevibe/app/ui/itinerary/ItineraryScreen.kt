package com.ridevibe.app.ui.itinerary

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.app.ui.theme.charcoalTopBarColors
import com.ridevibe.core.domain.model.Itinerary
import com.ridevibe.core.domain.model.JourneyLeg
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.repository.ItineraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ItineraryUiState(
    val isLoading: Boolean = true,
    val itineraries: List<Itinerary> = emptyList(),
)

// ItineraryRepository is injected directly (no use-case layer): CRUD over the
// traveller's saved plans. Screen + VM share a file until the feature grows.
@HiltViewModel
class ItineraryViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItineraryUiState())
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _uiState.update { it.copy(isLoading = false, itineraries = itineraryRepository.getItineraries()) }
        }
    }

    fun toggleLeg(itinerary: Itinerary, legIndex: Int) {
        viewModelScope.launch {
            itineraryRepository.setLegDone(itinerary.id, legIndex, legIndex !in itinerary.doneLegIndices)
            _uiState.update { it.copy(itineraries = itineraryRepository.getItineraries()) }
        }
    }

    fun remove(itineraryId: String) {
        viewModelScope.launch {
            itineraryRepository.removeItinerary(itineraryId)
            _uiState.update { it.copy(itineraries = itineraryRepository.getItineraries()) }
        }
    }
}

/**
 * The traveller's saved multi-leg plans (replaces the old QR scan tab). Tick
 * legs off as the trip progresses; every leg jumps to live trips to book.
 */
@Composable
fun ItineraryScreen(
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onFindLeg: (from: String, to: String, dateMillis: Long) -> Unit,
    onExploreDestination: (query: String) -> Unit,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Itinerary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Filled.Person, contentDescription = "Profile")
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

            uiState.itineraries.isEmpty() -> EmptyItinerary(
                modifier = Modifier.fillMaxSize().padding(padding),
                onExploreDestination = onExploreDestination,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(uiState.itineraries, key = { it.id }) { itinerary ->
                    ItineraryCard(
                        itinerary = itinerary,
                        onToggleLeg = { index -> viewModel.toggleLeg(itinerary, index) },
                        onFindLeg = { leg -> onFindLeg(leg.from, leg.to, itinerary.startDateMillis) },
                        onRemove = { viewModel.remove(itinerary.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyItinerary(modifier: Modifier, onExploreDestination: (String) -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Map,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Plan your island-hopping trip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Search a destination, pick a suggested route, and tap \"Add to Itinerary\" " +
                "to build your leg-by-leg plan — buses, ferries, and fastcrafts included.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Siargao", "Boracay", "Visayas").forEach { destination ->
                AssistChip(
                    onClick = { onExploreDestination(destination) },
                    label = { Text(destination, fontWeight = FontWeight.SemiBold) },
                )
            }
        }
    }
}

@Composable
private fun ItineraryCard(
    itinerary: Itinerary,
    onToggleLeg: (Int) -> Unit,
    onFindLeg: (JourneyLeg) -> Unit,
    onRemove: () -> Unit,
) {
    val journey = itinerary.journey
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${journey.from} → ${journey.to}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${journey.title} · starts ${formatDate(itinerary.startDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "~${journey.totalDurationMinutes / 60}h travel · est. ₱%,.0f".format(journey.totalFarePhp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove this plan",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (journey.legs.isEmpty()) 0f else itinerary.completedCount / journey.legs.size.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (itinerary.isComplete) {
                    "Trip complete — welcome to ${journey.to}!"
                } else {
                    "${itinerary.completedCount} of ${journey.legs.size} legs done"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (itinerary.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            journey.legs.forEachIndexed { index, leg ->
                val done = index in itinerary.doneLegIndices
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    // Tick off a leg once it's travelled.
                    IconButton(onClick = { onToggleLeg(index) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (done) "Mark leg not done" else "Mark leg done",
                            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .clickable { onFindLeg(leg) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (leg.kind) {
                                    RideKind.BUS -> Icons.Filled.DirectionsBus
                                    RideKind.FERRY -> Icons.Filled.DirectionsBoat
                                    RideKind.FASTCRAFT -> Icons.Filled.Speed
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "${leg.from} → ${leg.to}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Text(
                            "${leg.durationMinutes / 60}h %02dm · ~₱%,.0f".format(leg.durationMinutes % 60, leg.indicativeFarePhp),
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
                        modifier = Modifier.clickable { onFindLeg(leg) },
                    )
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(epochMillis))
