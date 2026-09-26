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
    /** One fare type per passenger being sold; sized to [passengerCount] on read. */
    val passengerTypes: List<PassengerType> = emptyList(),
    val isSelling: Boolean = false,
    val saleError: String? = null,
    /** Result dialog after a counter sale. */
    val saleIssued: OnsiteSaleIssued? = null,
    val message: String? = null,
) {
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

    /** Amount to collect at the counter with the regulated 20% discounts applied per passenger. */
    val saleAmountPhp: Double
        get() {
            val fare = selectedTrip?.farePhp ?: return 0.0
            return effectivePassengerTypes.sumOf { fare * (1 - it.discountRate) }
        }

    val canSell: Boolean
        get() = !isSelling && selectedTrip != null && passengerCount > 0
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
                state.copy(pickedSeatLabels = if (seat.label in picked) picked - seat.label else picked + seat.label, saleError = null)
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
     * more than the departure has left. Discounted fare types ride along in
     * the passenger name (`Maria Santos (SR+R)`, the rider QR's own tags)
     * because the sale request has no field for them yet — see
     * docs/api/PENDING-BACKEND.md.
     */
    fun recordOnsiteSale() {
        val state = _uiState.value
        val trip = state.selectedTrip ?: return
        val name = state.saleName.trim().ifBlank { DEFAULT_WALK_IN_NAME }
        val types = state.effectivePassengerTypes
        val taggedName = if (types.any { it != PassengerType.REGULAR }) "$name (${types.joinToString("+") { it.qrTag() }})" else name
        val request = if (trip.rideKind == RideKind.BUS) {
            if (state.pickedSeatLabels.isEmpty()) {
                _uiState.update { it.copy(saleError = "Tap the seats to sell first") }
                return
            }
            OnsiteSaleRequest(seatLabels = state.pickedSeatLabels.toList(), passengerFullName = taggedName)
        } else {
            val count = state.saleCountText.toIntOrNull()
            val problem = when {
                count == null || count < 1 -> "Enter how many passengers to sell for"
                count > trip.available -> "Only ${trip.available} ${if (trip.available == 1) "space is" else "spaces are"} left on this sailing"
                else -> null
            }
            if (problem != null || count == null) {
                _uiState.update { it.copy(saleError = problem) }
                return
            }
            OnsiteSaleRequest(count = count, passengerFullName = taggedName)
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

    /** The same short tags `MockDatabase.createTicket` puts in a rider QR. */
    private fun PassengerType.qrTag(): String = when (this) {
        PassengerType.REGULAR -> "R"
        PassengerType.STUDENT -> "ST"
        PassengerType.SENIOR_CITIZEN -> "SR"
        PassengerType.PWD -> "PWD"
    }
}
