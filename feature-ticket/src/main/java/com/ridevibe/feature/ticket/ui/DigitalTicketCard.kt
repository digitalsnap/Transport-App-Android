package com.ridevibe.feature.ticket.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.feature.ticket.format.TicketFormatter

/**
 * RideVibe boarding pass card (design pages 6-7): dark operator header,
 * high-density QR, ticket id, and the trip detail grid. [qrPayload] is a
 * server-signed token — this composable only renders it.
 *
 * [statusOverlay] ("CANCELLED", "REFUNDED", "LAPSED") greys the QR and stamps
 * it, so a dead ticket can never be mistaken for a live one at the door.
 */
@Composable
fun DigitalTicketCard(
    operatorName: String,
    classLabel: String, // e.g. "Deluxe • Senior" / "Tourist Class"
    seatLabel: String, // e.g. "2A" or "2A, 2B" or "P1"
    ticketId: String,
    dateLabel: String,
    departureLabel: String,
    origin: String,
    destination: String,
    passengerName: String,
    passengerTypeLabel: String, // e.g. "Regular Passenger"
    qrPayload: String,
    modifier: Modifier = Modifier,
    coPassengers: List<CoPassenger> = emptyList(),
    infantCount: Int = 0,
    rideKind: RideKind = RideKind.BUS,
    statusOverlay: String? = null,
) {
    val seatNoun = TicketFormatter.seatNoun(rideKind, plural = seatLabel.contains(','))
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        // Inverse-surface header keeps the dark chrome of the design in both themes.
        val headerColor = MaterialTheme.colorScheme.inverseSurface
        val onHeader = MaterialTheme.colorScheme.inverseOnSurface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = onHeader.copy(alpha = 0.2f)) {
                Icon(
                    if (rideKind.sellsPassage) Icons.Filled.DirectionsBoat else Icons.Filled.DirectionsBus,
                    contentDescription = if (rideKind.sellsPassage) "Sea trip" else "Bus trip",
                    tint = onHeader,
                    modifier = Modifier.padding(8.dp).size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    operatorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onHeader,
                )
                Text(
                    classLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onHeader.copy(alpha = 0.85f),
                )
            }
            Surface(shape = RoundedCornerShape(50), color = onHeader.copy(alpha = 0.25f)) {
                Text(
                    "$seatNoun $seatLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = onHeader,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Gold trim: the one sanctioned Gold accent on the ticket.
        HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.tertiary)

        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrPanel(qrPayload = qrPayload, ticketId = ticketId, statusOverlay = statusOverlay)

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "TICKET ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                ticketId,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TicketDetail("DATE", dateLabel)
                TicketDetail("DEPARTURE", departureLabel, alignEnd = true)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TicketDetail("FROM", origin)
                TicketDetail("TO", destination, alignEnd = true)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(passengerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    passengerTypeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                coPassengers.forEach { co ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${co.firstName} ${co.lastName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val typeLabel = TicketFormatter.coPassengerTypeLabel(co.type)
                    Text(
                        co.mobileNumber?.let { "$typeLabel • $it" } ?: typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (infantCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "+ $infantCount infant${if (infantCount == 1) "" else "s"} on lap (free)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrPanel(qrPayload: String, ticketId: String, statusOverlay: String?) {
    val qr by rememberQrCode(qrPayload)
    // The quiet zone stays pure white whatever the theme: boarding scanners
    // need the black/white contrast, and a dark-theme surface behind the
    // code cuts the read rate. This is the one deliberate hard-coded colour.
    Box(
        modifier = Modifier
            .size(206.dp)
            .background(Color.White, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (val render = qr) {
            QrRender.Loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))

            is QrRender.Ready -> Image(
                bitmap = render.image,
                contentDescription = if (statusOverlay == null) "Boarding QR code" else "Boarding QR code, $statusOverlay",
                modifier = Modifier
                    .size(190.dp)
                    .alpha(if (statusOverlay == null) 1f else 0.25f),
            )

            QrRender.Unavailable -> Text(
                "QR unavailable — show ticket ID $ticketId",
                style = MaterialTheme.typography.bodySmall,
                // Text sits on the white quiet zone, so it must be dark regardless of theme.
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
        statusOverlay?.let { stamp ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error,
            ) {
                Text(
                    stamp,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TicketDetail(label: String, value: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
