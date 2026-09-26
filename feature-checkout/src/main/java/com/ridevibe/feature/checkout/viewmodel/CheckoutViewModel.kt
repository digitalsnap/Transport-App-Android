package com.ridevibe.feature.checkout.viewmodel

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.FareCalculator
import com.ridevibe.core.domain.model.FareQuote
import com.ridevibe.core.domain.model.PartyRules
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.spaceNoun
import com.ridevibe.core.domain.repository.ProfileRepository
import com.ridevibe.core.domain.session.BookingCart
import com.ridevibe.core.domain.usecase.ConfirmBookingUseCase
import com.ridevibe.core.domain.usecase.GetTripUseCase
import com.ridevibe.core.domain.usecase.newClientReference
import com.ridevibe.feature.checkout.ocr.DiscountIdImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Form state for one additional passenger (seat 2..N). */
data class CoPassengerForm(
    val firstName: String = "",
    val lastName: String = "",
    val mobileNumber: String = "", // optional
    val type: PassengerType = PassengerType.REGULAR,
    val discountIdImagePath: String? = null,
    /** OCR's best guess at the ID number; the rider can correct it. */
    val idNumber: String = "",
    /** False when OCR found nothing that looks like an ID number — a warning, not a block. */
    val ocrLooksLikeId: Boolean = true,
    /** Seats past the party's adult count belong to children (2–11), who pay full fare today. */
    val isChild: Boolean = false,
) {
    val firstNameError: String? get() = CheckoutValidation.nameError(firstName, "First name")
    val lastNameError: String? get() = CheckoutValidation.nameError(lastName, "Last name")
    val mobileError: String? get() = CheckoutValidation.mobileError(mobileNumber, required = false)

    /** A discount claim needs its photo on disk, not just a remembered path. */
    val idCaptured: Boolean get() = !discountIdImagePath.isNullOrBlank() && File(discountIdImagePath).isFile

    /** What the fare is computed with: the discount only applies once the ID is captured. */
    val effectiveType: PassengerType get() = if (type.requiresIdCapture && !idCaptured) PassengerType.REGULAR else type

    val firstBlockingReason: String?
        get() = firstNameError ?: lastNameError ?: mobileError
            ?: if (type.requiresIdCapture && !idCaptured) "ID photo needed for the discount" else null

    val isComplete: Boolean get() = firstBlockingReason == null
}

/** The outbound leg booked but the return leg did not: the rider decides what happens next. */
data class PartialFailure(
    val outboundTicketId: String,
    val message: String,
)

