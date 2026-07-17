package com.ridevibe.feature.seatmap.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.usecase.GetTripUseCase
import com.ridevibe.core.domain.usecase.ObserveSeatMapUseCase
import com.ridevibe.core.domain.usecase.ReleaseSeatUseCase
import com.ridevibe.core.domain.usecase.SelectSeatUseCase
import com.ridevibe.core.domain.usecase.applySeatEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeatMapUiState(
    val isLoading: Boolean = true,
    val trip: Trip? = null,
    val seats: List<Seat> = emptyList(),
    /** Seats to pick, from the search form: adults + children. */
    val requiredSeatCount: Int = 1,
    val selectedSeatIds: List<String> = emptyList(),
    val holdSecondsRemaining: Int? = null,
    val errorMessage: String? = null,
) {
    val selectionComplete: Boolean get() = selectedSeatIds.size == requiredSeatCount
    val totalFarePhp: Double get() = (trip?.farePhp ?: 0.0) * selectedSeatIds.size
}

private const val HOLD_DURATION_SECONDS = 10 * 60 // 10-minute seat hold

@HiltViewModel
class SeatMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTripUseCase: GetTripUseCase,
    private val observeSeatMapUseCase: ObserveSeatMapUseCase,
    private val selectSeatUseCase: SelectSeatUseCase,
    private val releaseSeatUseCase: ReleaseSeatUseCase,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val requiredSeatCount: Int =
        (savedStateHandle.get<String>("seatCount")?.toIntOrNull() ?: 1).coerceAtLeast(1)

    private val _uiState = MutableStateFlow(SeatMapUiState(requiredSeatCount = requiredSeatCount))
    val uiState: StateFlow<SeatMapUiState> = _uiState.asStateFlow()

    private var holdCountdownJob: Job? = null

    init {
        load()
        listenForSeatEvents()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val tripResult = getTripUseCase(tripId)
            val seatsResult = runCatching { observeSeatMapUseCase.getInitialSeatMap(tripId) }
            tripResult.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "Unable to load trip") }
                return@launch
            }
            seatsResult.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "Unable to load seats") }
                return@launch
            }
            _uiState.update {
                it.copy(isLoading = false, trip = tripResult.getOrNull(), seats = seatsResult.getOrDefault(emptyList()))
            }
        }
    }

    private fun listenForSeatEvents() {
        viewModelScope.launch {
            observeSeatMapUseCase.observeEvents(tripId).collect { event ->
                _uiState.update { state -> state.copy(seats = applySeatEvent(state.seats, event)) }
            }
        }
    }

    fun onSeatClicked(seat: Seat) {
        val state = _uiState.value
        when {
            seat.id in state.selectedSeatIds -> deselectSeat(seat)

            seat.status == SeatStatus.AVAILABLE -> {
                if (state.selectedSeatIds.size >= state.requiredSeatCount) {
                    _uiState.update {
                        it.copy(
                            errorMessage = "You can only select ${it.requiredSeatCount} " +
                                "seat${if (it.requiredSeatCount == 1) "" else "s"} for the passengers declared.",
                        )
                    }
                } else {
                    selectSeat(seat)
                }
            }

            else -> Unit // occupied / locked by others: not selectable
        }
    }

    private fun selectSeat(seat: Seat) {
        viewModelScope.launch {
            selectSeatUseCase(tripId, seat.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            selectedSeatIds = state.selectedSeatIds + seat.id,
                            errorMessage = null,
                            seats = state.seats.map {
                                if (it.id == seat.id) it.copy(status = SeatStatus.SELECTED) else it
                            },
                        )
                    }
                    if (holdCountdownJob?.isActive != true) startHoldCountdown()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(errorMessage = throwable.message ?: "Unable to select seat") }
                }
        }
    }

    private fun deselectSeat(seat: Seat) {
        viewModelScope.launch {
            releaseSeatUseCase(tripId, seat.id)
            _uiState.update { state ->
                val remaining = state.selectedSeatIds - seat.id
                if (remaining.isEmpty()) holdCountdownJob?.cancel()
                state.copy(
                    selectedSeatIds = remaining,
                    holdSecondsRemaining = if (remaining.isEmpty()) null else state.holdSecondsRemaining,
                    seats = state.seats.map {
                        if (it.id == seat.id) it.copy(status = SeatStatus.AVAILABLE) else it
                    },
                )
            }
        }
    }

    /** One shared hold window for the whole selection; expiry releases everything. */
    private fun startHoldCountdown() {
        holdCountdownJob?.cancel()
        holdCountdownJob = viewModelScope.launch {
            for (secondsLeft in HOLD_DURATION_SECONDS downTo 0) {
                _uiState.update { it.copy(holdSecondsRemaining = secondsLeft) }
                delay(1_000)
            }
            releaseAllSeats()
        }
    }

    private fun releaseAllSeats() {
        viewModelScope.launch {
            val held = _uiState.value.selectedSeatIds
            held.forEach { releaseSeatUseCase(tripId, it) }
            _uiState.update { state ->
                state.copy(
                    selectedSeatIds = emptyList(),
                    holdSecondsRemaining = null,
                    seats = state.seats.map {
                        if (it.id in held) it.copy(status = SeatStatus.AVAILABLE) else it
                    },
                )
            }
        }
    }

    override fun onCleared() {
        holdCountdownJob?.cancel()
        super.onCleared()
    }
}
