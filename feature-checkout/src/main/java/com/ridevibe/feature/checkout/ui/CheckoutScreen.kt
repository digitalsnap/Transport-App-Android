package com.ridevibe.feature.checkout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.feature.checkout.ocr.IdCaptureCamera
import com.ridevibe.feature.checkout.viewmodel.CheckoutViewModel

@Composable
fun CheckoutScreen(
    onBookingConfirmed: (ticketId: String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.confirmedTicket) {
        uiState.confirmedTicket?.let { onBookingConfirmed(it.id) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Checkout") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp).let { PaddingValues(16.dp, padding.calculateTopPadding()) },
        ) {
            item {
                OutlinedTextField(
                    value = uiState.passengerFullName,
                    onValueChange = viewModel::onFullNameChanged,
                    label = { Text("Passenger full name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Passenger type")
                Spacer(modifier = Modifier.height(8.dp))
            }

            item { PassengerTypeSelector(uiState.passengerType, viewModel::onPassengerTypeSelected) }

            item {
                // Expands only for Student/Senior/PWD — regular fares skip ID capture entirely.
                AnimatedVisibility(visible = uiState.requiresIdCapture) {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Snap a photo of your ${uiState.passengerType.name.lowercase().replace('_', ' ')} ID")
                            Spacer(modifier = Modifier.height(8.dp))
                            IdCaptureCamera(
                                onCaptured = { path, _ -> viewModel.onIdCaptured(path) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            uiState.discountIdImagePath?.let {
                                Text("ID captured ✓", modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Payment method")
                Spacer(modifier = Modifier.height(8.dp))
                PaymentMethodSelector(uiState.paymentStatus, viewModel::onPaymentStatusSelected)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::confirmBooking,
                    enabled = uiState.canSubmit && !uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSubmitting) "Confirming..." else "Confirm booking")
                }
                uiState.errorMessage?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PassengerTypeSelector(selected: PassengerType, onSelect: (PassengerType) -> Unit) {
    Column {
        val rows = listOf(
            PassengerType.REGULAR to "Regular",
            PassengerType.STUDENT to "Student",
            PassengerType.SENIOR_CITIZEN to "Senior citizen",
            PassengerType.PWD to "PWD",
        )
        rows.chunked(2).forEach { rowItems ->
            Column {
                rowItems.forEach { (type, label) ->
                    FilterChip(
                        selected = selected == type,
                        onClick = { onSelect(type) },
                        label = { Text(label) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodSelector(selected: PaymentStatus, onSelect: (PaymentStatus) -> Unit) {
    Column {
        FilterChip(
            selected = selected == PaymentStatus.CASH_ON_BOARD,
            onClick = { onSelect(PaymentStatus.CASH_ON_BOARD) },
            label = { Text("Cash on board") },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FilterChip(
            selected = selected == PaymentStatus.PAID,
            onClick = { onSelect(PaymentStatus.PAID) },
            label = { Text("Pay now (e-wallet/card)") },
        )
    }
}
