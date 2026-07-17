package com.ridevibe.feature.checkout.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.ProfileRepository
import com.ridevibe.core.domain.usecase.ConfirmBookingUseCase
import com.ridevibe.core.domain.usecase.GetTripUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Form state for one additional passenger (seat 2..N). */
data class CoPassengerForm(
    val firstName: String = "",
    val lastName: String = "",
    val mobileNumber: String = "", // optional
    val type: PassengerType = PassengerType.REGULAR,
    val discountIdImagePath: String? = null,
) {
    val isComplete: Boolean get() = firstName.isNotBlank() && lastName.isNotBlank() &&
        (!type.requiresIdCapture || discountIdImagePath != null)
}

data class CheckoutUiState(
    val trip: Trip? = null,
    val seatIds: List<String> = emptyList(),
    val infantCount: Int = 0,
    /** True when the account owner is the primary traveler. */
    val bookingForSelf: Boolean = true,
    /** Account owner's saved profile name/email (may be blank if profile unset). */
    val accountName: String = "",
    val accountEmail: String = "",
    /** Primary passenger name fields — used when booking for someone else, or as
     *  fallback when booking for self but the profile has no name saved yet. */
    val primaryFirstName: String = "",
    val primaryLastName: String = "",
    val passengerType: PassengerType = PassengerType.REGULAR,
    val discountIdImagePath: String? = null,
    val coPassengers: List<CoPassengerForm> = emptyList(),
    /** null = no camera open; -1 = primary; 0..n-1 = co-passenger index. */
    val capturingForIndex: Int? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.GCASH,
    val promoCode: String = "",
    val isSubmitting: Boolean = false,
    val confirmedTicket: Ticket? = null,
    val errorMessage: String? = null,
) {
    /** Account profile usable as the primary passenger identity. */
    val useAccountAsPrimary: Boolean get() = bookingForSelf && accountName.isNotBlank()

    val primaryFullName: String
        get() = if (useAccountAsPrimary) accountName else "$primaryFirstName $primaryLastName".trim()

    val requiresIdCapture: Boolean get() = passengerType.requiresIdCapture

    val seatCount: Int get() = seatIds.size
    val farePerSeat: Double get() = trip?.farePhp ?: 0.0
    val baseFarePhp: Double get() = farePerSeat * seatCount

    /** Every discounted passenger gets 20% off their own seat. */
    val discountPhp: Double
        get() = farePerSeat * passengerType.discountRate +
            coPassengers.sumOf { farePerSeat * it.type.discountRate }

    val discountedPassengerCount: Int
        get() = (if (passengerType.requiresIdCapture) 1 else 0) +
            coPassengers.count { it.type.requiresIdCapture }

    val totalPhp: Double get() = baseFarePhp - discountPhp

    val canSubmit: Boolean get() = trip != null &&
        (useAccountAsPrimary || (primaryFirstName.isNotBlank() && primaryLastName.isNotBlank())) &&
        (!requiresIdCapture || discountIdImagePath != null) &&
        coPassengers.all { it.isComplete }
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTripUseCase: GetTripUseCase,
    private val profileRepository: ProfileRepository,
    private val confirmBookingUseCase: ConfirmBookingUseCase,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val seatIds: List<String> =
        checkNotNull(savedStateHandle.get<String>("seats")).split(",").filter { it.isNotBlank() }
    private val infantCount: Int = savedStateHandle.get<String>("infants")?.toIntOrNull() ?: 0
    private val bookingForSelf: Boolean =
        savedStateHandle.get<String>("forSelf")?.toBooleanStrictOrNull() ?: true

    private val _uiState = MutableStateFlow(
        CheckoutUiState(
            seatIds = seatIds,
            infantCount = infantCount,
            bookingForSelf = bookingForSelf,
            coPassengers = List((seatIds.size - 1).coerceAtLeast(0)) { CoPassengerForm() },
        ),
    )
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getTripUseCase(tripId)
                .onSuccess { trip -> _uiState.update { it.copy(trip = trip) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(errorMessage = throwable.message ?: "Unable to load trip details") }
                }
        }
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            _uiState.update { it.copy(accountName = profile.fullName.trim(), accountEmail = profile.email.trim()) }
        }
    }

    fun onPrimaryFirstNameChanged(value: String) = _uiState.update { it.copy(primaryFirstName = value) }

    fun onPrimaryLastNameChanged(value: String) = _uiState.update { it.copy(primaryLastName = value) }

    fun onPassengerTypeSelected(type: PassengerType) {
        _uiState.update {
            it.copy(
                passengerType = type,
                discountIdImagePath = if (type.requiresIdCapture) it.discountIdImagePath else null,
                capturingForIndex = if (type.requiresIdCapture) it.capturingForIndex else null,
            )
        }
    }

    fun onCoPassengerChanged(index: Int, transform: (CoPassengerForm) -> CoPassengerForm) {
        _uiState.update { state ->
            state.copy(
                coPassengers = state.coPassengers.mapIndexed { i, form ->
                    if (i == index) transform(form) else form
                },
            )
        }
    }

    fun onCoPassengerTypeSelected(index: Int, type: PassengerType) {
        onCoPassengerChanged(index) {
            it.copy(
                type = type,
                discountIdImagePath = if (type.requiresIdCapture) it.discountIdImagePath else null,
            )
        }
    }

    /** Opens the ID camera for one passenger at a time (-1 = primary). */
    fun onStartCapture(forIndex: Int) = _uiState.update { it.copy(capturingForIndex = forIndex) }

    fun onIdCaptured(imagePath: String) {
        _uiState.update { state ->
            when (val target = state.capturingForIndex) {
                null -> state
                -1 -> state.copy(discountIdImagePath = imagePath, capturingForIndex = null)
                else -> state.copy(
                    coPassengers = state.coPassengers.mapIndexed { i, form ->
                        if (i == target) form.copy(discountIdImagePath = imagePath) else form
                    },
                    capturingForIndex = null,
                )
            }
        }
    }

    fun onPaymentMethodSelected(method: PaymentMethod) = _uiState.update { it.copy(paymentMethod = method) }

    fun onPromoCodeChanged(code: String) = _uiState.update { it.copy(promoCode = code) }

    fun confirmBooking() {
        val state = _uiState.value
        if (!state.canSubmit || state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val primary = Passenger(
                fullName = state.primaryFullName,
                type = state.passengerType,
                discountIdImagePath = state.discountIdImagePath,
            )
            val coPassengers = state.coPassengers.map {
                CoPassenger(
                    firstName = it.firstName.trim(),
                    lastName = it.lastName.trim(),
                    mobileNumber = it.mobileNumber.trim().takeIf { number -> number.isNotBlank() },
                    type = it.type,
                    discountIdImagePath = it.discountIdImagePath,
                )
            }
            confirmBookingUseCase(
                tripId = tripId,
                seatIds = state.seatIds,
                primaryPassenger = primary,
                coPassengers = coPassengers,
                infantCount = state.infantCount,
                paymentMethod = state.paymentMethod,
                promoCode = state.promoCode,
            )
                .onSuccess { ticket -> _uiState.update { it.copy(isSubmitting = false, confirmedTicket = ticket) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = throwable.message ?: "Booking failed")
                    }
                }
        }
    }
}
