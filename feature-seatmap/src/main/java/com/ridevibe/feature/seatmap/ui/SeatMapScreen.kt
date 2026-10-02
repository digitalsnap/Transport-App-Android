package com.ridevibe.feature.seatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.format.formatPhp
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.displayLabel
import com.ridevibe.core.domain.model.spaceNoun
import com.ridevibe.feature.seatmap.viewmodel.SeatFeedStatus
import com.ridevibe.feature.seatmap.viewmodel.SeatMapSelection
import com.ridevibe.feature.seatmap.viewmodel.SeatMapUiState
import com.ridevibe.feature.seatmap.viewmodel.SeatMapViewModel

private val SeatShape = RoundedCornerShape(14.dp)
private val SeatSize = 52.dp
private val AisleWidth = 24.dp
private val SeatGap = 10.dp

/** Countdown thresholds at which the hold clock changes colour and announces itself. */
private const val HOLD_WARN_SECONDS = 120
private const val HOLD_URGENT_SECONDS = 60

/**
 * "Choose Seats" screen per the Visily design (page 4): a 2+aisle+2 cabin by
 * default, widened or narrowed from the seat data.
 *
 * [onSeatsConfirmed] is the current contract: it carries the held seats, the
 * server hold expiry and the fare so the nav graph can fill `BookingCart`.
 * [onProceedToCheckout] is the older CSV-only callback and is only invoked
 * when [onSeatsConfirmed] is not supplied, so an un-migrated nav graph keeps
 * working.
 */
