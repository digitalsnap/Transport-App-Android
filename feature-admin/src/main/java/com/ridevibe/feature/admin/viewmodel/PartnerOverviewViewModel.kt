package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.PartnerOverview
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.core.domain.repository.PartnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerOverviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val overview: PartnerOverview? = null,
    /** `getTrips()` with no date — today's departures. */
    val todaysTrips: List<TripOccupancy> = emptyList(),
    val selectedTrip: TripOccupancy? = null,
    val seats: List<AllocatedSeat> = emptyList(),
    val isSeatsLoading: Boolean = false,
    val seatsError: String? = null,
    val inspecting: AllocatedSeat? = null,
)

/**
 * Partner Overview (`/partner/api/me` + today's trips). A PARTNER session acts
 * for its own operator (operatorId null); an ADMIN passes the operator to view.
 */
@HiltViewModel
class PartnerOverviewViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerOverviewUiState())
    val uiState: StateFlow<PartnerOverviewUiState> = _uiState.asStateFlow()

    private var operatorId: Int? = null
    private var started = false

    /** Binds the portal to an operator; reloads when the admin switches company. */
    fun start(operatorId: Int?) {
        if (started && this.operatorId == operatorId) return
        started = true
        this.operatorId = operatorId
        _uiState.value = PartnerOverviewUiState()
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val overview = partnerRepository.getOverview(operatorId)
            overview.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Unable to load overview") }
                return@launch
            }
            val trips = partnerRepository.getTrips(dateIso = null, operatorId = operatorId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    overview = overview.getOrNull(),
                    todaysTrips = trips.getOrDefault(emptyList()),
                    error = trips.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun openTrip(trip: TripOccupancy) {
        _uiState.update { it.copy(selectedTrip = trip, seats = emptyList(), seatsError = null, inspecting = null) }
        if (trip.rideKind != RideKind.BUS) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSeatsLoading = true) }
            partnerRepository.getTripSeats(trip.id, operatorId)
                .onSuccess { seats -> _uiState.update { it.copy(isSeatsLoading = false, seats = seats) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSeatsLoading = false, seatsError = throwable.message ?: "Unable to load seats") }
                }
        }
    }

    fun closeTrip() = _uiState.update { it.copy(selectedTrip = null, seats = emptyList(), inspecting = null) }

    fun inspectSeat(seat: AllocatedSeat) = _uiState.update { it.copy(inspecting = seat) }

    fun dismissInspect() = _uiState.update { it.copy(inspecting = null) }
}
