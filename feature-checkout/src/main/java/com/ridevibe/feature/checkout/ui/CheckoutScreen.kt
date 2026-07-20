package com.ridevibe.feature.checkout.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.feature.checkout.ocr.IdCaptureCamera
import com.ridevibe.feature.checkout.viewmodel.CheckoutUiState
import com.ridevibe.feature.checkout.viewmodel.CheckoutViewModel
import com.ridevibe.feature.checkout.viewmodel.CoPassengerForm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Checkout screen per the Visily design (page 5). */
@Composable
fun CheckoutScreen(
    onClose: () -> Unit,
    onBookingConfirmed: (ticketId: String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.confirmedTicketIds) {
        uiState.confirmedTicketIds?.let(onBookingConfirmed)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Checkout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to seat selection")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close checkout")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Button(
                    onClick = viewModel::confirmBooking,
                    enabled = uiState.canSubmit && !uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    val label = when {
                        uiState.isSubmitting -> "Processing…"
                        uiState.paymentMethod == PaymentMethod.CASH_ON_BOARD ->
                            "Reserve • ₱%,.2f".format(uiState.totalPhp)
                        uiState.isRoundTrip -> "Pay Now (2 trips) • ₱%,.2f".format(uiState.totalPhp)
                        else -> "Pay Now • ₱%,.2f".format(uiState.totalPhp)
                    }
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            OrderSummaryCard(uiState)

            SectionTitle("Primary passenger${if (uiState.seatCount > 1) " • Seat ${uiState.seatIds.firstOrNull()}" else ""}")

            if (uiState.useAccountAsPrimary) {
                // Account owner is traveling — identity comes from their profile.
                AccountPassengerCard(name = uiState.accountName, email = uiState.accountEmail)
            } else {
                if (uiState.bookingForSelf) {
                    Text(
                        "No name saved on your profile yet — enter it here, or save it once " +
                            "under Profile to skip this next time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.primaryFirstName,
                        onValueChange = viewModel::onPrimaryFirstNameChanged,
                        label = { Text("First name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.primaryLastName,
                        onValueChange = viewModel::onPrimaryLastNameChanged,
                        label = { Text("Last name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            PassengerTypeChips(uiState.passengerType, viewModel::onPassengerTypeSelected)

            AnimatedVisibility(visible = uiState.requiresIdCapture) {
                IdCaptureSection(
                    typeName = uiState.passengerType.name,
                    captured = uiState.discountIdImagePath != null,
                    cameraOpen = uiState.capturingForIndex == -1,
                    onStartCapture = { viewModel.onStartCapture(-1) },
                    onCaptured = viewModel::onIdCaptured,
                )
            }

            uiState.coPassengers.forEachIndexed { index, form ->
                CoPassengerCard(
                    index = index,
                    seatLabel = uiState.seatIds.getOrNull(index + 1) ?: "",
                    form = form,
                    cameraOpen = uiState.capturingForIndex == index,
                    onChanged = { transform -> viewModel.onCoPassengerChanged(index, transform) },
                    onTypeSelected = { type -> viewModel.onCoPassengerTypeSelected(index, type) },
                    onStartCapture = { viewModel.onStartCapture(index) },
                    onCaptured = viewModel::onIdCaptured,
                )
            }

            if (uiState.infantCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "${uiState.infantCount} lap-held infant${if (uiState.infantCount == 1) "" else "s"} " +
                            "included — free of charge, no seat assigned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            SectionTitle("Promo Code")
            OutlinedTextField(
                value = uiState.promoCode,
                onValueChange = viewModel::onPromoCodeChanged,
                placeholder = { Text("Enter coupon code") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    TextButton(onClick = { /* promo validated server-side at booking time */ }) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Select Payment Method")
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
private fun OrderSummaryCard(uiState: CheckoutUiState) {
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
                uiState.trip?.let { trip ->
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                        Text(
                            "${trip.busClass.name.lowercase().replaceFirstChar { it.uppercase() }} Bus",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "From",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        uiState.trip?.origin ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "To",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        uiState.trip?.destination ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        if (uiState.seatCount == 1) "SEAT" else "SEATS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(uiState.seatIds.joinToString(", "), fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "DEPARTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        uiState.trip?.departureEpochMillis?.let(::formatDeparture) ?: "—",
                        fontWeight = FontWeight.SemiBold,
                    )
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
                            "Seat${if (uiState.returnSeatIds.size == 1) "" else "s"} ${uiState.returnSeatIds.joinToString(", ")}",
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

@Composable
private fun PassengerTypeChips(selected: PassengerType, onSelect: (PassengerType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun AccountPassengerCard(name: String, email: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (email.isNotBlank()) "$email • Your RideVibe account" else "Your RideVibe account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** ID capture for a Student/Senior/PWD discount. Only one camera opens at a time. */
@Composable
private fun IdCaptureSection(
    typeName: String,
    captured: Boolean,
    cameraOpen: Boolean,
    onStartCapture: () -> Unit,
    onCaptured: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "A photo of the ${typeName.lowercase().replace('_', ' ')} ID is required " +
                    "for the 20% fare discount.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(10.dp))
            when {
                captured -> Text(
                    "ID captured ✓",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )

                cameraOpen -> IdCaptureCamera(
                    onCaptured = { path, _ -> onCaptured(path) },
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> Button(onClick = onStartCapture, shape = RoundedCornerShape(20.dp)) {
                    Text("Capture ID", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CoPassengerCard(
    index: Int,
    seatLabel: String,
    form: CoPassengerForm,
    cameraOpen: Boolean,
    onChanged: ((CoPassengerForm) -> CoPassengerForm) -> Unit,
    onTypeSelected: (PassengerType) -> Unit,
    onStartCapture: () -> Unit,
    onCaptured: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Passenger ${index + 2}${if (seatLabel.isNotBlank()) " • Seat $seatLabel" else ""}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.firstName,
                    onValueChange = { value -> onChanged { it.copy(firstName = value) } },
                    label = { Text("First name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.lastName,
                    onValueChange = { value -> onChanged { it.copy(lastName = value) } },
                    label = { Text("Last name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = form.mobileNumber,
                onValueChange = { value -> onChanged { it.copy(mobileNumber = value) } },
                label = { Text("Mobile number (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))
            PassengerTypeChips(form.type, onTypeSelected)

            AnimatedVisibility(visible = form.type.requiresIdCapture) {
                IdCaptureSection(
                    typeName = form.type.name,
                    captured = form.discountIdImagePath != null,
                    cameraOpen = cameraOpen,
                    onStartCapture = onStartCapture,
                    onCaptured = onCaptured,
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodList(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            title = "QR PH / Instapay",
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
            subtitle = "Pay directly to conductor",
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
            .selectable(selected = selected, onClick = { onSelect(method) }),
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
                    contentDescription = null,
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
            RadioButton(selected = selected, onClick = { onSelect(method) })
        }
    }
}

@Composable
private fun FareBreakdown(uiState: CheckoutUiState) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (uiState.isRoundTrip) {
                FareRow(
                    "Outbound • ${uiState.trip?.operatorName ?: ""} " +
                        "(₱%,.0f × ${uiState.seatCount})".format(uiState.farePerSeat),
                    "₱%,.2f".format(uiState.outboundBasePhp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                FareRow(
                    "Return • ${uiState.returnTrip?.operatorName ?: ""} " +
                        "(₱%,.0f × ${uiState.returnSeatIds.size})".format(uiState.returnFarePerSeat),
                    "₱%,.2f".format(uiState.returnBasePhp),
                )
            } else {
                FareRow(
                    "Base Fare (${uiState.seatCount} Seat${if (uiState.seatCount == 1) "" else "s"})",
                    "₱%,.2f".format(uiState.baseFarePhp),
                )
            }
            if (uiState.infantCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                FareRow(
                    "Infant${if (uiState.infantCount == 1) "" else "s"} ×${uiState.infantCount} (lap-held)",
                    "FREE",
                )
            }
            if (uiState.discountPhp > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                FareRow(
                    "Fare discounts (${uiState.discountedPassengerCount} passenger" +
                        "${if (uiState.discountedPassengerCount == 1) "" else "s"} × 20%" +
                        "${if (uiState.isRoundTrip) ", both trips" else ""})",
                    "−₱%,.2f".format(uiState.discountPhp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total Amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "₱%,.2f".format(uiState.totalPhp),
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatDeparture(epochMillis: Long): String =
    SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()).format(Date(epochMillis))