data class CheckoutUiState(
    val trip: Trip? = null,
    val seatIds: List<String> = emptyList(),
    /** Round trip only: the return leg collected by the booking cart. */
    val returnTrip: Trip? = null,
    val returnSeatIds: List<String> = emptyList(),
    /** True when the cart holds a return leg that MUST load before paying —
     *  prevents silently charging for the outbound leg alone. */
    val expectsReturnLeg: Boolean = false,
    /** True while the trip (and return trip) load; the form is not shown before then. */
    val isLoading: Boolean = true,
    /** Trip load failed (or the route arguments were missing); Retry re-runs the load. */
    val loadError: String? = null,
    val adults: Int = 1,
    val children: Int = 0,
    val infantCount: Int = 0,
    /** True when the account owner is the primary traveler. */
    val bookingForSelf: Boolean = true,
    /** True once the profile lookup finished, so the "no name saved" hint is not shown early. */
    val profileLoaded: Boolean = false,
    /** Account owner's saved profile name (blank when the profile is unset). */
    val accountName: String = "",
    val primaryFirstName: String = "",
    val primaryLastName: String = "",
    val primaryEmail: String = "",
    val primaryMobile: String = "",
    val passengerType: PassengerType = PassengerType.REGULAR,
    val discountIdImagePath: String? = null,
    val primaryIdNumber: String = "",
    val primaryOcrLooksLikeId: Boolean = true,
    val coPassengers: List<CoPassengerForm> = emptyList(),
    /** null = no camera open; -1 = primary; 0..n-1 = co-passenger index. */
    val capturingForIndex: Int? = null,
    /** The last camera/gallery failure, shown next to the capture control. */
    val captureError: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.GCASH,
    val promoCode: String = "",
    /** Seat holds from the cart (min of both legs); null for sea passage, which holds nothing. */
    val holdExpiresAtEpochMillis: Long? = null,
    val holdSecondsRemaining: Long? = null,
    val holdExpired: Boolean = false,
    /** The explicit demo gate for online methods: no PSP is connected yet. */
    val showPaymentDemoSheet: Boolean = false,
    val isSubmitting: Boolean = false,
    /** CSV of confirmed ticket ids (two entries for a round trip). */
    val confirmedTicketIds: String? = null,
    val partialFailure: PartialFailure? = null,
    val errorMessage: String? = null,
) {
    val isRoundTrip: Boolean get() = returnTrip != null
    val rideKind: RideKind get() = trip?.rideKind ?: RideKind.BUS
    val requiresIdCapture: Boolean get() = passengerType.requiresIdCapture
    val seatCount: Int get() = seatIds.size

    val primaryFirstNameError: String? get() = CheckoutValidation.nameError(primaryFirstName, "First name")
    val primaryLastNameError: String? get() = CheckoutValidation.nameError(primaryLastName, "Last name")

    /** Booking for someone else: we must be able to reach the traveller, so both are required. */
    val primaryEmailError: String? get() = CheckoutValidation.emailError(primaryEmail, required = !bookingForSelf)
    val primaryMobileError: String? get() = CheckoutValidation.mobileError(primaryMobile, required = !bookingForSelf)

    val primaryFullName: String get() = "${primaryFirstName.trim()} ${primaryLastName.trim()}".trim()
    val primaryIdCaptured: Boolean
        get() = !discountIdImagePath.isNullOrBlank() && File(discountIdImagePath).isFile
    val primaryEffectiveType: PassengerType
        get() = if (requiresIdCapture && !primaryIdCaptured) PassengerType.REGULAR else passengerType

    /** Blank profile name on a self booking: nudge the rider to save it once under Profile. */
    val showProfileHint: Boolean get() = bookingForSelf && profileLoaded && accountName.isBlank()

    val partyError: String? get() = PartyRules.validate(adults, children, infantCount)?.message

    /** One entry per seat, with discounts applied only where the ID is already captured. */
    val effectivePassengerTypes: List<PassengerType>
        get() = listOf(primaryEffectiveType) + coPassengers.map { it.effectiveType }

    /** Discount claims still waiting on a photo; the total shows full fare for them. */
    val pendingDiscountCount: Int
        get() = (if (requiresIdCapture && !primaryIdCaptured) 1 else 0) +
            coPassengers.count { it.type.requiresIdCapture && !it.idCaptured }

    val discountedPassengerCount: Int
        get() = effectivePassengerTypes.count { it.requiresIdCapture }

    val quote: FareQuote?
        get() = trip?.let { FareCalculator.quote(it.farePhp, effectivePassengerTypes, returnTrip?.farePhp) }

    val totalPhp: Double get() = quote?.totalPhp ?: 0.0

    /** The first thing stopping "Pay", in the order the rider sees the form; null when bookable. */
    val firstBlockingReason: String?
        get() = when {
            trip == null -> "Trip details are still loading"
            expectsReturnLeg && returnTrip == null -> "Return trip is still loading"
            seatIds.isEmpty() -> "No ${rideKind.spaceNoun(plural = true)} selected"
            isRoundTrip && returnSeatIds.size != seatIds.size ->
                "Return leg has ${returnSeatIds.size} ${rideKind.spaceNoun(plural = returnSeatIds.size != 1)} " +
                    "but the outbound has ${seatIds.size}. Pick the return ${rideKind.spaceNoun(plural = true)} again."
            partyError != null -> partyError
            holdExpired -> "Your seat hold expired"
            primaryFirstNameError != null -> "Primary passenger: $primaryFirstNameError"
            primaryLastNameError != null -> "Primary passenger: $primaryLastNameError"
            primaryEmailError != null -> "Primary passenger: $primaryEmailError"
            primaryMobileError != null -> "Primary passenger: $primaryMobileError"
            requiresIdCapture && !primaryIdCaptured -> "Primary passenger: ID photo needed for the discount"
            else -> coPassengers.withIndex()
                .firstNotNullOfOrNull { (index, form) -> form.firstBlockingReason?.let { "Passenger ${index + 2}: $it" } }
        }

    val canSubmit: Boolean get() = firstBlockingReason == null
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getTripUseCase: GetTripUseCase,
    private val profileRepository: ProfileRepository,
    private val confirmBookingUseCase: ConfirmBookingUseCase,
    private val bookingCart: BookingCart,
    private val discountIdImages: DiscountIdImageStore,
) : ViewModel() {

    private val routeTripId: String? = savedStateHandle.get<String>("tripId")?.takeIf { it.isNotBlank() }
    private val routeSeatIds: List<String> =
        savedStateHandle.get<String>("seats")?.split(",")?.filter { it.isNotBlank() }.orEmpty()
    private val routeInfants: Int? = savedStateHandle.get<String>("infants")?.toIntOrNull()
    private val routeForSelf: Boolean? = savedStateHandle.get<String>("forSelf")?.toBooleanStrictOrNull()

    private val cart = bookingCart.state.value

    /** The outbound trip: the route names it; the cart confirms which seats were held for it. */
    private val tripId: String? = routeTripId ?: cart.outboundTripId
    private val seatIds: List<String> =
        if (cart.outboundTripId == tripId && cart.outboundSeatIds.isNotEmpty()) cart.outboundSeatIds else routeSeatIds
    private val returnTripId: String? = cart.returnTripId.takeIf { cart.isRoundTrip }

    /**
     * Idempotency keys, one per leg (the server dedupes on the key, so the two
     * legs can never share one). Generated once per checkout attempt, kept
     * across process death, reused on retry, and replaced only after a
     * confirmed success or when the rider changes the party after an attempt.
     */
    private var clientReference: String = savedStateHandle[KEY_CLIENT_REF] ?: newClientReference().also {
        savedStateHandle[KEY_CLIENT_REF] = it
    }
    private var returnClientReference: String = savedStateHandle[KEY_RETURN_CLIENT_REF] ?: newClientReference().also {
        savedStateHandle[KEY_RETURN_CLIENT_REF] = it
    }
    private var attemptedWithCurrentKeys: Boolean = savedStateHandle[KEY_ATTEMPTED] ?: false
        set(value) {
            field = value
            savedStateHandle[KEY_ATTEMPTED] = value
        }

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private var holdJob: Job? = null
    private var profileApplied = false

    init {
        // Photos referenced by a restored form are still needed; anything else is an orphan.
        discountIdImages.sweepInBackground(keep = _uiState.value.allIdPaths())
        loadTrips()
        loadProfile()
        startHoldCountdown(_uiState.value.holdExpiresAtEpochMillis)
    }

    private fun initialState(): CheckoutUiState {
        val adults = cart.adults.coerceAtLeast(1)
        val infants = routeInfants ?: cart.infants
        val forSelf = routeForSelf ?: cart.forSelf
        val holdExpiresAt = if (cart.rideKind?.sellsPassage == true) {
            null
        } else {
            listOfNotNull(cart.outboundHoldExpiresAtEpochMillis, cart.returnHoldExpiresAtEpochMillis).minOrNull()
        }
        val base = CheckoutUiState(
            seatIds = seatIds,
            returnSeatIds = if (returnTripId != null) cart.returnSeatIds else emptyList(),
            expectsReturnLeg = returnTripId != null,
            adults = adults,
            children = cart.children,
            infantCount = infants,
            bookingForSelf = forSelf,
            // Seat index 0 is the primary passenger, always an adult (PartyRules needs one).
            coPassengers = List((seatIds.size - 1).coerceAtLeast(0)) { index ->
                CoPassengerForm(isChild = index + 1 >= adults)
            },
            holdExpiresAtEpochMillis = holdExpiresAt,
            loadError = if (tripId == null) "Missing booking details. Go back and pick the trip again." else null,
            isLoading = tripId != null,
        )
        val restored = savedStateHandle.get<Bundle>(KEY_FORM)?.let { base.restoreForm(it) } ?: return base
        // The cache can be cleared behind our back; a path with no file is no capture.
        return restored.dropMissingIdFiles()
    }

    fun loadTrips() {
        val outboundId = tripId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            val outbound = getTripUseCase(outboundId).getOrElse { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, loadError = throwable.message ?: "Unable to load trip details")
                }
                return@launch
            }
            val returnTrip = returnTripId?.let { id ->
                getTripUseCase(id).getOrElse { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, loadError = throwable.message ?: "Unable to load return trip")
                    }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    trip = outbound,
                    returnTrip = returnTrip,
                    isLoading = false,
                    // Sea passage holds nothing, whatever a stale cart says.
                    holdExpiresAtEpochMillis = if (outbound.rideKind.sellsPassage) null else it.holdExpiresAtEpochMillis,
                )
            }
            if (outbound.rideKind.sellsPassage) stopHoldCountdown()
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            _uiState.update { state ->
                // A restored form already holds what the rider typed; don't overwrite it with the profile.
                val prefill = state.bookingForSelf && !profileApplied && savedStateHandle.get<Bundle>(KEY_FORM) == null
                profileApplied = true
                val filled = if (prefill && profile != null) {
                    state.copy(
                        primaryFirstName = state.primaryFirstName.ifBlank { profile.firstName.trim() },
                        primaryLastName = state.primaryLastName.ifBlank { profile.lastName.trim() },
                        primaryEmail = state.primaryEmail.ifBlank { profile.email.trim() },
                        primaryMobile = state.primaryMobile.ifBlank { profile.mobileNumber.trim() },
                    )
                } else {
                    state
                }
                filled.copy(profileLoaded = true, accountName = profile?.fullName?.trim().orEmpty())
            }
            persistForm()
        }
    }

    // ---- Passenger form ---------------------------------------------------------------------

    fun onPrimaryFirstNameChanged(value: String) = updateForm { it.copy(primaryFirstName = value) }

    fun onPrimaryLastNameChanged(value: String) = updateForm { it.copy(primaryLastName = value) }

    fun onPrimaryEmailChanged(value: String) = updateForm { it.copy(primaryEmail = value) }

    fun onPrimaryMobileChanged(value: String) = updateForm { it.copy(primaryMobile = value) }

    fun onPrimaryIdNumberChanged(value: String) = updateForm { it.copy(primaryIdNumber = value) }

    fun onPassengerTypeSelected(type: PassengerType) {
        val previousPath = _uiState.value.discountIdImagePath
        updateForm {
            it.copy(
                passengerType = type,
                discountIdImagePath = if (type.requiresIdCapture) it.discountIdImagePath else null,
                primaryIdNumber = if (type.requiresIdCapture) it.primaryIdNumber else "",
                primaryOcrLooksLikeId = if (type.requiresIdCapture) it.primaryOcrLooksLikeId else true,
                capturingForIndex = if (type.requiresIdCapture) it.capturingForIndex else null,
            )
        }
        if (!type.requiresIdCapture) discountIdImages.deleteInBackground(previousPath)
    }

    fun onCoPassengerChanged(index: Int, transform: (CoPassengerForm) -> CoPassengerForm) = updateForm { state ->
        state.copy(
            coPassengers = state.coPassengers.mapIndexed { i, form -> if (i == index) transform(form) else form },
        )
    }

    fun onCoPassengerTypeSelected(index: Int, type: PassengerType) {
        val previousPath = _uiState.value.coPassengers.getOrNull(index)?.discountIdImagePath
        onCoPassengerChanged(index) {
            it.copy(
                type = type,
                discountIdImagePath = if (type.requiresIdCapture) it.discountIdImagePath else null,
                idNumber = if (type.requiresIdCapture) it.idNumber else "",
                ocrLooksLikeId = if (type.requiresIdCapture) it.ocrLooksLikeId else true,
            )
        }
        if (!type.requiresIdCapture) discountIdImages.deleteInBackground(previousPath)
    }

    // ---- ID capture -------------------------------------------------------------------------

    /** Opens the ID camera for one passenger at a time (-1 = primary). */
    fun onStartCapture(forIndex: Int) = _uiState.update { it.copy(capturingForIndex = forIndex, captureError = null) }

    fun onCancelCapture() = _uiState.update { it.copy(capturingForIndex = null) }

    fun onCaptureFailed(message: String) = _uiState.update { it.copy(captureError = message) }

    /** Retake: drops the photo (and its file) so the camera opens again for that passenger. */
    fun onRetake(forIndex: Int) {
        val previous = if (forIndex == -1) {
            _uiState.value.discountIdImagePath
        } else {
            _uiState.value.coPassengers.getOrNull(forIndex)?.discountIdImagePath
        }
        updateForm { state ->
            if (forIndex == -1) {
                state.copy(discountIdImagePath = null, primaryIdNumber = "", primaryOcrLooksLikeId = true, capturingForIndex = -1)
            } else {
                state.copy(
                    coPassengers = state.coPassengers.mapIndexed { i, form ->
                        if (i == forIndex) form.copy(discountIdImagePath = null, idNumber = "", ocrLooksLikeId = true) else form
                    },
                    capturingForIndex = forIndex,
                )
            }
        }
        discountIdImages.deleteInBackground(previous)
    }

    fun onIdCaptured(index: Int, imagePath: String, ocrText: String) {
        if (imagePath.isBlank() || !File(imagePath).isFile) {
            onCaptureFailed("The photo wasn't saved. Try again.")
            return
        }
        val idNumber = CheckoutValidation.extractIdNumber(ocrText)
        var replaced: String? = null
        updateForm { state ->
            when (index) {
                -1 -> {
                    replaced = state.discountIdImagePath
                    state.copy(
                        discountIdImagePath = imagePath,
                        primaryIdNumber = idNumber.orEmpty(),
                        primaryOcrLooksLikeId = idNumber != null,
                        capturingForIndex = null,
                        captureError = null,
                    )
                }
                else -> state.copy(
                    coPassengers = state.coPassengers.mapIndexed { i, form ->
                        if (i == index) {
                            replaced = form.discountIdImagePath
                            form.copy(
                                discountIdImagePath = imagePath,
                                idNumber = idNumber.orEmpty(),
                                ocrLooksLikeId = idNumber != null,
                            )
                        } else {
                            form
                        }
                    },
                    capturingForIndex = null,
                    captureError = null,
                )
            }
        }
        // A retake orphans the previous photo — remove it now rather than at exit.
        if (replaced != imagePath) discountIdImages.deleteInBackground(replaced)
    }

    // ---- Payment ----------------------------------------------------------------------------

    fun onPaymentMethodSelected(method: PaymentMethod) = updateForm { it.copy(paymentMethod = method) }

    fun onPromoCodeChanged(code: String) = updateForm { it.copy(promoCode = code) }

    /**
     * The pay button. Cash on board reserves straight away; the online methods
     * pass through the demo gate first because no payment provider is wired.
     */
    fun onPayClicked() {
        val state = _uiState.value
        if (!state.canSubmit || state.isSubmitting || state.holdExpired) return
        if (state.paymentMethod == PaymentMethod.CASH_ON_BOARD) {
            confirmBooking()
        } else {
            _uiState.update { it.copy(showPaymentDemoSheet = true) }
        }
    }

    fun onPaymentDemoDismissed() = _uiState.update { it.copy(showPaymentDemoSheet = false) }

    fun onPaymentDemoConfirmed() {
        _uiState.update { it.copy(showPaymentDemoSheet = false) }
        confirmBooking()
    }

    fun onErrorDismissed() = _uiState.update { it.copy(errorMessage = null) }

    // ---- Submission -------------------------------------------------------------------------

    fun confirmBooking() {
        val state = _uiState.value.dropMissingIdFiles()
        _uiState.value = state
        if (!state.canSubmit || state.isSubmitting || state.holdExpired) {
            state.firstBlockingReason?.let { reason -> _uiState.update { it.copy(errorMessage = reason) } }
            return
        }
        val trip = state.trip ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            attemptedWithCurrentKeys = true
            val primary = state.primaryPassenger()
            val coPassengers = state.coPassengerModels()
            val outboundTicket = confirmBookingUseCase(
                tripId = trip.id,
                seatIds = state.seatIds,
                primaryPassenger = primary,
                coPassengers = coPassengers,
                infantCount = state.infantCount,
                paymentMethod = state.paymentMethod,
                promoCode = state.promoCode.trim().ifBlank { null },
                clientReference = clientReference,
            ).getOrElse { throwable ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = throwable.message ?: "Booking failed") }
                return@launch
            }
            // The seats are ours now; the hold no longer matters.
            stopHoldCountdown()

            val returnTrip = state.returnTrip
            if (returnTrip == null) {
                finishSuccess(listOf(outboundTicket.id))
            } else {
                bookReturnLeg(outboundTicket.id, returnTrip, state, primary, coPassengers)
            }
        }
    }

    /** Partial failure: try the return leg again with the same idempotency key. */
    fun retryReturnLeg() {
        val state = _uiState.value
        val failure = state.partialFailure ?: return
        val returnTrip = state.returnTrip ?: return
        if (state.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            bookReturnLeg(failure.outboundTicketId, returnTrip, state, state.primaryPassenger(), state.coPassengerModels())
        }
    }

    /** Partial failure: keep the outbound ticket and stop trying the return leg. */
    fun continueWithOutboundOnly() {
        val failure = _uiState.value.partialFailure ?: return
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch { finishSuccess(listOf(failure.outboundTicketId)) }
    }

    private suspend fun bookReturnLeg(
        outboundTicketId: String,
        returnTrip: Trip,
        state: CheckoutUiState,
        primary: Passenger,
        coPassengers: List<CoPassenger>,
    ) {
        confirmBookingUseCase(
            tripId = returnTrip.id,
            seatIds = state.returnSeatIds,
            primaryPassenger = primary,
            coPassengers = coPassengers,
            infantCount = state.infantCount,
            paymentMethod = state.paymentMethod,
            promoCode = state.promoCode.trim().ifBlank { null },
            clientReference = returnClientReference,
        )
            .onSuccess { returnTicket -> finishSuccess(listOf(outboundTicketId, returnTicket.id)) }
            .onFailure { throwable ->
                // The photos stay: a retry is validated against them. Nothing is swept here.
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        partialFailure = PartialFailure(
                            outboundTicketId = outboundTicketId,
                            message = throwable.message ?: "The return leg could not be booked.",
                        ),
                    )
                }
            }
    }

    /** Only here — full success or the explicit outbound-only path — does the cart reset and the photos go. */
    private suspend fun finishSuccess(ticketIds: List<String>) {
        stopHoldCountdown()
        bookingCart.reset()
        discountIdImages.sweep()
        savedStateHandle.remove<Bundle>(KEY_FORM)
        rotateClientReferences()
        _uiState.update {
            it.copy(
                isSubmitting = false,
                partialFailure = null,
                errorMessage = null,
                confirmedTicketIds = ticketIds.joinToString(","),
            )
        }
    }

    private fun rotateClientReferences() {
        clientReference = newClientReference().also { savedStateHandle[KEY_CLIENT_REF] = it }
        returnClientReference = newClientReference().also { savedStateHandle[KEY_RETURN_CLIENT_REF] = it }
        attemptedWithCurrentKeys = false
    }

    // ---- Hold countdown ---------------------------------------------------------------------

    private fun startHoldCountdown(expiresAt: Long?) {
        holdJob?.cancel()
        if (expiresAt == null) return
        holdJob = viewModelScope.launch {
            while (isActive) {
                val remaining = ((expiresAt - System.currentTimeMillis()) / 1_000).coerceAtLeast(0)
                _uiState.update { it.copy(holdSecondsRemaining = remaining, holdExpired = remaining == 0L) }
                if (remaining == 0L) break
                delay(1_000)
            }
        }
    }

    private fun stopHoldCountdown() {
        holdJob?.cancel()
        holdJob = null
        _uiState.update { it.copy(holdExpiresAtEpochMillis = null, holdSecondsRemaining = null, holdExpired = false) }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    /**
     * Every user edit goes through here: it lands in the state, in the saved
     * form, and — if a booking attempt already used the current idempotency
     * keys — rotates them, since the party the server saw is no longer the
     * party being booked.
     */
    private fun updateForm(transform: (CheckoutUiState) -> CheckoutUiState) {
        _uiState.update(transform)
        if (attemptedWithCurrentKeys && _uiState.value.partialFailure == null) rotateClientReferences()
        persistForm()
    }

    private fun persistForm() {
        savedStateHandle[KEY_FORM] = _uiState.value.toFormBundle()
    }

    private fun CheckoutUiState.primaryPassenger() = Passenger(
        fullName = primaryFullName,
        type = passengerType,
        discountIdImagePath = discountIdImagePath,
    )

    private fun CheckoutUiState.coPassengerModels() = coPassengers.map {
        CoPassenger(
            firstName = it.firstName.trim(),
            lastName = it.lastName.trim(),
            mobileNumber = it.mobileNumber.trim().takeIf { number -> number.isNotBlank() },
            type = it.type,
            discountIdImagePath = it.discountIdImagePath,
        )
    }

    private fun CheckoutUiState.allIdPaths(): List<String> =
        listOfNotNull(discountIdImagePath) + coPassengers.mapNotNull { it.discountIdImagePath }

    private fun CheckoutUiState.dropMissingIdFiles(): CheckoutUiState = copy(
        discountIdImagePath = discountIdImagePath?.takeIf { discountIdImages.exists(it) },
        coPassengers = coPassengers.map { form ->
            form.copy(discountIdImagePath = form.discountIdImagePath?.takeIf { discountIdImages.exists(it) })
        },
    )

    override fun onCleared() {
        holdJob?.cancel()
        // Checkout abandoned (or finished) — the photos must not outlive it. A
        // submission still in flight keeps them: its validation reads the files.
        if (!_uiState.value.isSubmitting) discountIdImages.sweepInBackground()
        super.onCleared()
    }

    // ---- SavedStateHandle form persistence --------------------------------------------------
    // Bundles rather than a Parcelable: this module has no parcelize plugin, and
    // the form is flat strings, enums and one list.

    private fun CheckoutUiState.toFormBundle(): Bundle = Bundle().apply {
        putString("primaryFirstName", primaryFirstName)
        putString("primaryLastName", primaryLastName)
        putString("primaryEmail", primaryEmail)
        putString("primaryMobile", primaryMobile)
        putString("passengerType", passengerType.name)
        putString("discountIdImagePath", discountIdImagePath)
        putString("primaryIdNumber", primaryIdNumber)
        putBoolean("primaryOcrLooksLikeId", primaryOcrLooksLikeId)
        putString("paymentMethod", paymentMethod.name)
        putString("promoCode", promoCode)
        putStringArrayList("coFirstNames", ArrayList(coPassengers.map { it.firstName }))
        putStringArrayList("coLastNames", ArrayList(coPassengers.map { it.lastName }))
        putStringArrayList("coMobiles", ArrayList(coPassengers.map { it.mobileNumber }))
        putStringArrayList("coTypes", ArrayList(coPassengers.map { it.type.name }))
        putStringArrayList("coIdPaths", ArrayList(coPassengers.map { it.discountIdImagePath.orEmpty() }))
        putStringArrayList("coIdNumbers", ArrayList(coPassengers.map { it.idNumber }))
        putBooleanArray("coOcrLooksLikeId", coPassengers.map { it.ocrLooksLikeId }.toBooleanArray())
    }

    private fun CheckoutUiState.restoreForm(bundle: Bundle): CheckoutUiState {
        val coFirst = bundle.getStringArrayList("coFirstNames").orEmpty()
        val coLast = bundle.getStringArrayList("coLastNames").orEmpty()
        val coMobile = bundle.getStringArrayList("coMobiles").orEmpty()
        val coTypes = bundle.getStringArrayList("coTypes").orEmpty()
        val coPaths = bundle.getStringArrayList("coIdPaths").orEmpty()
        val coIdNumbers = bundle.getStringArrayList("coIdNumbers").orEmpty()
        val coOcr = bundle.getBooleanArray("coOcrLooksLikeId") ?: BooleanArray(0)
        return copy(
            primaryFirstName = bundle.getString("primaryFirstName", primaryFirstName),
            primaryLastName = bundle.getString("primaryLastName", primaryLastName),
            primaryEmail = bundle.getString("primaryEmail", primaryEmail),
            primaryMobile = bundle.getString("primaryMobile", primaryMobile),
            passengerType = bundle.getString("passengerType")?.let(::passengerTypeOrNull) ?: passengerType,
            discountIdImagePath = bundle.getString("discountIdImagePath"),
            primaryIdNumber = bundle.getString("primaryIdNumber", ""),
            primaryOcrLooksLikeId = bundle.getBoolean("primaryOcrLooksLikeId", true),
            paymentMethod = bundle.getString("paymentMethod")
                ?.let { name -> PaymentMethod.entries.firstOrNull { it.name == name } } ?: paymentMethod,
            promoCode = bundle.getString("promoCode", ""),
            // The seat count is authoritative; a saved form for a different party is ignored per seat.
            coPassengers = coPassengers.mapIndexed { index, form ->
                form.copy(
                    firstName = coFirst.getOrNull(index) ?: form.firstName,
                    lastName = coLast.getOrNull(index) ?: form.lastName,
                    mobileNumber = coMobile.getOrNull(index) ?: form.mobileNumber,
                    type = coTypes.getOrNull(index)?.let(::passengerTypeOrNull) ?: form.type,
                    discountIdImagePath = coPaths.getOrNull(index)?.takeIf { it.isNotBlank() },
                    idNumber = coIdNumbers.getOrNull(index) ?: "",
                    ocrLooksLikeId = coOcr.getOrNull(index) ?: true,
                )
            },
        )
    }

    private fun passengerTypeOrNull(name: String): PassengerType? =
        PassengerType.entries.firstOrNull { it.name == name }

    private companion object {
        const val KEY_FORM = "checkoutForm"
        const val KEY_CLIENT_REF = "clientReference"
        const val KEY_RETURN_CLIENT_REF = "returnClientReference"
        const val KEY_ATTEMPTED = "attemptedWithCurrentKeys"
    }
}
