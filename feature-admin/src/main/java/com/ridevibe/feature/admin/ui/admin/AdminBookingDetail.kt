package com.ridevibe.feature.admin.ui.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.core.domain.model.SupportBooking
import com.ridevibe.core.domain.model.displayLabel
import com.ridevibe.feature.admin.ui.components.ButtonSpinner
import com.ridevibe.feature.admin.ui.components.CodeText
import com.ridevibe.feature.admin.ui.components.ConfirmDialog
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LabeledText
import com.ridevibe.feature.admin.ui.components.LabeledValue
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.NoteCard
import com.ridevibe.feature.admin.ui.components.PaymentPills
import com.ridevibe.feature.admin.ui.components.Pill
import com.ridevibe.feature.admin.ui.components.PillTone
import com.ridevibe.feature.admin.ui.components.SeatChips
import com.ridevibe.feature.admin.ui.components.StatusPills
import com.ridevibe.feature.admin.ui.components.rememberStaffQrBitmap
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.ui.formatPhp
import com.ridevibe.feature.admin.ui.humanize
import com.ridevibe.feature.admin.ui.shortId

/**
 * Full booking record for the support console with every field, the QR
 * rendered from `qrPayload`, and the three staff actions. Dialog and note
 * state survive rotation (rememberSaveable) so a half-written refund note is
 * not lost to a config change mid-call.
 */
