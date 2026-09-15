package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.OnsiteSaleIssued
import com.ridevibe.core.domain.model.OnsiteSaleRequest
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.core.domain.repository.PartnerRepository
import com.ridevibe.feature.admin.ui.shiftIsoDay
import com.ridevibe.feature.admin.ui.todayPhIso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerTripsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val dateIso: String = todayPhIso(),
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
    val isSelling: Boolean = false,
    val saleError: String? = null,
    /** Result dialog after a counter sale. */
    val saleIssued: OnsiteSaleIssued? = null,
    val message: String? = null,
)

/** Partner Trips: departures by PH date, seat maps with passenger names, extra departures and on-site sales. */
@HiltViewModel
class PartnerTripsViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerTripsUiState())
    val uiState: StateFlow<PartnerTripsUiState> = _uiState.asStateFlow()

    private var operatorId: Int? = null
    private var started = false

    fun start(operatorId: Int?, writable: Boolean) {
        if (started && this.operatorId == operatorId) return
        started = true
        this.operatorId = operatorId
        _uiState.value = PartnerTripsUiState()
        load()
        if (writable) loadServices()
    }

    fun previousDay() = changeDay(-1)

    fun nextDay() = changeDay(+1)

    private fun changeDay(delta: Int) {
        _uiState.update { it.copy(dateIso = shiftIsoDay(it.dateIso, delta)) }
        load()
    }

    fun load() {
        val dateIso = _uiState.value.dateIso
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            partnerRepository.getTrips(dateIso = dateIso, operatorId = operatorId)
                .onSuccess { trips -> _uiState.update { it.copy(isLoading = false, trips = trips) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Unable to load trips") }
                }
        }
    }

    private fun loadServices() {
        viewModelScope.launch {
            partnerRepository.getServices(operatorId)
                .onSuccess { services -> _uiState.update { it.copy(services = services) } }
                .onFailure { throwable -> _uiState.update { it.copy(addTripError = throwable.message) } }
        }
    }

    /** One-off departure outside the regular hours; the list jumps to that date on success. */
    fun addExtraTrip(serviceId: Int, dateIso: String, timeHm: String) {
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
                    _uiState.update { it.copy(isAddingTrip = false, addTripError = throwable.message ?: "Unable to add departure") }
                }
        }
    }

    fun openTrip(trip: TripOccupancy) {
        _uiState.update {
            it.copy(selectedTrip = trip, seats = emptyList(), seatsError = null, pickedSeatLabels = emptySet(), inspecting = null, saleError = null)
        }
        if (trip.rideKind == RideKind.BUS) loadSeats(trip.id)
    }

    private fun loadSeats(tripId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSeatsLoading = true) }
            partnerRepository.getTripSeats(tripId, operatorId)
                .onSuccess { seats -> _uiState.update { it.copy(isSeatsLoading = false, seats = seats) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSeatsLoading = false, seatsError = throwable.message ?: "Unable to load seats") }
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
                state.copy(pickedSeatLabels = if (seat.label in picked) picked - seat.label else picked + seat.label)
            }
        } else {
            _uiState.update { it.copy(inspecting = seat) }
        }
    }

    fun dismissInspect() = _uiState.update { it.copy(inspecting = null) }

    /** Bus: sells the picked seats. Open seating: sells [count] spaces. */
    fun recordOnsiteSale(passengerName: String, count: Int?) {
        val state = _uiState.value
        val trip = state.selectedTrip ?: return
        val request = if (trip.rideKind == RideKind.BUS) {
            if (state.pickedSeatLabels.isEmpty()) {
                _uiState.update { it.copy(saleError = "Tap the seats to sell first") }
                return
            }
            OnsiteSaleRequest(seatLabels = state.pickedSeatLabels.toList(), passengerFullName = passengerName.trim().ifBlank { "Walk-in passenger" })
        } else {
            if (count == null || count < 1) {
                _uiState.update { it.copy(saleError = "Enter how many passengers to sell for") }
                return
            }
            OnsiteSaleRequest(count = count, passengerFullName = passengerName.trim().ifBlank { "Walk-in passenger" })
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSelling = true, saleError = null) }
            partnerRepository.recordOnsiteSale(trip.id, request)
                .onSuccess { issued ->
                    _uiState.update { it.copy(isSelling = false, saleIssued = issued, pickedSeatLabels = emptySet()) }
                    // Re-fetch: sold seats now show the passenger name; counts refresh in the list.
                    if (trip.rideKind == RideKind.BUS) loadSeats(trip.id)
                    load()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSelling = false, saleError = throwable.message ?: "Unable to record sale") }
                }
        }
    }

    fun dismissSale() {
        val issued = _uiState.value.saleIssued ?: return
        _uiState.update { state ->
            // Keep the sheet's occupancy tiles in step with what the list will show after reload.
            val trip = state.selectedTrip?.let { current ->
                state.trips.firstOrNull { it.id == current.id } ?: current.copy(
                    sold = current.sold + issued.seatLabels.size,
                    available = (current.available - issued.seatLabels.size).coerceAtLeast(0),
                )
            }
            state.copy(saleIssued = null, selectedTrip = trip)
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