@Composable
fun SeatMapScreen(
    onBack: () -> Unit,
    onProceedToCheckout: (seatIdsCsv: String) -> Unit,
    onSeatsConfirmed: ((selection: SeatMapSelection) -> Unit)? = null,
    viewModel: SeatMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // The socket only lives while the screen is visible; the VM resyncs on each re-open.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onScreenStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }

    val proceed = {
        viewModel.proceed()?.let { selection ->
            onSeatsConfirmed?.invoke(selection) ?: onProceedToCheckout(selection.seatIdsCsv)
        }
        Unit
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (uiState.sellsPassage) "Passage" else "Choose Seats",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading && uiState.loadError == null && uiState.trip != null) {
                FareBottomBar(uiState = uiState, onConfirm = proceed)
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.loadError != null -> FullScreenMessage(
                modifier = Modifier.padding(padding),
                title = "Couldn't load the seat map",
                body = uiState.loadError.orEmpty(),
                actionLabel = "Retry",
                onAction = viewModel::load,
                isError = true,
            )

            // Sea services sell passage; the nav graph normally skips this
            // screen for them. If one lands here anyway, explain and let the
            // rider continue with auto-assigned spaces rather than dead-end.
            uiState.sellsPassage -> FullScreenMessage(
                modifier = Modifier.padding(padding),
                title = "No seat pick needed",
                body = "This service sells passage. We'll assign " +
                    "${uiState.requiredSeatCount} ${uiState.trip?.rideKind?.spaceNoun(plural = uiState.requiredSeatCount != 1)} " +
                    "for your party; fastcraft seating is allocated at the port.",
                actionLabel = "Continue",
                onAction = proceed,
                isError = false,
            )

            uiState.seats.isEmpty() -> FullScreenMessage(
                modifier = Modifier.padding(padding),
                title = "No seat map for this trip",
                body = "The operator hasn't published seats for this departure yet. Try again in a moment or pick another trip.",
                actionLabel = "Retry",
                onAction = viewModel::load,
                isError = true,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                if (uiState.feedStatus != SeatFeedStatus.LIVE) {
                    FeedStatusBanner(status = uiState.feedStatus, onRetry = viewModel::retryConnection)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                uiState.partyError?.let {
                    PartyRuleCard(message = it)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                uiState.trip?.let {
                    TripSummaryCard(trip = it, seatCount = uiState.requiredSeatCount, infants = uiState.infants)
                }

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

                CabinGrid(
                    seats = uiState.seats,
                    columnCount = uiState.columnCount,
                    onSeatClicked = viewModel::onSeatClicked,
                )

                Spacer(modifier = Modifier.height(16.dp))
                HoldNoticeCard()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (uiState.holdExpired) {
        HoldExpiredDialog(onPickAgain = viewModel::onHoldExpiredAcknowledged)
    }
}

@Composable
private fun FullScreenMessage(
    modifier: Modifier,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    isError: Boolean,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (isError) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun FeedStatusBanner(status: SeatFeedStatus, onRetry: () -> Unit) {
    val offline = status == SeatFeedStatus.OFFLINE
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (offline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (offline) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                if (offline) "Live seat updates are off. Seats may have changed." else "Reconnecting to live seat updates…",
                style = MaterialTheme.typography.bodySmall,
                color = if (offline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            if (offline) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun PartyRuleCard(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun TripSummaryCard(trip: Trip, seatCount: Int, infants: Int) {
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
                "${trip.operatorName} • ${trip.busClass.displayLabel(trip.rideKind)} • ${formatDeparture(trip.departureEpochMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                partySummary(seatCount, infants),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
            SeatStatus.entries.forEach { status ->
                LegendItem(status)
            }
        }
    }
}

@Composable
private fun LegendItem(status: SeatStatus) {
    val look = seatLook(status)
    val label = when (status) {
        SeatStatus.AVAILABLE -> "Available"
        SeatStatus.SELECTED -> "Selected"
        SeatStatus.OCCUPIED -> "Occupied"
        SeatStatus.LOCKED -> "Held"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Legend: $label seat, ${seatStateDescription(status)}"
        },
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(look.background, RoundedCornerShape(4.dp))
                .then(
                    if (look.bordered) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            look.icon?.let {
                Icon(it, contentDescription = null, tint = look.content, modifier = Modifier.size(12.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * The cabin. Columns come from the data (`maxOf { column }`), the aisle always
 * sits after column 2: that is both the 2+2 ordinary/deluxe layout and the 2+1
 * luxury layout. A row missing a seat keeps an empty slot so the columns line
 * up. The grid scrolls sideways so four seats never clip on a narrow phone.
 */
@Composable
private fun CabinGrid(seats: List<Seat>, columnCount: Int, onSeatClicked: (Seat) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp).horizontalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.width(cabinWidth(columnCount)),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        "Driver",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Text(
                    "Door",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            seats.groupBy { it.row }.toSortedMap().forEach { (_, rowSeats) ->
                val byColumn = rowSeats.associateBy { it.column }
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(SeatGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (column in 1..columnCount) {
                        if (column == 3) Spacer(modifier = Modifier.width(AisleWidth - SeatGap)) // aisle
                        val seat = byColumn[column]
                        if (seat != null) SeatCell(seat, onSeatClicked) else Spacer(modifier = Modifier.size(SeatSize))
                    }
                }
            }
        }
    }
}

private fun cabinWidth(columnCount: Int) =
    SeatSize * columnCount + SeatGap * (columnCount - 1).coerceAtLeast(0) + (if (columnCount > 2) AisleWidth - SeatGap else 0.dp)

/** Colour and marker for one seat state; colour alone must not carry the meaning. */
private data class SeatLook(
    val background: Color,
    val content: Color,
    val bordered: Boolean,
    val icon: ImageVector?,
)

@Composable
private fun seatLook(status: SeatStatus): SeatLook = when (status) {
    SeatStatus.AVAILABLE -> SeatLook(
        background = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        bordered = true,
        icon = null,
    )
    SeatStatus.SELECTED -> SeatLook(
        background = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onPrimary,
        bordered = false,
        icon = Icons.Filled.Check,
    )
    SeatStatus.OCCUPIED -> SeatLook(
        background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        bordered = false,
        icon = Icons.Filled.Close,
    )
    SeatStatus.LOCKED -> SeatLook(
        background = MaterialTheme.colorScheme.tertiary,
        content = MaterialTheme.colorScheme.onTertiary,
        bordered = false,
        icon = Icons.Filled.Lock,
    )
}

private fun seatStateDescription(status: SeatStatus): String = when (status) {
    SeatStatus.AVAILABLE -> "available"
    SeatStatus.SELECTED -> "selected"
    SeatStatus.OCCUPIED -> "occupied"
    SeatStatus.LOCKED -> "held by another passenger"
}

@Composable
private fun SeatCell(seat: Seat, onClick: (Seat) -> Unit) {
    val isClickable = seat.status == SeatStatus.AVAILABLE || seat.status == SeatStatus.SELECTED
    val look = seatLook(seat.status)
    val stateText = seatStateDescription(seat.status)

    Box(
        modifier = Modifier
            .size(SeatSize)
            .background(look.background, SeatShape)
            .border(
                width = 1.dp,
                color = if (look.bordered) MaterialTheme.colorScheme.outline else Color.Transparent,
                shape = SeatShape,
            )
            .clickable(enabled = isClickable, role = Role.Button) { onClick(seat) }
            // The label and marker icon inside would otherwise be read on top of
            // this description; one node per seat keeps TalkBack to "Seat 3A, available".
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Seat ${seat.label}, $stateText"
                stateDescription = stateText
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                seat.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = look.content,
                textAlign = TextAlign.Center,
            )
            look.icon?.let {
                Icon(it, contentDescription = null, tint = look.content, modifier = Modifier.size(12.dp))
            }
        }
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
                "Selected seats are held for you while you complete checkout. If the hold runs out, you'll need to pick again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun HoldExpiredDialog(onPickAgain: () -> Unit) {
    AlertDialog(
        onDismissRequest = onPickAgain,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text("Seat hold expired") },
        text = { Text("Your seat hold expired. Pick your seats again.") },
        confirmButton = {
            Button(onClick = onPickAgain) { Text("Pick again") }
        },
    )
}

@Composable
private fun HoldCountdown(secondsRemaining: Int) {
    val color = when {
        secondsRemaining <= HOLD_URGENT_SECONDS -> MaterialTheme.colorScheme.error
        secondsRemaining <= HOLD_WARN_SECONDS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // A live region announces every change, so the spoken text moves per
    // minute (and at the 2:00 / 1:00 warnings), never per second.
    val announcement = when {
        secondsRemaining <= 0 -> "Seat hold expired"
        secondsRemaining <= HOLD_URGENT_SECONDS -> "Less than a minute left to confirm your seats"
        secondsRemaining <= HOLD_WARN_SECONDS -> "Two minutes left to confirm your seats"
        else -> "Seats held for ${secondsRemaining / 60} more minutes"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
    ) {
        if (secondsRemaining <= HOLD_WARN_SECONDS) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            "Seats held for ${formatCountdown(secondsRemaining)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (secondsRemaining <= HOLD_WARN_SECONDS) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun FareBottomBar(uiState: SeatMapUiState, onConfirm: () -> Unit) {
    val requiredCount = uiState.requiredSeatCount
    val noun = uiState.trip?.rideKind?.spaceNoun(plural = requiredCount != 1) ?: "seats"
    val passageFare = (uiState.trip?.farePhp ?: 0.0) * requiredCount
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            val secondsRemaining = uiState.holdSecondsRemaining
            if (secondsRemaining != null && uiState.selectedSeatIds.isNotEmpty()) {
                HoldCountdown(secondsRemaining)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (uiState.sellsPassage) {
                            "TOTAL FARE • $requiredCount ${noun.uppercase()}"
                        } else {
                            "TOTAL FARE • ${uiState.selectedSeatIds.size}/$requiredCount SEATS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatPhp(if (uiState.sellsPassage) passageFare else uiState.totalFarePhp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Discounts for students/seniors/PWD are applied at checkout",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onConfirm,
                    enabled = uiState.canProceed,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(
                        when {
                            uiState.sellsPassage -> "Continue"
                            uiState.canProceed -> "Confirm Seats"
                            else -> "Pick $requiredCount $noun"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
