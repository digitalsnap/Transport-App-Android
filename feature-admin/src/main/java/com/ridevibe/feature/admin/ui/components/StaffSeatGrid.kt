package com.ridevibe.feature.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.ui.humanize
import com.ridevibe.feature.admin.ui.shortId

/*
 * Read-only re-creation of feature-seatmap's CabinGrid / SeatCell for staff
 * seat allocation. Colours through theme roles only:
 *   AVAILABLE  -> surface + outline border
 *   LOCKED     -> tertiaryContainer (gold "held" like the dashboards' amber)
 *   OCCUPIED   -> inverseSurface (dark grey "sold")
 *   picked     -> primaryContainer + primary border (partner on-site sale)
 */

@Composable
private fun seatColors(status: SeatStatus, picked: Boolean): Pair<Color, Color> = when {
    picked -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    status == SeatStatus.AVAILABLE -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    status == SeatStatus.LOCKED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    status == SeatStatus.OCCUPIED -> MaterialTheme.colorScheme.inverseSurface to MaterialTheme.colorScheme.inverseOnSurface
    else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
}

/**
 * Cabin grid: rows sorted, columns 1–2 left of the aisle and 3+ right of it
 * (2×2 ordinary/deluxe, 2×1 luxury). Every seat is tappable so staff can
 * inspect it; [pickedLabels] highlights seats chosen for an on-site sale.
 */
@Composable
fun StaffSeatGrid(
    seats: List<AllocatedSeat>,
    modifier: Modifier = Modifier,
    pickedLabels: Set<String> = emptySet(),
    onSeatClick: ((AllocatedSeat) -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(
                        "DRIVER",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Text(
                    "Entrance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            seats.groupBy { it.row }.toSortedMap().forEach { (_, rowSeats) ->
                val byColumn = rowSeats.sortedBy { it.column }
                val left = byColumn.filter { it.column <= 2 }
                val right = byColumn.filter { it.column >= 3 }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        left.forEach { StaffSeatCell(it, it.label in pickedLabels, onSeatClick) }
                    }
                    Spacer(modifier = Modifier.width(20.dp)) // aisle
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        right.forEach { StaffSeatCell(it, it.label in pickedLabels, onSeatClick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffSeatCell(seat: AllocatedSeat, picked: Boolean, onClick: ((AllocatedSeat) -> Unit)?) {
    val (background, content) = seatColors(seat.status, picked)
    val borderColor = when {
        picked -> MaterialTheme.colorScheme.primary
        seat.status == SeatStatus.AVAILABLE -> MaterialTheme.colorScheme.outline
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 40.dp)
            .background(background, RoundedCornerShape(10.dp))
            .border(if (picked) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick(seat) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            seat.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** AVAILABLE / LOCKED / OCCUPIED legend; [pickable] adds the on-site sale hint. */
@Composable
fun StaffSeatLegend(modifier: Modifier = Modifier, pickable: Boolean = false, partnerView: Boolean = false) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        LegendEntry(
            if (pickable) "Available (tap to select for on-site sale)" else "Available",
            MaterialTheme.colorScheme.surface,
            bordered = true,
        )
        LegendEntry(
            if (partnerView) "Locked — being selected now" else "Locked — tap for holder & expiry",
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        LegendEntry(
            if (partnerView) "Occupied — tap for passenger name" else "Occupied — tap for ticket id",
            MaterialTheme.colorScheme.inverseSurface,
        )
    }
}

@Composable
private fun LegendEntry(label: String, color: Color, bordered: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(color, bordered)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Tap-to-inspect popup: the hover tooltip of the web seat grid. */
@Composable
fun SeatInspectDialog(seat: AllocatedSeat, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seat ${seat.label}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                LabeledValue("Status") {
                    Pill(
                        seat.status.name,
                        tone = when (seat.status) {
                            SeatStatus.AVAILABLE -> PillTone.OK
                            SeatStatus.LOCKED -> PillTone.WARN
                            SeatStatus.OCCUPIED -> PillTone.PLAIN
                            SeatStatus.SELECTED -> PillTone.MINT
                        },
                    )
                }
                seat.passengerName?.let { LabeledText("Passenger", it) }
                seat.bookingId?.let { LabeledValue("Booking id") { CodeText(it) } }
                seat.heldByUserId?.let { LabeledValue("Held by user") { CodeText(shortId(it, 8)) } }
                seat.holdExpiresAtEpochMillis?.let { LabeledText("Hold expires (PH)", formatPhDateTime(it)) }
                if (seat.status == SeatStatus.AVAILABLE) {
                    HintText("Free — nobody is holding or has bought this seat.")
                }
                seat.id?.let { LabeledValue("Seat id") { CodeText(it) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Ferries and fastcraft board open (roll-on/roll-off) — no seat map, just the
 * Sold / Being selected / Available tiles from the dashboards.
 */
@Composable
fun OpenSeatingSummary(trip: TripOccupancy, modifier: Modifier = Modifier, partnerView: Boolean = false) {
    Column(modifier = modifier.fillMaxWidth()) {
        StatTileGrid(
            listOf(
                StatTileSpec("Sold", trip.sold.toString()),
                StatTileSpec("Being selected", trip.held.toString()),
                StatTileSpec("Available", trip.available.toString(), accent = true),
                StatTileSpec("Capacity", trip.seats.toString()),
            ),
        )
        Spacer(modifier = Modifier.height(10.dp))
        HintText(
            "${humanize(trip.rideKind.name)} — open seating (roll-on/roll-off boarding); no seat map. " +
                if (partnerView) {
                    "Passenger names are on the Manifest tab."
                } else {
                    "Passengers for this departure are in Support → search by trip, or the partner's Manifest."
                },
        )
    }
}
