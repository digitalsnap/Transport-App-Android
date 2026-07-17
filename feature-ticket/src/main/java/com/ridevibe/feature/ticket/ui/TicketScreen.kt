package com.ridevibe.feature.ticket.ui

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.feature.ticket.viewmodel.TicketViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Your Ticket" screen per the Visily design: confirmed (page 6) or unpaid reservation (page 7). */
@Composable
fun TicketScreen(
    onBackToHome: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Your Ticket", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                    }
                },
                actions = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
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

            else -> uiState.ticket?.let { ticket ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (uiState.isReservation) {
                        ReservationHeader(uiState.expirySecondsRemaining)
                    } else {
                        ConfirmedHeader()
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    DigitalTicketCard(
                        operatorName = ticket.trip.operatorName,
                        classLabel = ticket.classLabel(),
                        seatLabel = ticket.seatLabels.joinToString(", "),
                        ticketId = ticket.id,
                        dateLabel = formatDate(ticket.trip.departureEpochMillis),
                        departureLabel = formatTime(ticket.trip.departureEpochMillis),
                        origin = ticket.trip.origin,
                        destination = ticket.trip.destination,
                        passengerName = ticket.primaryPassenger.fullName,
                        passengerTypeLabel = ticket.passengerTypeLabel(),
                        qrPayload = ticket.qrPayload,
                        coPassengers = ticket.coPassengers,
                        infantCount = ticket.infantCount,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (uiState.isReservation) {
                        PaymentRequiredNotice()
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    OutlinedButton(
                        onClick = { shareTicket(context, ticket) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Share Ticket", fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = onBackToHome, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("Back to Home")
                    }

                    ValidityNotice(ticket)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ConfirmedHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(12.dp))
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF22C55E),
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Booking Confirmed!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Your ticket is ready. Please present the QR code to the conductor upon boarding.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReservationHeader(expirySecondsRemaining: Long?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "EXPIRES IN",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            expirySecondsRemaining?.let(::formatCountdown) ?: "—",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PaymentRequiredNotice() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    "Payment Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Please present this QR code to the bus conductor. You can pay via cash or " +
                        "digital wallet on-board to finalize your seat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ValidityNotice(ticket: Ticket) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    "Validity Notice",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "This ticket is valid for the ${formatDate(ticket.trip.departureEpochMillis)}, " +
                        "${formatTime(ticket.trip.departureEpochMillis)} trip only. Boarding closes " +
                        "15 minutes before departure. Please bring a valid ID for verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun shareTicket(context: android.content.Context, ticket: Ticket) {
    val text = buildString {
        appendLine("RideVibe Ticket ${ticket.id}")
        appendLine("${ticket.trip.origin} → ${ticket.trip.destination}")
        appendLine("${formatDate(ticket.trip.departureEpochMillis)} • ${formatTime(ticket.trip.departureEpochMillis)}")
        appendLine("Seat${if (ticket.seatLabels.size == 1) "" else "s"} ${ticket.seatLabels.joinToString(", ")} • ${ticket.trip.operatorName}")
        appendLine("Passengers: ${ticket.primaryPassenger.fullName}" +
            ticket.coPassengers.joinToString("") { ", ${it.firstName} ${it.lastName}" })
        if (ticket.infantCount > 0) appendLine("Infants (free): ${ticket.infantCount}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share ticket"))
}

private fun Ticket.classLabel(): String {
    val busClass = trip.busClass.name.lowercase().replaceFirstChar { it.uppercase() }
    return when (primaryPassenger.type) {
        PassengerType.REGULAR -> busClass
        PassengerType.STUDENT -> "$busClass • Student"
        PassengerType.SENIOR_CITIZEN -> "$busClass • Senior"
        PassengerType.PWD -> "$busClass • PWD"
    }
}

private fun Ticket.passengerTypeLabel(): String = when (primaryPassenger.type) {
    PassengerType.REGULAR -> "Regular Passenger"
    PassengerType.STUDENT -> "Student Passenger"
    PassengerType.SENIOR_CITIZEN -> "Senior Citizen"
    PassengerType.PWD -> "PWD Passenger"
}

private fun formatCountdown(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(epochMillis))
