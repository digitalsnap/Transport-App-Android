package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.core.domain.repository.AdminRepository
import com.ridevibe.feature.admin.ui.shiftIsoDay
import com.ridevibe.feature.admin.ui.todayPhIso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminTripsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** PH calendar date, `yyyy-MM-dd`; defaults to today. */
    val dateIso: String = todayPhIso(),
    val operatorFilter: String = "",
    val placeFilter: String = "",
    val trips: List<TripOccupancy> = emptyList(),
    /** Sheet: the tapped departure and, for buses, its allocation grid. */
    val selectedTrip: TripOccupancy? = null,
    val seats: List<AllocatedSeat> = emptyList(),
    val isSeatsLoading: Boolean = false,
    val seatsError: String? = null,
    /** Tap-to-inspect popup. */
    val inspecting: AllocatedSeat? = null,
)

/** Admin Trips: browse departures for a PH date; bus trips open the read-only seat allocation grid. */
@HiltViewModel
class AdminTripsViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminTripsUiState())
    val uiState: StateFlow<AdminTripsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun previousDay() = changeDay(-1)

    fun nextDay() = changeDay(+1)

    private fun changeDay(delta: Int) {
        _uiState.update { it.copy(dateIso = shiftIsoDay(it.dateIso, delta)) }
        load()
    }

    fun onOperatorFilterChanged(value: String) = _uiState.update { it.copy(operatorFilter = value) }

    fun onPlaceFilterChanged(value: String) = _uiState.update { it.copy(placeFilter = value) }

    fun load() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            adminRepository.getTrips(
                dateIso = state.dateIso,
                operator = state.operatorFilter.trim(),
                place = state.placeFilter.trim(),
                limit = 200,
            )
                .onSuccess { trips -> _uiState.update { it.copy(isLoading = false, trips = trips) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Unable to load trips") }
                }
        }
    }

    /** Seat maps are a bus concept — ferries and fastcraft show the occupancy summary only. */
    fun openTrip(trip: TripOccupancy) {
        _uiState.update { it.copy(selectedTrip = trip, seats = emptyList(), seatsError = null, inspecting = null) }
        if (trip.rideKind != RideKind.BUS) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSeatsLoading = true) }
            adminRepository.getTripSeats(trip.id)
                .onSuccess { seats -> _uiState.update { it.copy(isSeatsLoading = false, seats = seats) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSeatsLoading = false, seatsError = throwable.message ?: "Unable to load seats") }
                }
        }
    }

    fun closeTrip() = _uiState.update { it.copy(selectedTrip = null, seats = emptyList(), inspecting = null, seatsError = null) }

    fun inspectSeat(seat: AllocatedSeat) = _uiState.update { it.copy(inspecting = seat) }

    fun dismissInspect() = _uiState.update { it.copy(inspecting = null) }
}
