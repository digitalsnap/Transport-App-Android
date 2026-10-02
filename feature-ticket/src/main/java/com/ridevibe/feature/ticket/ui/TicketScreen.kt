package com.ridevibe.feature.ticket.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.feature.ticket.format.TicketFormatter
import com.ridevibe.feature.ticket.viewmodel.TicketLeg
import com.ridevibe.feature.ticket.viewmodel.TicketViewModel

/**
 * "Your Ticket" screen per the Visily design: confirmed (page 6) or unpaid
 * reservation (page 7). [onBookAgain] is offered once a cash reservation has
 * lapsed; it defaults to [onBackToHome] so existing call sites keep working.
 */
@Composable
fun TicketScreen(
    onBackToHome: () -> Unit,
    onBookAgain: () -> Unit = onBackToHome,
    viewModel: TicketViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    KeepScreenOn(boostBrightness = uiState.boostBrightness)

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }
    LaunchedEffect(uiState.shareIntent) {
        val intent = uiState.shareIntent ?: return@LaunchedEffect
        try {
            context.startActivity(Intent.createChooser(intent, "Share ticket"))
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage("No app on this phone can receive the ticket.")
        }
        viewModel.onShareHandled()
    }
    LaunchedEffect(uiState.calendarIntent) {
        val intent = uiState.calendarIntent ?: return@LaunchedEffect
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage("No calendar app found on this phone.")
        }
        viewModel.onCalendarHandled()
    }

    // API 24–28 only: the public Pictures folder needs the storage permission.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.saveToDevice() else viewModel.showMessage("Storage permission is needed to save the ticket.")
    }
    val saveToDevice = {
        if (viewModel.needsStoragePermission()) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.saveToDevice()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    titleContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.errorMessage != null || uiState.legs.isEmpty() -> EmptyState(
                message = uiState.errorMessage ?: "No ticket to show",
                onBack = onBackToHome,
                modifier = Modifier.padding(padding),
            )

            uiState.isLoading && uiState.tickets.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> {
                val tickets = uiState.tickets
                val rideKind = tickets.firstOrNull()?.trip?.rideKind ?: RideKind.BUS
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    uiState.cancelledStatus?.let { status ->
                        StatusBanner(status)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    when {
                        uiState.cancelledStatus != null -> Unit
                        uiState.reservationExpired -> LapsedHeader(onBookAgain)
                        uiState.isReservation -> ReservationHeader(uiState.expirySecondsRemaining)
                        else -> ConfirmedHeader(rideKind)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    uiState.legs.forEachIndexed { index, leg ->
                        if (uiState.legs.size > 1) {
                            Text(
                                "${leg.label.uppercase()} TRIP",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        LegContent(
                            leg = leg,
                            statusOverlay = uiState.qrOverlay,
                            onRetry = { viewModel.retryLeg(index) },
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    if (uiState.isReservation && !uiState.reservationExpired && uiState.cancelledStatus == null) {
                        PaymentRequiredNotice(rideKind)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    ActionRow(
                        enabled = uiState.actionsEnabled,
                        isExporting = uiState.isExporting,
                        boostBrightness = uiState.boostBrightness,
                        legCount = tickets.size,
                        onShare = viewModel::share,
                        onSave = saveToDevice,
                        onCalendar = { viewModel.addToCalendar(index = 0) },
                        onToggleBrightness = viewModel::toggleBrightness,
                    )

                    TextButton(onClick = onBackToHome, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("Back to Home")
                    }

                    tickets.firstOrNull()?.let { ValidityNotice(it) }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * A ticket at the door must not dim or lock mid-scan. The flag and the
 * brightness override live on the Activity window and are undone on dispose,
 * so the rest of the app keeps the system defaults.
 */
@Composable
private fun KeepScreenOn(boostBrightness: Boolean) {
    val context = LocalContext.current
    DisposableEffect(boostBrightness) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (window != null && boostBrightness) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

@Composable
private fun LegContent(leg: TicketLeg, statusOverlay: String?, onRetry: () -> Unit) {
    val ticket = leg.ticket
    when {
        ticket != null -> DigitalTicketCard(
            operatorName = ticket.trip.operatorName,
            classLabel = TicketFormatter.classLabel(ticket),
            seatLabel = ticket.seatLabels.joinToString(", "),
            ticketId = ticket.id,
            dateLabel = TicketFormatter.dateLabel(ticket.trip.departureEpochMillis),
            departureLabel = TicketFormatter.timeLabel(ticket.trip.departureEpochMillis),
            origin = ticket.trip.origin,
            destination = ticket.trip.destination,
            passengerName = ticket.primaryPassenger.fullName,
            passengerTypeLabel = TicketFormatter.passengerTypeLabel(ticket.primaryPassenger.type),
            qrPayload = ticket.qrPayload,
            coPassengers = ticket.coPassengers,
            infantCount = ticket.infantCount,
            rideKind = ticket.trip.rideKind,
            statusOverlay = statusOverlay,
        )

        leg.isLoading -> Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        else -> Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Couldn't load ticket ${leg.ticketId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    leg.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun ActionRow(
    enabled: Boolean,
    isExporting: Boolean,
    boostBrightness: Boolean,
    legCount: Int,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCalendar: () -> Unit,
    onToggleBrightness: () -> Unit,
) {
    OutlinedButton(
        onClick = onShare,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
    ) {
        if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Filled.Share, contentDescription = "Share ticket", modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(if (legCount > 1) "Share Tickets" else "Share Ticket", fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSave, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Download, contentDescription = "Save ticket to device")
        }
        IconButton(onClick = onCalendar, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Event, contentDescription = "Add trip to calendar")
        }
        FilterChip(
            selected = boostBrightness,
            onClick = onToggleBrightness,
            label = { Text("Boost brightness") },
            leadingIcon = {
                Icon(
                    Icons.Filled.BrightnessHigh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun EmptyState(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to Home") }
    }
}

@Composable
private fun StatusBanner(status: BookingStatus) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                if (status == BookingStatus.REFUNDED) "Booking refunded" else "Booking cancelled",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onError,
            )
            Text(
                "This ticket is no longer valid for boarding. Contact support if you didn't expect this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onError,
            )
        }
    }
}

@Composable
private fun ConfirmedHeader(rideKind: RideKind) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(12.dp))
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Booking Confirmed!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Your ticket is ready. Show this QR code to ${TicketFormatter.staffNoun(rideKind)} " +
                if (rideKind.sellsPassage) "at the pier." else "upon boarding.",
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
            expirySecondsRemaining?.let(TicketFormatter::countdown) ?: "No expiry",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LapsedHeader(onBookAgain: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Reservation lapsed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Reservation lapsed — this ticket is no longer valid. The seats were released for other riders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onBookAgain, modifier = Modifier.heightIn(min = 48.dp)) { Text("Book again") }
        }
    }
}

@Composable
private fun PaymentRequiredNotice(rideKind: RideKind) {
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
                    "Show this QR code to ${TicketFormatter.staffNoun(rideKind)}. Pay in cash or by " +
                        "digital wallet ${if (rideKind.sellsPassage) "at the counter" else "on board"} " +
                        "to finalise your ${TicketFormatter.seatNoun(rideKind).lowercase()}.",
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
                    "This ticket is valid for the ${TicketFormatter.dateLabel(ticket.trip.departureEpochMillis)}, " +
                        "${TicketFormatter.timeLabel(ticket.trip.departureEpochMillis)} trip only. Boarding closes " +
                        "${if (ticket.trip.rideKind.sellsPassage) "30" else "15"} minutes before departure. " +
                        "Please bring a valid ID for verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
