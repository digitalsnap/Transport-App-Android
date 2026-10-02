package com.ridevibe.feature.admin.ui.partner

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.feature.admin.ui.components.CodeText
import com.ridevibe.feature.admin.ui.components.ConfirmDialog
import com.ridevibe.feature.admin.ui.components.DateSelector
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.ErrorWithRetry
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.PaymentPills
import com.ridevibe.feature.admin.ui.components.Pill
import com.ridevibe.feature.admin.ui.components.PillTone
import com.ridevibe.feature.admin.ui.components.RowCard
import com.ridevibe.feature.admin.ui.components.SeatChips
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.ui.formatPhTime
import com.ridevibe.feature.admin.ui.humanize
import com.ridevibe.feature.admin.ui.shortId
import com.ridevibe.feature.admin.viewmodel.CheckInOutcome
import com.ridevibe.feature.admin.viewmodel.ManifestRow
import com.ridevibe.feature.admin.viewmodel.PartnerManifestViewModel

/**
 * Manifest: the day's bookings grouped per departure — passenger, party,
 * seats, payment — with QR check-in. The rider QR carries the ticket and trip
 * ids, so a scan is matched locally against what is on screen. Boarded marks
 * are device-local until the backend has a check-in endpoint (see
 * `ManifestCheckInStore`). An admin viewing read-only sees the list but
 * cannot board anyone.
 */
@Composable
fun PartnerManifestTab(
    sessionKey: String,
    operatorId: Int?,
    readOnly: Boolean,
    onMessage: (String) -> Unit,
    viewModel: PartnerManifestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(sessionKey, operatorId) { viewModel.start(sessionKey, operatorId) }

    // Manual "Mark boarded" answers on the list (not the scanner) go to the snackbar.
    LaunchedEffect(state.lastOutcome, state.showScanner) {
        val outcome = state.lastOutcome
        if (outcome != null && !state.showScanner) {
            onMessage(
                when (outcome) {
                    is CheckInOutcome.Boarded -> "Boarded ✓ ${outcome.passengerName}"
                    is CheckInOutcome.AlreadyBoarded -> "${outcome.passengerName} already boarded at ${formatPhTime(outcome.boardedAtEpochMillis)}"
                    is CheckInOutcome.Cancelled -> "Cancelled ticket — ${outcome.passengerName} cannot board"
                    is CheckInOutcome.NotOnManifest -> outcome.reason
                },
            )
            viewModel.dismissOutcome()
        }
    }

    if (state.showScanner) {
        ManifestScannerScreen(
            outcome = state.lastOutcome,
            onScanned = viewModel::onScanned,
            onDismissOutcome = viewModel::dismissOutcome,
            onClose = viewModel::closeScanner,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.Top) {
                    PageHeader(
                        "Manifest",
                        "The day's bookings per departure: passenger, party size, seats, payment",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "RideVibe manifest ${state.dateIso}")
                                putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                            }
                            context.startActivity(Intent.createChooser(send, "Share manifest"))
                        },
                        enabled = state.entries.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share manifest as text")
                    }
                    IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh manifest")
                    }
                }
            }
            item {
                DateSelector(
                    dateIso = state.dateIso,
                    onPrevious = viewModel::previousDay,
                    onNext = viewModel::nextDay,
                    onPick = viewModel::setDate,
                )
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    label = { Text("Find a passenger") },
                    placeholder = { Text("Name or booking id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.departureOptions.size > 1) {
                item {
                    Column {
                        MicroLabel("Departure")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            FilterChip(
                                selected = state.departureFilter == null,
                                onClick = { viewModel.onDepartureFilterChanged(null) },
                                label = { Text("All") },
                            )
                            state.departureOptions.forEach { departure ->
                                FilterChip(
                                    selected = state.departureFilter == departure.tripId,
                                    onClick = { viewModel.onDepartureFilterChanged(departure.tripId) },
                                    label = { Text(departure.label) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Hide cancelled", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = state.hideCancelled, onCheckedChange = viewModel::onHideCancelledChanged)
                }
            }
            when {
                state.isLoading && state.entries.isEmpty() -> item { LoadingRow() }
                state.error != null && state.entries.isEmpty() -> item { ErrorWithRetry(state.error!!, onRetry = viewModel::load) }
                state.entries.isEmpty() -> item { EmptyText("No bookings on ${state.dateIso}.") }
                else -> {
                    state.error?.let { item { InlineError(it) } }
                    item {
                        Column {
                            Text(
                                "Boarded ${state.totalBoarded} / ${state.totalConfirmed}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            HintText(
                                "${state.totalBookings} booking${if (state.totalBookings == 1) "" else "s"} · " +
                                    "${state.totalPassengers} passengers boarding" +
                                    if (state.departures.sumOf { it.rows.size } != state.totalBookings) " · filtered" else "",
                            )
                        }
                    }
                    if (state.departures.isEmpty()) {
                        item { EmptyText("No passenger matches.") }
                    }
                    state.departures.forEach { departure ->
                        item(key = "trip-${departure.tripId}") {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                MicroLabel("${formatPhDateTime(departure.departureEpochMillis)} · ${departure.origin} → ${departure.destination}")
                                Text(
                                    "${departure.rows.size} booking${if (departure.rows.size == 1) "" else "s"} · " +
                                        "${departure.passengers} passengers · boarded ${departure.boardedCount} / ${departure.confirmedRows.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(departure.rows, key = { it.entry.id }) { row ->
                            ManifestRowCard(
                                row = row,
                                onMarkBoarded = if (readOnly) null else ({ viewModel.requestManualBoarding(row.entry) }),
                            )
                        }
                    }
                }
            }
        }

        if (!readOnly) {
            ExtendedFloatingActionButton(
                onClick = viewModel::openScanner,
                icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                text = { Text("Scan") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    state.pendingManual?.let { entry ->
        ConfirmDialog(
            title = "Mark as boarded?",
            text = "Board ${entry.passengerFullName} (party of ${entry.partySize}, " +
                "${if (entry.seatLabels.isEmpty()) "open seating" else "seats ${entry.seatLabels.joinToString(", ")}"}) " +
                "without scanning their QR?",
            confirmLabel = "Mark boarded",
            onConfirm = viewModel::confirmManualBoarding,
            onDismiss = viewModel::cancelManualBoarding,
        )
    }
}

@Composable
private fun ManifestRowCard(row: ManifestRow, onMarkBoarded: (() -> Unit)?) {
    val entry = row.entry
    RowCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.passengerFullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(humanize(entry.passengerType.name))
                        append(" · party of ${entry.partySize}")
                        if (entry.infantCount > 0) append(" + ${entry.infantCount} infant${if (entry.infantCount == 1) "" else "s"}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.status == BookingStatus.CANCELLED) Pill("CANCELLED", PillTone.BAD) else Pill("CONFIRMED", PillTone.OK)
                if (row.isBoarded) Pill("BOARDED ${formatPhTime(row.boardedAtEpochMillis ?: 0L)}", PillTone.MINT)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SeatChips(entry.seatLabels)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PaymentPills(entry.paymentMethod, entry.paymentStatus)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // The manifest model carries no contact number yet; the booking id is what support needs.
            CodeText(shortId(entry.id, 18))
            Spacer(modifier = Modifier.width(8.dp))
            Spacer(modifier = Modifier.weight(1f))
            if (onMarkBoarded != null && entry.status == BookingStatus.CONFIRMED && !row.isBoarded) {
                TextButton(onClick = onMarkBoarded) { Text("Mark boarded") }
            }
        }
    }
}
