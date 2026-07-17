package com.ridevibe.feature.ticket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridevibe.core.domain.model.PaymentStatus

/**
 * The consumer-facing digital boarding pass. [qrPayload] is a server-signed
 * token — this composable only renders it, it does not decide its contents.
 */
@Composable
fun DigitalTicketCard(
    routeLabel: String, // e.g. "Manila → Baguio"
    departureLabel: String, // e.g. "Jul 20, 2026 • 6:00 AM"
    seatLabel: String,
    passengerClassLabel: String, // e.g. "Deluxe" or "Student discount"
    paymentStatus: PaymentStatus,
    qrPayload: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = routeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PaymentStatusBadge(paymentStatus)
            }

            Text(
                text = departureLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TicketDetail(label = "Seat", value = seatLabel)
                TicketDetail(label = "Class", value = passengerClassLabel)
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val qrBitmap = rememberQrCodeBitmap(qrPayload)
                Image(
                    painter = BitmapPainter(qrBitmap),
                    contentDescription = "Boarding QR code",
                    modifier = Modifier.size(200.dp),
                )
                Text(
                    text = "Present this code to the conductor",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TicketDetail(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PaymentStatusBadge(status: PaymentStatus) {
    val (label, containerColor) = when (status) {
        PaymentStatus.PAID -> "PAID" to MaterialTheme.colorScheme.primaryContainer
        PaymentStatus.CASH_ON_BOARD -> "CASH ON BOARD" to MaterialTheme.colorScheme.tertiaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = containerColor) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