@Composable
fun AdminBookingDetail(
    booking: SupportBooking,
    isActing: Boolean,
    error: String?,
    onCancel: (reason: String) -> Unit,
    onRefund: (RefundStatus, note: String) -> Unit,
    onReissueQr: () -> Unit,
) {
    var showCancelDialog by rememberSaveable(booking.id) { mutableStateOf(false) }
    var showReissueDialog by rememberSaveable(booking.id) { mutableStateOf(false) }
    var showRefundConfirm by rememberSaveable(booking.id) { mutableStateOf(false) }
    var refundChoice by rememberSaveable(booking.id, booking.refundStatus) {
        mutableStateOf(if (booking.refundStatus == RefundStatus.NONE) RefundStatus.REQUESTED else booking.refundStatus)
    }
    var refundNote by rememberSaveable(booking.id, booking.refundNote) { mutableStateOf(booking.refundNote.orEmpty()) }

    // Header
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(booking.passengerFullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Pill(humanize(booking.passengerType.name), PillTone.PLAIN)
            }
            Spacer(modifier = Modifier.height(4.dp))
            CodeText(booking.id)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    StatusPills(booking.status, booking.refundStatus)
    Spacer(modifier = Modifier.height(16.dp))

    // Detail grid
    LabeledValue("Trip") {
        Text("${booking.origin} → ${booking.destination}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${booking.operatorName} · ${booking.busClass.displayLabel(booking.rideKind)} · ${humanize(booking.rideKind.name)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LabeledText("Departure (PH)", formatPhDateTime(booking.departureEpochMillis))
    LabeledValue("Seats") { SeatChips(booking.seatLabels) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LabeledText("Fare / seat", formatPhp(booking.farePhp))
        LabeledText("Infants", booking.infantCount.toString())
    }
    LabeledValue("Payment") { PaymentPills(booking.paymentMethod, booking.paymentStatus) }
    LabeledValue("User id") { CodeText(booking.userId) }
    LabeledValue("Trip id") { CodeText(booking.tripId) }
    LabeledText("Booked", formatPhDateTime(booking.createdAtEpochMillis))
    booking.promoCode?.let { LabeledText("Promo code", it) }
    LabeledValue("Co-passengers") {
        if (booking.coPassengers.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodyMedium)
        } else {
            booking.coPassengers.forEach { passenger ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("${passenger.firstName} ${passenger.lastName}".trim(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(6.dp))
                    Pill(humanize(passenger.type.name), PillTone.PLAIN)
                    passenger.mobileNumber?.let {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    LabeledValue("QR ticket") {
        val qr by rememberStaffQrBitmap(booking.qrPayload)
        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            val bitmap = qr
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Boarding QR for ${shortId(booking.id)}",
                    modifier = Modifier.size(180.dp),
                )
            } else {
                Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        CodeText(booking.qrPayload, modifier = Modifier.fillMaxWidth())
    }

    if (booking.cancelledAtEpochMillis != null) {
        Spacer(modifier = Modifier.height(6.dp))
        NoteCard(
            bold = "Cancelled ${formatPhDateTime(booking.cancelledAtEpochMillis)}",
            text = (booking.cancelReason ?: "No reason recorded") +
                (booking.refundNote?.let { "\nRefund note: $it" } ?: ""),
        )
    }
    booking.refundedAtEpochMillis?.let {
        Spacer(modifier = Modifier.height(6.dp))
        LabeledText("Refunded", formatPhDateTime(it))
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    InlineError(error)

    // Actions
    Button(
        onClick = { showCancelDialog = true },
        enabled = booking.status != BookingStatus.CANCELLED && !isActing,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (booking.status == BookingStatus.CANCELLED) "Already cancelled" else "Cancel booking", fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(16.dp))
    MicroLabel("Refund")
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = refundChoice == RefundStatus.REQUESTED,
            onClick = { refundChoice = RefundStatus.REQUESTED },
            label = { Text("Mark REQUESTED") },
        )
        FilterChip(
            selected = refundChoice == RefundStatus.REFUNDED,
            onClick = { refundChoice = RefundStatus.REFUNDED },
            label = { Text("Mark REFUNDED") },
        )
        FilterChip(
            selected = refundChoice == RefundStatus.NONE,
            onClick = { refundChoice = RefundStatus.NONE },
            label = { Text("Clear") },
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = refundNote,
        onValueChange = { refundNote = it },
        label = { Text("Note (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { showRefundConfirm = true },
        enabled = !isActing,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isActing) ButtonSpinner() else Text("Apply refund status")
    }
    HintText("Bookkeeping only — no payment provider is integrated.", modifier = Modifier.padding(top = 4.dp))

    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(
        onClick = { showReissueDialog = true },
        enabled = !isActing,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Reissue QR (missing ticket)") }

    if (showCancelDialog) {
        CancelBookingDialog(
            booking = booking,
            onConfirm = { reason ->
                showCancelDialog = false
                onCancel(reason)
            },
            onDismiss = { showCancelDialog = false },
        )
    }
    if (showRefundConfirm) {
        ConfirmDialog(
            title = "Apply refund status?",
            text = "Set the refund status of ${shortId(booking.id)} to ${refundChoice.name}" +
                (refundNote.takeIf { it.isNotBlank() }?.let { " with the note \"$it\"" } ?: "") +
                "? This is bookkeeping only — no money moves.",
            confirmLabel = "Apply",
            onConfirm = {
                showRefundConfirm = false
                onRefund(refundChoice, refundNote)
            },
            onDismiss = { showRefundConfirm = false },
        )
    }
    if (showReissueDialog) {
        ConfirmDialog(
            title = "Reissue QR ticket?",
            text = "Issue a fresh QR payload? The old one stops matching support records.",
            confirmLabel = "Issue new QR",
            onConfirm = {
                showReissueDialog = false
                onReissueQr()
            },
            onDismiss = { showReissueDialog = false },
        )
    }
}

/** Cancellation requires a reason; the seats are released back into inventory. */
@Composable
private fun CancelBookingDialog(
    booking: SupportBooking,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel booking", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Cancel ${shortId(booking.id)} and release seats " +
                        "${booking.seatLabels.joinToString(", ").ifBlank { "—" }}?",
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        touched = true
                    },
                    label = { Text("Cancellation reason") },
                    placeholder = { Text("required to cancel") },
                    // Only complain once the person has typed and then cleared it — not on first open.
                    isError = touched && reason.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Cancel booking", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep booking") } },
    )
}
