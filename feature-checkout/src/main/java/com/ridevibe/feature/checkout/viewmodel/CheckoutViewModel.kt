package com.ridevibe.feature.checkout.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.usecase.ConfirmBookingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckoutUiState(
    val passengerFullName: String = "",
    val passengerType: PassengerType = PassengerType.REGULAR,
    val discountIdImagePath: String? = null,
    val paymentStatus: PaymentStatus = PaymentStatus.CASH_ON_BOARD,
    val isSubmitting: Boolean = false,
    val confirmedTicket: Ticket? = null,
    val errorMessage: String? = null,
) {
    /** Drives whether the CameraX ID-capture card expands under the passenger type selector. */
    val requiresIdCapture: Boolean get() = passengerType.requiresIdCapture
    val canSubmit: Boolean get() = passengerFullName.isNotBlank() &&
        (!requiresIdCapture || discountIdImagePath != null)
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val confirmBookingUseCase: ConfirmBookingUseCase,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val seatId: String = checkNotNull(savedStateHandle["seatId"])

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    fun onFullNameChanged(name: String) {
        _uiState.update { it.copy(passengerFullName = name) }
    }

    fun onPassengerTypeSelected(type: PassengerType) {
        _uiState.update {
            it.copy(passengerType = type, discountIdImagePath = if (type.requiresIdCapture) it.discountIdImagePath else null)
        }
    }

    fun onIdCaptured(imagePath: String) {
        _uiState.update { it.copy(discountIdImagePath = imagePath) }
    }

    fun onPaymentStatusSelected(status: PaymentStatus) {
        _uiState.update { it.copy(paymentStatus = status) }
    }

    fun confirmBooking() {
        val state = _uiState.value
        if (!state.canSubmit || state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val passenger = Passenger(
                fullName = state.passengerFullName,
                type = state.passengerType,
                discountIdImagePath = state.discountIdImagePath,
            )
            confirmBookingUseCase(tripId, seatId, passenger, state.paymentStatus)
                .onSuccess { ticket -> _uiState.update { it.copy(isSubmitting = false, confirmedTicket = ticket) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = throwable.message ?: "Booking failed") }
                }
        }
    }
}
