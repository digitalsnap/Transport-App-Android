package com.ridevibe.feature.ticket.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.PassengerType

/**
 * RideVibe boarding pass card (design pages 6-7): indigo operator header,
 * high-density QR, ticket id, and the trip detail grid. [qrPayload] is a
 * server-signed token — this composable only renders it.
 */
@Composable
fun DigitalTicketCard(
    operatorName: String,
    classLabel: String, // e.g. "Premier Executive Class" / "Deluxe • Senior"
    seatLabel: String, // e.g. "2A" or "2A, 2B"
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
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        // Indigo operator header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.2f)) {
                Icon(
                    Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(8.dp).size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    operatorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    classLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.25f)) {
                Text(
                    "Seat $seatLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val qrBitmap = rememberQrCodeBitmap(qrPayload)
            Image(
                painter = BitmapPainter(qrBitmap),
                contentDescription = "Boarding QR code",
                modifier = Modifier.size(190.dp),
            )

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
                    val typeLabel = when (co.type) {
                        PassengerType.REGULAR -> "Regular"
                        PassengerType.STUDENT -> "Student"
                        PassengerType.SENIOR_CITIZEN -> "Senior Citizen"
                        PassengerType.PWD -> "PWD"
                    }
                    Text(
                        co.mobileNumber?.let { "$typeLabel • $it" } ?: typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (infantCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "+ $infantCount lap-held infant${if (infantCount == 1) "" else "s"} (free)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
