package com.ridevibe.feature.admin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.core.domain.model.SupportBooking
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.feature.admin.ui.formatDuration
import com.ridevibe.feature.admin.ui.formatHours
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.ui.formatPhp
import com.ridevibe.feature.admin.ui.formatRating
import com.ridevibe.feature.admin.ui.shortId

/* Row renderers shared by the admin and partner surfaces — the table rows of the dashboards. */

/** Card wrapper for one list row; tappable when [onClick] is set. */
@Composable
fun RowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
fun BusClassPill(busClass: BusClass) {
    Pill(busClass.name, tone = if (busClass == BusClass.LUXURY) PillTone.GOLD else PillTone.PLAIN)
}

@Composable
fun StatusPills(status: BookingStatus, refundStatus: RefundStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (status == BookingStatus.CANCELLED) Pill("CANCELLED", PillTone.BAD) else Pill("CONFIRMED", PillTone.OK)
        if (refundStatus != RefundStatus.NONE) {
            Pill(
                "REFUND ${refundStatus.name}",
                tone = if (refundStatus == RefundStatus.REFUNDED) PillTone.MINT else PillTone.WARN,
            )
        }
    }
}

@Composable
fun PaymentPills(method: PaymentMethod, status: PaymentStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Pill(method.name, PillTone.PLAIN)
        Pill(status.name, tone = if (status == PaymentStatus.PAID) PillTone.OK else PillTone.WARN)
    }
}

/** One row of the Support / Recent bookings table. */
@Composable
fun BookingRow(booking: SupportBooking, onClick: (() -> Unit)? = null, selected: Boolean = false) {
    RowCard(onClick = onClick, selected = selected) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shortId(booking.id),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Booked ${formatPhDateTime(booking.createdAtEpochMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatPhp(booking.farePhp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(booking.passengerFullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "user ${shortId(booking.userId, 8)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("${booking.origin} → ${booking.destination}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            "${booking.operatorName} · ${formatPhDateTime(booking.departureEpochMillis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SeatChips(booking.seatLabels)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PaymentPills(booking.paymentMethod, booking.paymentStatus)
            StatusPills(booking.status, booking.refundStatus)
        }
    }
}

/** One departure with sold / held / available counts — the Trips table row. */
@Composable
fun TripOccupancyRow(trip: TripOccupancy, onClick: (() -> Unit)? = null, selected: Boolean = false) {
    RowCard(onClick = onClick, selected = selected) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatPhDateTime(trip.departureEpochMillis),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("${trip.origin} → ${trip.destination}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                trip.operatorName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(formatPhp(trip.farePhp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BusClassPill(trip.busClass)
                Pill(trip.rideKind.name, PillTone.MINT)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountLabel("Sold", trip.sold.toString())
                CountLabel("Held", trip.held.toString())
                CountLabel("Available", trip.available.toString(), highlight = true)
            }
        }
    }
}

@Composable
private fun CountLabel(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One operator service with its seat layout — Partners detail and "My services". */
@Composable
fun ServiceRow(service: OperatorService, trailing: (@Composable () -> Unit)? = null) {
    RowCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${service.origin} → ${service.destination}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(service.mode, PillTone.PLAIN)
                    BusClassPill(service.busClass)
                    Pill(service.rideKind.name, PillTone.MINT)
                }
            }
            Text(formatPhp(service.farePhp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LabeledText("Departure hours (PH)", formatHours(service.departureHours))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LabeledText("Duration", formatDuration(service.durationMinutes))
            LabeledText("Seating", service.seatLayout)
            LabeledText("Rating", formatRating(service.rating))
        }
        if (trailing != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { trailing() }
        }
    }
}
