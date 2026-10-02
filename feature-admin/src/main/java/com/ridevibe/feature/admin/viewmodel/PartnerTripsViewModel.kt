package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.OnsiteSaleIssued
import com.ridevibe.core.domain.model.OnsiteSaleRequest
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.core.domain.repository.PartnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_WALK_IN_NAME = "Walk-in passenger"

// Limits of OnsiteSaleRequest in docs/api/staff-openapi.yaml: `seatLabels` maxItems / `count` maximum,
// and `passengerFullName` maxLength — the server rejects anything past them with a 400.
private const val ONSITE_SALE_MAX_PASSENGERS = 20
private const val ONSITE_SALE_NAME_MAX_CHARS = 120

data class PartnerTripsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val dateIso: String = PhTime.todayIso(),
    val trips: List<TripOccupancy> = emptyList(),
    /** Service picker for "Add extra trip"; loaded only when the portal is writable. */
    val services: List<OperatorService> = emptyList(),
    val isAddingTrip: Boolean = false,
    val addTripError: String? = null,
    /** Seat sheet. */
    val selectedTrip: TripOccupancy? = null,
    val seats: List<AllocatedSeat> = emptyList(),
    val isSeatsLoading: Boolean = false,
    val seatsError: String? = null,
    /** Seats chosen on the grid for a walk-up sale (bus only). */
    val pickedSeatLabels: Set<String> = emptySet(),
    val inspecting: AllocatedSeat? = null,
    /** Walk-up sale form. */
    val saleName: String = "",
    /** Open-seating passenger count as typed. */
    val saleCountText: String = "1",
    /** One fare type per passenger being sold, in [pickedSeatsOrdered] order on a bus; sized to [passengerCount] on read. */
    val passengerTypes: List<PassengerType> = emptyList(),
    val isSelling: Boolean = false,
    val saleError: String? = null,
    /** Result dialog after a counter sale. */
    val saleIssued: OnsiteSaleIssued? = null,
    val message: String? = null,
) {
    /**
     * The picked seats in grid order (row, then column). This is the one
     * ordering for the fare-type rows, the `seatLabels` sent and the tags in
     * the passenger name, so passenger #2 on screen is passenger #2 on the
     * ticket.
     */
    val pickedSeatsOrdered: List<String>
        get() = seats.sortedWith(compareBy({ it.row }, { it.column })).map { it.label }.filter { it in pickedSeatLabels }

    /** How many people the sale covers: picked seats on a bus, the typed count on open seating. */
    val passengerCount: Int
        get() = when (selectedTrip?.rideKind) {
            null -> 0
            RideKind.BUS -> pickedSeatLabels.size
            else -> saleCountText.toIntOrNull() ?: 0
        }

    /** [passengerTypes] padded with REGULAR (or trimmed) to match [passengerCount]. */
    val effectivePassengerTypes: List<PassengerType>
        get() = List(passengerCount) { index -> passengerTypes.getOrElse(index) { PassengerType.REGULAR } }

    /**
     * The name as sent: discounted fare types ride along as the rider QR's own
     * tags (`Maria Santos (SR+R)`) because the sale request has no field for
     * them yet — see docs/api/PENDING-BACKEND.md.
     */
    val saleNameTagged: String
        get() {
            val name = saleName.trim().ifBlank { DEFAULT_WALK_IN_NAME }
            val types = effectivePassengerTypes
            return if (types.any { it != PassengerType.REGULAR }) "$name (${types.joinToString("+") { it.qrTag() }})" else name
        }

    /** Amount to collect at the counter with the regulated 20% discounts applied per passenger. */
    val saleAmountPhp: Double
        get() {
            val fare = selectedTrip?.farePhp ?: return 0.0
            return effectivePassengerTypes.sumOf { fare * (1 - it.discountRate) }
        }

    /** Highlights the name field; the wording is in [saleValidationError]. */
    val saleNameTooLong: Boolean get() = saleNameTagged.length > ONSITE_SALE_NAME_MAX_CHARS

    /** Contract limits the server would answer 400 to, checked live so the form says so before "Confirm sale". */
    val saleValidationError: String?
        get() {
            val trip = selectedTrip ?: return null
            val isBus = trip.rideKind == RideKind.BUS
            val over = saleNameTagged.length - ONSITE_SALE_NAME_MAX_CHARS
            return when {
                passengerCount > ONSITE_SALE_MAX_PASSENGERS ->
                    "A counter sale covers at most $ONSITE_SALE_MAX_PASSENGERS ${if (isBus) "seats" else "passengers"} — " +
                        "record the rest as a second sale"
                !isBus && passengerCount > trip.available ->
                    "Only ${trip.available} ${if (trip.available == 1) "space is" else "spaces are"} left on this sailing"
                over > 0 ->
                    "Passenger name is $over character${if (over == 1) "" else "s"} over the $ONSITE_SALE_NAME_MAX_CHARS limit" +
                        (if (saleNameTagged != saleName.trim()) " (the fare-type tags count)" else "")
                else -> null
            }
        }

    val canSell: Boolean
        get() = !isSelling && selectedTrip != null && passengerCount > 0 && saleValidationError == null
}

