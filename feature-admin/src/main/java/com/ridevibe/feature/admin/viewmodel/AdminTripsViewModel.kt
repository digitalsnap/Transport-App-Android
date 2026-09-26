package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.core.domain.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TRIPS_PAGE_SIZE = 50
private const val FILTER_DEBOUNCE_MS = 300L

data class AdminTripsUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** PH calendar date, `yyyy-MM-dd`; defaults to today. */
    val dateIso: String = PhTime.todayIso(),
    val operatorFilter: String = "",
    val placeFilter: String = "",
    val trips: List<TripOccupancy> = emptyList(),
    /** False until the first page for the current filters has answered — "no trips" is only true after that. */
    val hasLoaded: Boolean = false,
    /** True while the last page came back full, i.e. another `offset` may yield more. */
    val canLoadMore: Boolean = false,
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

    private var sessionKey: String? = null
    private var debounceJob: Job? = null
    private var loadJob: Job? = null

    fun start(sessionKey: String) {
        if (this.sessionKey == sessionKey) return
        this.sessionKey = sessionKey
        debounceJob?.cancel()
        _uiState.value = AdminTripsUiState()
        load()
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

    fun onOperatorFilterChanged(value: String) {
        _uiState.update { it.copy(operatorFilter = value) }
        scheduleLoad()
    }

    fun onPlaceFilterChanged(value: String) {
        _uiState.update { it.copy(placeFilter = value) }
        scheduleLoad()
    }

    /** Typing in a filter reloads after a short pause, so each keystroke is not a request. */
    private fun scheduleLoad() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(FILTER_DEBOUNCE_MS)
            load()
        }
    }

    /** First page for the current date and filters; replaces the list. */
    fun load() {
        debounceJob?.cancel()
        loadJob?.cancel()
        val state = _uiState.value
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchPage(state, offset = 0)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(isLoading = false, hasLoaded = true, trips = page, canLoadMore = page.size >= TRIPS_PAGE_SIZE)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.staffMessage("Unable to load trips")) }
                }
        }
    }

    /** Next page with `offset = trips.size`, appended. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, error = null) }
            fetchPage(state, offset = state.trips.size)
                .onSuccess { page ->
                    _uiState.update { current ->
                        val known = current.trips.map { it.id }.toSet()
                        current.copy(
                            isLoadingMore = false,
                            trips = current.trips + page.filter { it.id !in known },
                            canLoadMore = page.size >= TRIPS_PAGE_SIZE,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoadingMore = false, error = throwable.staffMessage("Unable to load more trips")) }
                }
        }
    }

    private suspend fun fetchPage(state: AdminTripsUiState, offset: Int): Result<List<TripOccupancy>> =
        adminRepository.getTrips(
            dateIso = state.dateIso,
            operator = state.operatorFilter.trim(),
            place = state.placeFilter.trim(),
            limit = TRIPS_PAGE_SIZE,
            offset = offset,
        )

    /** Seat maps are a bus concept — ferries and fastcraft show the occupancy summary only. */
    fun openTrip(trip: TripOccupancy) {
        _uiState.update { it.copy(selectedTrip = trip, seats = emptyList(), seatsError = null, inspecting = null) }
        if (trip.rideKind != RideKind.BUS) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSeatsLoading = true) }
            adminRepository.getTripSeats(trip.id)
                .onSuccess { seats -> _uiState.update { it.copy(isSeatsLoading = false, seats = seats) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSeatsLoading = false, seatsError = throwable.staffMessage("Unable to load seats")) }
                }
        }
    }

    fun closeTrip() = _uiState.update { it.copy(selectedTrip = null, seats = emptyList(), inspecting = null, seatsError = null) }

    fun inspectSeat(seat: AllocatedSeat) = _uiState.update { it.copy(inspecting = seat) }

    fun dismissInspect() = _uiState.update { it.copy(inspecting = null) }
}
