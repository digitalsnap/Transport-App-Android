package com.ridevibe.feature.seatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.feature.seatmap.viewmodel.SeatMapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Choose Seats" screen per the Visily design (page 4): 2+aisle+2 cabin layout. */
@Composable
fun SeatMapScreen(
    onBack: () -> Unit,
    onProceedToCheckout: (seatIdsCsv: String) -> Unit,
    viewModel: SeatMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Choose Seats", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            FareBottomBar(
                totalFarePhp = uiState.totalFarePhp,
                selectedCount = uiState.selectedSeatIds.size,
                requiredCount = uiState.requiredSeatCount,
                holdSecondsRemaining = uiState.holdSecondsRemaining,
                inlineError = if (!uiState.isLoading && uiState.seats.isNotEmpty()) uiState.errorMessage else null,
                canProceed = uiState.selectionComplete,
                onConfirm = {
                    if (uiState.selectionComplete) {
                        onProceedToCheckout(uiState.selectedSeatIds.joinToString(","))
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.errorMessage != null && uiState.seats.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(padding).padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    uiState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = viewModel::load) { Text("Retry") }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                uiState.trip?.let { TripSummaryCard(it) }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Passenger Deck", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${uiState.selectedSeatIds.size} of ${uiState.requiredSeatCount} seats selected",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (uiState.selectionComplete) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                SeatLegend()
                Spacer(modifier = Modifier.height(16.dp))

                CabinGrid(seats = uiState.seats, onSeatClicked = viewModel::onSeatClicked)

                Spacer(modifier = Modifier.height(16.dp))
                HoldNoticeCard()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TripSummaryCard(trip: Trip) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "DEPARTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(trip.origin, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        durationLabel(trip.departureEpochMillis, trip.arrivalEpochMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "ARRIVAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(trip.destination, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "${trip.busClass.name.lowercase().replaceFirstChar { it.uppercase() }} • ${formatDeparture(trip.departureEpochMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeatLegend() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LegendItem("Available", MaterialTheme.colorScheme.surface, bordered = true)
            LegendItem("Selected", MaterialTheme.colorScheme.primary)
            LegendItem("Occupied", MaterialTheme.colorScheme.surfaceVariant)
            LegendItem("Locked", MaterialTheme.colorScheme.tertiaryContainer)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, bordered: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(4.dp))
                .then(
                    if (bordered) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    } else {
                        Modifier
                    },
                ),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun CabinGrid(seats: List<Seat>, onSeatClicked: (Seat) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        "DRIVER",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Text(
                    "Entrance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            seats.groupBy { it.row }.toSortedMap().forEach { (_, rowSeats) ->
                val byColumn = rowSeats.sortedBy { it.column }
                val left = byColumn.filter { it.column <= 2 }
                val right = byColumn.filter { it.column >= 3 }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        left.forEach { SeatCell(it, onSeatClicked) }
                    }
                    Spacer(modifier = Modifier.width(24.dp)) // aisle
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        right.forEach { SeatCell(it, onSeatClicked) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    "EMERGENCY EXIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SeatCell(seat: Seat, onClick: (Seat) -> Unit) {
    val isClickable = seat.status == SeatStatus.AVAILABLE || seat.status == SeatStatus.SELECTED
    val (background, contentColor) = when (seat.status) {
        SeatStatus.AVAILABLE -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
        SeatStatus.SELECTED -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        SeatStatus.OCCUPIED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        SeatStatus.LOCKED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .background(background, RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (seat.status == SeatStatus.AVAILABLE) {
                    MaterialTheme.colorScheme.outline
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = isClickable) { onClick(seat) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            seat.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HoldNoticeCard() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Selected seats are held for 10:00 minutes while you complete your checkout. Please confirm your selection quickly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun FareBottomBar(
    totalFarePhp: Double,
    selectedCount: Int,
    requiredCount: Int,
    holdSecondsRemaining: Int?,
    inlineError: String?,
    canProceed: Boolean,
    onConfirm: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            inlineError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (holdSecondsRemaining != null) {
                val minutes = holdSecondsRemaining / 60
                val seconds = holdSecondsRemaining % 60
                Text(
                    "Seats held for %d:%02d".format(minutes, seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (holdSecondsRemaining < 60) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "TOTAL FARE • $selectedCount/$requiredCount SEATS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "₱%,.0f".format(totalFarePhp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = onConfirm,
                    enabled = canProceed,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(
                        if (canProceed) "Confirm Seats" else "Pick $requiredCount seat${if (requiredCount == 1) "" else "s"}",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun durationLabel(departMillis: Long, arriveMillis: Long): String {
    val totalMinutes = ((arriveMillis - departMillis) / 60_000).coerceAtLeast(0)
    return "${totalMinutes / 60}h %02dm".format(totalMinutes % 60)
}

private fun formatDeparture(epochMillis: Long): String =
    SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(epochMillis))