/** The same short tags `MockDatabase.createTicket` puts in a rider QR. */
private fun PassengerType.qrTag(): String = when (this) {
    PassengerType.REGULAR -> "R"
    PassengerType.STUDENT -> "ST"
    PassengerType.SENIOR_CITIZEN -> "SR"
    PassengerType.PWD -> "PWD"
}

/** Partner Trips: departures by PH date, seat maps with passenger names, extra departures and on-site sales. */
@HiltViewModel
class PartnerTripsViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerTripsUiState())
    val uiState: StateFlow<PartnerTripsUiState> = _uiState.asStateFlow()

    private var operatorId: Int? = null
    private var sessionKey: String? = null

    fun start(sessionKey: String, operatorId: Int?, writable: Boolean) {
        if (this.sessionKey == sessionKey && this.operatorId == operatorId) return
        this.sessionKey = sessionKey
        this.operatorId = operatorId
        _uiState.value = PartnerTripsUiState()
        load()
        if (writable) loadServices()
    }

    fun previousDay() = changeDay(-1)

    fun nextDay() = changeDay(+1)

    fun setDate(isoDay: String) {
        if (!PhTime.isValidIso(isoDay)) return
        _uiState.update { it.copy(dateIso = isoDay) }
        load()
    }

    private fun changeDay(delta: Int) {
        _uiState.update { it.copy(dateIso = PhTime.plusDays(it.dateIso, delta)) }
        load()
    }

    fun load() {
        val dateIso = _uiState.value.dateIso
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            partnerRepository.getTrips(dateIso = dateIso, operatorId = operatorId)
                .onSuccess { trips ->
                    _uiState.update { state ->
                        // The open sheet shows the server's counts, never a locally patched copy.
                        val selected = state.selectedTrip?.let { current -> trips.firstOrNull { it.id == current.id } ?: current }
                        state.copy(isLoading = false, trips = trips, selectedTrip = selected)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.staffMessage("Unable to load trips")) }
                }
        }
    }

    private fun loadServices() {
        viewModelScope.launch {
            partnerRepository.getServices(operatorId)
                .onSuccess { services -> _uiState.update { it.copy(services = services) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(addTripError = throwable.staffMessage("Unable to load your services")) }
                }
        }
    }

    /** One-off departure outside the regular hours; the list jumps to that date on success. */
    fun addExtraTrip(serviceId: Int, dateIso: String, timeHm: String) {
        val problem = when {
            _uiState.value.services.none { it.id == serviceId } -> "Choose a service"
            !PhTime.isValidIso(dateIso) -> "Pick a date"
            !PhTime.isValidHm(timeHm) -> "Pick a departure time (24-hour, PH)"
            else -> null
        }
        if (problem != null) {
            _uiState.update { it.copy(addTripError = problem) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTrip = true, addTripError = null) }
            partnerRepository.addExtraTrip(serviceId, dateIso, timeHm)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isAddingTrip = false,
                            dateIso = dateIso,
                            message = "Extra departure added (${created.seats} seats generated)",
                        )
                    }
                    load()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isAddingTrip = false, addTripError = throwable.staffMessage("Unable to add departure")) }
                }
        }
    }

    fun openTrip(trip: TripOccupancy) {
        _uiState.update {
            it.copy(
                selectedTrip = trip,
                seats = emptyList(),
                seatsError = null,
                pickedSeatLabels = emptySet(),
                inspecting = null,
                saleError = null,
                saleName = "",
                saleCountText = "1",
                passengerTypes = emptyList(),
            )
        }
        if (trip.rideKind == RideKind.BUS) loadSeats(trip.id)
    }

    private fun loadSeats(tripId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSeatsLoading = true) }
            partnerRepository.getTripSeats(tripId, operatorId)
                .onSuccess { seats -> _uiState.update { it.copy(isSeatsLoading = false, seats = seats) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSeatsLoading = false, seatsError = throwable.staffMessage("Unable to load seats")) }
                }
        }
    }

    fun closeTrip() = _uiState.update {
        it.copy(selectedTrip = null, seats = emptyList(), pickedSeatLabels = emptySet(), inspecting = null, saleError = null)
    }

    /** Available seats toggle into the sale; sold / held seats open the inspect popup instead. */
    fun onSeatTapped(seat: AllocatedSeat, writable: Boolean) {
        if (writable && seat.status == SeatStatus.AVAILABLE) {
            _uiState.update { state ->
                val picked = state.pickedSeatLabels
                // Fare types are positional in grid order, so a seat picked mid-grid would shift
                // everyone below it; carry each chosen type over by seat label instead.
                val typeBySeat = state.pickedSeatsOrdered.zip(state.effectivePassengerTypes).toMap()
                val next = state.copy(
                    pickedSeatLabels = if (seat.label in picked) picked - seat.label else picked + seat.label,
                    saleError = null,
                )
                next.copy(passengerTypes = next.pickedSeatsOrdered.map { typeBySeat[it] ?: PassengerType.REGULAR })
            }
        } else {
            _uiState.update { it.copy(inspecting = seat) }
        }
    }

    fun dismissInspect() = _uiState.update { it.copy(inspecting = null) }

    fun onSaleNameChanged(value: String) = _uiState.update { it.copy(saleName = value) }

    fun onSaleCountChanged(value: String) =
        _uiState.update { it.copy(saleCountText = value.filter(Char::isDigit).take(3), saleError = null) }

    fun onPassengerTypeChanged(index: Int, type: PassengerType) {
        _uiState.update { state ->
            val types = state.effectivePassengerTypes.toMutableList()
            if (index in types.indices) types[index] = type
            state.copy(passengerTypes = types)
        }
    }

    /**
     * Bus: sells the picked seats. Open seating: sells the typed count, never
     * more than the departure has left. The name goes out with the fare-type
     * tags appended ([PartnerTripsUiState.saleNameTagged]).
     */
    fun recordOnsiteSale() {
        val state = _uiState.value
        val trip = state.selectedTrip ?: return
        val problem = state.saleValidationError ?: when {
            trip.rideKind == RideKind.BUS && state.pickedSeatLabels.isEmpty() -> "Tap the seats to sell first"
            trip.rideKind != RideKind.BUS && state.passengerCount < 1 -> "Enter how many passengers to sell for"
            else -> null
        }
        if (problem != null) {
            _uiState.update { it.copy(saleError = problem) }
            return
        }
        val request = if (trip.rideKind == RideKind.BUS) {
            OnsiteSaleRequest(seatLabels = state.pickedSeatsOrdered, passengerFullName = state.saleNameTagged)
        } else {
            OnsiteSaleRequest(count = state.passengerCount, passengerFullName = state.saleNameTagged)
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSelling = true, saleError = null) }
            partnerRepository.recordOnsiteSale(trip.id, request)
                .onSuccess { issued ->
                    _uiState.update {
                        it.copy(isSelling = false, saleIssued = issued, pickedSeatLabels = emptySet(), passengerTypes = emptyList())
                    }
                    // Re-fetch from the server: sold seats now show the passenger name and the counts come back authoritative.
                    if (trip.rideKind == RideKind.BUS) loadSeats(trip.id)
                    load()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSelling = false, saleError = throwable.staffMessage("Unable to record sale")) }
                }
        }
    }

    fun dismissSale() = _uiState.update { it.copy(saleIssued = null) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
