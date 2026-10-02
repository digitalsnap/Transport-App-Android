package com.ridevibe.feature.checkout.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.format.formatPhp
import com.ridevibe.core.domain.model.FareQuote
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.displayLabel
import com.ridevibe.core.domain.model.spaceNoun
import com.ridevibe.feature.checkout.ocr.IdCaptureCamera
import com.ridevibe.feature.checkout.viewmodel.CheckoutUiState
import com.ridevibe.feature.checkout.viewmodel.CheckoutViewModel
import com.ridevibe.feature.checkout.viewmodel.CoPassengerForm
import com.ridevibe.feature.checkout.viewmodel.PartialFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checkout screen per the Visily design (page 5).
 *
 * [onHoldExpired] fires when the rider acknowledges that the seat hold lapsed;
 * the app should send them back to the seat map. It defaults to [onClose] so
 * existing call sites keep working.
 */
@Composable
fun CheckoutScreen(
    onClose: () -> Unit,
    onBookingConfirmed: (ticketId: String) -> Unit,
    onHoldExpired: () -> Unit = onClose,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showBusyDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.confirmedTicketIds) {
        uiState.confirmedTicketIds?.let(onBookingConfirmed)
    }

    // Leaving mid-submission would orphan a booking the server may still issue.
    // After a partial failure the outbound leg is already a ticket, so leaving
    // means "keep the outbound only": the cart resets and the ticket opens,
    // rather than a stale outbound leg being re-booked from the seat map.
    BackHandler(enabled = uiState.isSubmitting || uiState.partialFailure != null) {
        if (uiState.isSubmitting) showBusyDialog = true else viewModel.continueWithOutboundOnly()
    }
    val guardedClose: () -> Unit = {
        when {
            uiState.isSubmitting -> showBusyDialog = true
            uiState.partialFailure != null -> viewModel.continueWithOutboundOnly()
            else -> onClose()
        }
    }

    val showHoldExpiredDialog = uiState.holdExpired && !uiState.isSubmitting &&
        uiState.confirmedTicketIds == null && uiState.partialFailure == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Checkout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = guardedClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to seat selection")
                    }
                },
                actions = {
                    IconButton(onClick = guardedClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close checkout")
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
        bottomBar = {
            if (!uiState.isLoading && uiState.loadError == null && uiState.partialFailure == null) {
                PayBar(uiState, onPay = viewModel::onPayClicked)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isSubmitting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            uiState.holdSecondsRemaining?.let { seconds ->
                if (uiState.partialFailure == null) HoldChip(seconds, uiState.rideKind)
            }
            when {
                uiState.loadError != null -> LoadErrorContent(
                    message = uiState.loadError.orEmpty(),
                    onRetry = viewModel::loadTrips,
                    onBack = onClose,
                )

                uiState.isLoading -> LoadingSkeleton()

                uiState.partialFailure != null -> PartialFailureContent(
                    failure = uiState.partialFailure ?: return@Column,
                    returnTrip = uiState.returnTrip,
                    isSubmitting = uiState.isSubmitting,
                    onRetry = viewModel::retryReturnLeg,
                    onContinue = viewModel::continueWithOutboundOnly,
                )

                else -> CheckoutForm(uiState, viewModel)
            }
        }
    }

    if (uiState.showPaymentDemoSheet) {
        PaymentDemoSheet(
            method = uiState.paymentMethod,
            onConfirm = viewModel::onPaymentDemoConfirmed,
            onDismiss = viewModel::onPaymentDemoDismissed,
        )
    }

    if (showHoldExpiredDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Your seat hold expired") },
            text = {
                Text(
                    "The ${uiState.rideKind.spaceNoun(plural = true)} were released so other riders can " +
                        "book them. Pick ${uiState.rideKind.spaceNoun(plural = true)} again to continue.",
                )
            },
            confirmButton = { TextButton(onClick = onHoldExpired) { Text("Pick seats again") } },
        )
    }

    if (showBusyDialog) {
        AlertDialog(
            onDismissRequest = { showBusyDialog = false },
            title = { Text("Booking in progress…") },
            text = { Text("Hang on while we confirm your booking. Leaving now could lose the ticket.") },
            confirmButton = { TextButton(onClick = { showBusyDialog = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun CheckoutForm(uiState: CheckoutUiState, viewModel: CheckoutViewModel) {
    val seatNoun = uiState.rideKind.spaceNoun().replaceFirstChar { it.uppercase() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        OrderSummaryCard(uiState)

        SectionTitle(
            "Primary passenger" +
                if (uiState.seatCount > 1) " • $seatNoun ${uiState.seatIds.firstOrNull().orEmpty()}" else "",
        )
        if (uiState.showProfileHint) {
            Text(
                "No name saved on your profile yet — enter it here, or save it once " +
                    "under Profile to skip this next time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NameField(
                value = uiState.primaryFirstName,
                onValueChange = viewModel::onPrimaryFirstNameChanged,
                label = "First name",
                error = uiState.primaryFirstName.takeIf { it.isNotEmpty() }?.let { uiState.primaryFirstNameError },
                modifier = Modifier.weight(1f),
            )
            NameField(
                value = uiState.primaryLastName,
                onValueChange = viewModel::onPrimaryLastNameChanged,
                label = "Last name",
                error = uiState.primaryLastName.takeIf { it.isNotEmpty() }?.let { uiState.primaryLastNameError },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.primaryEmail,
            onValueChange = viewModel::onPrimaryEmailChanged,
            label = { Text(if (uiState.bookingForSelf) "Email (optional)" else "Email") },
            singleLine = true,
            isError = uiState.primaryEmail.isNotEmpty() && uiState.primaryEmailError != null,
            supportingText = uiState.primaryEmail.takeIf { it.isNotEmpty() }?.let { uiState.primaryEmailError }
                ?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        MobileField(
            value = uiState.primaryMobile,
            onValueChange = viewModel::onPrimaryMobileChanged,
            label = if (uiState.bookingForSelf) "Mobile number (optional)" else "Mobile number",
            error = uiState.primaryMobile.takeIf { it.isNotEmpty() }?.let { uiState.primaryMobileError },
            imeAction = ImeAction.Done,
        )
        if (!uiState.bookingForSelf) {
            Text(
                "Booking for someone else: we send the ticket and any schedule changes to these details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PassengerTypeChips(uiState.passengerType, viewModel::onPassengerTypeSelected)

        AnimatedVisibility(visible = uiState.requiresIdCapture) {
            IdCaptureSection(
                typeName = uiState.passengerType.name,
                imagePath = uiState.discountIdImagePath.takeIf { uiState.primaryIdCaptured },
                idNumber = uiState.primaryIdNumber,
                ocrLooksLikeId = uiState.primaryOcrLooksLikeId,
                cameraOpen = uiState.capturingForIndex == -1,
                captureError = uiState.captureError.takeIf { uiState.capturingForIndex == -1 },
                onStartCapture = { viewModel.onStartCapture(forIndex = -1) },
                onRetake = { viewModel.onRetake(forIndex = -1) },
                onCaptured = { path, text -> viewModel.onIdCaptured(index = -1, imagePath = path, ocrText = text) },
                onCaptureError = viewModel::onCaptureFailed,
                onCancelCapture = viewModel::onCancelCapture,
                onIdNumberChanged = viewModel::onPrimaryIdNumberChanged,
            )
        }

        uiState.coPassengers.forEachIndexed { index, form ->
            CoPassengerCard(
                index = index,
                seatLabel = uiState.seatIds.getOrNull(index + 1).orEmpty(),
                seatNoun = seatNoun,
                form = form,
                cameraOpen = uiState.capturingForIndex == index,
                captureError = uiState.captureError.takeIf { uiState.capturingForIndex == index },
                onChanged = { transform -> viewModel.onCoPassengerChanged(index, transform) },
                onTypeSelected = { type -> viewModel.onCoPassengerTypeSelected(index, type) },
                onStartCapture = { viewModel.onStartCapture(forIndex = index) },
                onRetake = { viewModel.onRetake(forIndex = index) },
                onCaptured = { path, text -> viewModel.onIdCaptured(index = index, imagePath = path, ocrText = text) },
                onCaptureError = viewModel::onCaptureFailed,
                onCancelCapture = viewModel::onCancelCapture,
            )
        }

        if (uiState.infantCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "${uiState.infantCount} infant${if (uiState.infantCount == 1) "" else "s"} on lap, free — " +
                        "no ${uiState.rideKind.spaceNoun()} assigned.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        uiState.partyError?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        SectionTitle("Promo code")
        OutlinedTextField(
            value = uiState.promoCode,
            onValueChange = viewModel::onPromoCodeChanged,
            label = { Text("Promo code (applied by the server at booking)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Payment method")
        PaymentMethodList(uiState.paymentMethod, viewModel::onPaymentMethodSelected)

        Spacer(modifier = Modifier.height(20.dp))
        FareBreakdown(uiState)

        uiState.errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PayBar(uiState: CheckoutUiState, onPay: () -> Unit) {
    val enabled = uiState.canSubmit && !uiState.isSubmitting && !uiState.holdExpired
    val total = formatPhp(uiState.totalPhp)
    val label = when {
        uiState.isSubmitting -> "Processing…"
        uiState.paymentMethod == PaymentMethod.CASH_ON_BOARD -> "Reserve, pay on board • $total"
        else -> "Continue to ${uiState.paymentMethod.providerLabel()} • $total"
    }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (!enabled && !uiState.isSubmitting) {
                uiState.firstBlockingReason?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Button(
                onClick = onPay,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun PaymentMethod.providerLabel(): String = when (this) {
    PaymentMethod.GCASH -> "GCash"
    PaymentMethod.QR_PH -> "QR Ph"
    PaymentMethod.CARD -> "card"
    PaymentMethod.CASH_ON_BOARD -> "cash on board"
}

/**
 * The explicit demo gate. No payment provider is connected, so the online
 * methods confirm the booking directly once the rider acknowledges that.
 */
// TODO(payments): replace with the PSP flow once a provider is chosen — see docs/DEVELOPER-ACTIONS.md §B
@Composable
private fun PaymentDemoSheet(method: PaymentMethod, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                "Payment provider not connected yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${method.providerLabel().replaceFirstChar { it.uppercase() }} checkout is not live in this build. " +
                    "Confirming here books the trip as a demo — no money moves.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Cancel") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Confirm") }
            }
        }
    }
}

@Composable
private fun HoldChip(secondsRemaining: Long, rideKind: RideKind) {
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    val urgent = secondsRemaining < 60
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    if (secondsRemaining == 0L) {
                        "Hold expired"
                    } else {
                        "${rideKind.spaceNoun(plural = true).replaceFirstChar { it.uppercase() }} held for " +
                            "%d:%02d".format(minutes, seconds)
                    },
                )
            },
            leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = if (urgent) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                disabledLabelColor = if (urgent) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                disabledLeadingIconContentColor = if (urgent) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            ),
        )
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        listOf(140.dp, 56.dp, 56.dp, 120.dp).forEach { height ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            "Loading trip details…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadErrorContent(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
    }
}

@Composable
private fun PartialFailureContent(
    failure: PartialFailure,
    returnTrip: Trip?,
    isSubmitting: Boolean,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Outbound booked, return leg failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your outbound ticket ${failure.outboundTicketId} is confirmed. " +
                        "The return trip" +
                        (returnTrip?.let { " ${it.origin} → ${it.destination}" } ?: "") +
                        " could not be booked: ${failure.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) { Text(if (isSubmitting) "Retrying…" else "Retry return leg", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onContinue,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) { Text("Continue with outbound only") }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Continuing keeps the outbound ticket. You can book the return trip separately from Home.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = imeAction,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

@Composable
private fun MobileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = imeAction),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OrderSummaryCard(uiState: CheckoutUiState) {
    val trip = uiState.trip ?: return
    val sea = trip.rideKind.sellsPassage
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "TRIP DETAILS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                    Text(
                        "${trip.busClass.displayLabel(trip.rideKind)} ${trip.rideKind.kindLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(trip.origin, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("To", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(trip.destination, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        passageLabel(trip.rideKind, uiState.seatCount).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        // Fastcraft/ferry berthing is assigned at the port counter.
                        if (sea) "Assigned at the port" else uiState.seatIds.joinToString(", "),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "DEPARTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(formatDeparture(trip.departureEpochMillis), fontWeight = FontWeight.SemiBold)
                }
            }

            uiState.returnTrip?.let { returnTrip ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "RETURN • ${returnTrip.operatorName}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            "${returnTrip.origin} → ${returnTrip.destination}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (returnTrip.rideKind.sellsPassage) {
                                "${passageLabel(returnTrip.rideKind, uiState.returnSeatIds.size)} assigned at the port"
                            } else {
                                "${passageLabel(returnTrip.rideKind, uiState.returnSeatIds.size)} " +
                                    uiState.returnSeatIds.joinToString(", ")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "DEPARTURE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(formatDeparture(returnTrip.departureEpochMillis), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** "Seat"/"Seats" for buses; ferries and fastcrafts sell passage, so "Passage"/"Passages". */
private fun passageLabel(rideKind: RideKind, count: Int): String = when {
    rideKind.sellsPassage -> if (count == 1) "Passage" else "Passages"
    else -> rideKind.spaceNoun(plural = count != 1).replaceFirstChar { it.uppercase() }
}

private fun RideKind.kindLabel(): String = when (this) {
    RideKind.BUS -> "Bus"
    RideKind.FERRY -> "Ferry"
    RideKind.FASTCRAFT -> "Fastcraft"
}

@Composable
private fun PassengerTypeChips(selected: PassengerType, onSelect: (PassengerType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            PassengerType.REGULAR to "Regular",
            PassengerType.STUDENT to "Student",
            PassengerType.SENIOR_CITIZEN to "Senior",
            PassengerType.PWD to "PWD",
        ).forEach { (type, label) ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(label) },
                shape = RoundedCornerShape(50),
                // One of four fare types, not an independent toggle: read it as a radio option.
                modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** ID capture for a Student/Senior/PWD discount. Only one camera opens at a time. */
@Composable
private fun IdCaptureSection(
    typeName: String,
    imagePath: String?,
    idNumber: String,
    ocrLooksLikeId: Boolean,
    cameraOpen: Boolean,
    captureError: String?,
    onStartCapture: () -> Unit,
    onRetake: () -> Unit,
    onCaptured: (path: String, ocrText: String) -> Unit,
    onCaptureError: (String) -> Unit,
    onCancelCapture: () -> Unit,
    onIdNumberChanged: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "A photo of the ${typeName.lowercase().replace('_', ' ')} ID is required " +
                    "for the 20% fare discount. The conductor checks the physical ID on board.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(10.dp))
            when {
                imagePath != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IdThumbnail(imagePath)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "ID captured",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(onClick = onRetake, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Retake")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = idNumber,
                        onValueChange = onIdNumberChanged,
                        label = { Text("ID number") },
                        singleLine = true,
                        supportingText = if (!ocrLooksLikeId) {
                            { Text("We couldn't read an ID number from the photo. Type it if you can — the conductor verifies the ID either way.") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                cameraOpen -> IdCaptureCamera(
                    onCaptured = onCaptured,
                    onError = onCaptureError,
                    onClose = onCancelCapture,
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> Button(
                    onClick = onStartCapture,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Capture ID", fontWeight = FontWeight.Bold)
                }
            }
            captureError?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Downsampled preview of the captured ID; decoding happens off the main thread. */
@Composable
private fun IdThumbnail(imagePath: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imagePath, bounds)
                val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 240)
                BitmapFactory.decodeFile(imagePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Captured ID photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CoPassengerCard(
    index: Int,
    seatLabel: String,
    seatNoun: String,
    form: CoPassengerForm,
    cameraOpen: Boolean,
    captureError: String?,
    onChanged: ((CoPassengerForm) -> CoPassengerForm) -> Unit,
    onTypeSelected: (PassengerType) -> Unit,
    onStartCapture: () -> Unit,
    onRetake: () -> Unit,
    onCaptured: (path: String, ocrText: String) -> Unit,
    onCaptureError: (String) -> Unit,
    onCancelCapture: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Passenger ${index + 2}" +
                    (if (form.isChild) " • Child (2–11)" else "") +
                    (if (seatLabel.isNotBlank()) " • $seatNoun $seatLabel" else ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (form.isChild) {
                Text(
                    "Children take a seat and pay the full fare.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NameField(
                    value = form.firstName,
                    onValueChange = { value -> onChanged { it.copy(firstName = value) } },
                    label = "First name",
                    error = form.firstName.takeIf { it.isNotEmpty() }?.let { form.firstNameError },
                    modifier = Modifier.weight(1f),
                )
                NameField(
                    value = form.lastName,
                    onValueChange = { value -> onChanged { it.copy(lastName = value) } },
                    label = "Last name",
                    error = form.lastName.takeIf { it.isNotEmpty() }?.let { form.lastNameError },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            MobileField(
                value = form.mobileNumber,
                onValueChange = { value -> onChanged { it.copy(mobileNumber = value) } },
                label = "Mobile number (optional)",
                error = form.mobileNumber.takeIf { it.isNotEmpty() }?.let { form.mobileError },
                imeAction = ImeAction.Done,
            )

            Spacer(modifier = Modifier.height(10.dp))
            PassengerTypeChips(form.type, onTypeSelected)

            AnimatedVisibility(visible = form.type.requiresIdCapture) {
                IdCaptureSection(
                    typeName = form.type.name,
                    imagePath = form.discountIdImagePath.takeIf { form.idCaptured },
                    idNumber = form.idNumber,
                    ocrLooksLikeId = form.ocrLooksLikeId,
                    cameraOpen = cameraOpen,
                    captureError = captureError,
                    onStartCapture = onStartCapture,
                    onRetake = onRetake,
                    onCaptured = onCaptured,
                    onCaptureError = onCaptureError,
                    onCancelCapture = onCancelCapture,
                    onIdNumberChanged = { value -> onChanged { it.copy(idNumber = value) } },
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodList(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaymentMethodCard(
            method = PaymentMethod.GCASH,
            title = "GCash",
            subtitle = "Fast & secure mobile wallet",
            icon = Icons.Filled.PhoneAndroid,
            badge = "Popular",
            selected = selected == PaymentMethod.GCASH,
            onSelect = onSelect,
        )
        PaymentMethodCard(
            method = PaymentMethod.QR_PH,
            title = "QR Ph / InstaPay",
            subtitle = "Scan with any local banking app",
            icon = Icons.Filled.QrCode,
            selected = selected == PaymentMethod.QR_PH,
            onSelect = onSelect,
        )
        PaymentMethodCard(
            method = PaymentMethod.CARD,
            title = "Credit / Debit Card",
            subtitle = "Visa, Mastercard, JCB",
            icon = Icons.Filled.CreditCard,
            selected = selected == PaymentMethod.CARD,
            onSelect = onSelect,
        )
        PaymentMethodCard(
            method = PaymentMethod.CASH_ON_BOARD,
            title = "Cash on Board",
            subtitle = "Pay the conductor or port staff; your seat is still reserved",
            icon = Icons.Filled.AccountBalanceWallet,
            selected = selected == PaymentMethod.CASH_ON_BOARD,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun PaymentMethodCard(
    method: PaymentMethod,
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: (PaymentMethod) -> Unit,
    badge: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(method) }),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = "$title icon",
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    badge?.let {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The row is the selectable; a second click target would double up in TalkBack.
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun FareBreakdown(uiState: CheckoutUiState) {
    val quote: FareQuote = uiState.quote ?: return
    val outbound = quote.legs.firstOrNull() ?: return
    val returnLeg = quote.legs.getOrNull(1)
    val noun = uiState.rideKind.spaceNoun(plural = outbound.seatCount != 1)
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (returnLeg != null) {
                FareRow(
                    "Outbound • ${uiState.trip?.operatorName.orEmpty()} " +
                        "(${formatPhp(outbound.farePerSeatPhp, showCentavos = false)} × ${outbound.seatCount})",
                    formatPhp(outbound.subtotalPhp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                FareRow(
                    "Return • ${uiState.returnTrip?.operatorName.orEmpty()} " +
                        "(${formatPhp(returnLeg.farePerSeatPhp, showCentavos = false)} × ${returnLeg.seatCount})",
                    formatPhp(returnLeg.subtotalPhp),
                )
            } else {
                FareRow("Base fare (${outbound.seatCount} $noun)", formatPhp(quote.subtotalPhp))
            }
            if (uiState.infantCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                FareRow(
                    "Infant${if (uiState.infantCount == 1) "" else "s"} ×${uiState.infantCount} (on lap)",
                    "FREE",
                )
            }
            if (quote.discountPhp > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                FareRow(
                    "Fare discounts (${uiState.discountedPassengerCount} passenger" +
                        "${if (uiState.discountedPassengerCount == 1) "" else "s"} × 20%" +
                        "${if (returnLeg != null) ", both trips" else ""})",
                    formatPhp(-quote.discountPhp),
                )
            }
            if (uiState.pendingDiscountCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "20% off after ID capture for ${uiState.pendingDiscountCount} passenger" +
                        if (uiState.pendingDiscountCount == 1) "" else "s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    formatPhp(quote.totalPhp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FareRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatDeparture(epochMillis: Long): String = PhTime.formatDateTime(epochMillis, "MMM d, hh:mm a")
