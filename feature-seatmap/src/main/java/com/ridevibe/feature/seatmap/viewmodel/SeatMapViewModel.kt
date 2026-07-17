package com.ridevibe.feature.seatmap.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
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
    val seats: List<Seat> = emptyList(),
    val selectedSeatId: String? = null,
    val holdSecondsRemaining: Int? = null,
    val errorMessage: String? = null,
)

private const val HOLD_DURATION_SECONDS = 10 * 60 // 10-minute seat hold

@HiltViewModel
class SeatMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeSeatMapUseCase: ObserveSeatMapUseCase,
    private val selectSeatUseCase: SelectSeatUseCase,
    private val releaseSeatUseCase: ReleaseSeatUseCase,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])

    private val _uiState = MutableStateFlow(SeatMapUiState())
    val uiState: StateFlow<SeatMapUiState> = _uiState.asStateFlow()

    private var holdCountdownJob: Job? = null

    init {
        loadSeatMap()
        listenForSeatEvents()
    }

    private fun loadSeatMap() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val seats = observeSeatMapUseCase.getInitialSeatMap(tripId)
            _uiState.update { it.copy(isLoading = false, seats = seats) }
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
        when (seat.status) {
            SeatStatus.AVAILABLE -> selectSeat(seat)
            SeatStatus.SELECTED -> deselectSeat(seat)
            SeatStatus.LOCKED, SeatStatus.OCCUPIED -> Unit // not selectable
        }
    }

    private fun selectSeat(seat: Seat) {
        // Deselect any prior pick first — only one seat may be held at a time.
        _uiState.value.selectedSeatId?.let { previousId ->
            _uiState.value.seats.find { it.id == previousId }?.let { releaseSeat(it) }
        }

        viewModelScope.launch {
            val result = selectSeatUseCase(tripId, seat.id)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        selectedSeatId = seat.id,
                        seats = state.seats.map {
                            if (it.id == seat.id) it.copy(status = SeatStatus.SELECTED) else it
                        },
                    )
                }
                startHoldCountdown(seat)
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.message ?: "Unable to select seat") }
            }
        }
    }

    private fun deselectSeat(seat: Seat) = releaseSeat(seat)

    private fun releaseSeat(seat: Seat) {
        holdCountdownJob?.cancel()
        viewModelScope.launch {
            releaseSeatUseCase(tripId, seat.id)
            _uiState.update { state ->
                state.copy(
                    selectedSeatId = null,
                    holdSecondsRemaining = null,
                    seats = state.seats.map {
                        if (it.id == seat.id) it.copy(status = SeatStatus.AVAILABLE) else it
                    },
                )
            }
        }
    }

    private fun startHoldCountdown(seat: Seat) {
        holdCountdownJob?.cancel()
        holdCountdownJob = viewModelScope.launch {
            for (secondsLeft in HOLD_DURATION_SECONDS downTo 0) {
                _uiState.update { it.copy(holdSecondsRemaining = secondsLeft) }
                delay(1_000)
            }
            // Hold expired without checkout — release the seat back to the pool.
            releaseSeat(seat)
        }
    }

    override fun onCleared() {
        holdCountdownJob?.cancel()
        super.onCleared()
    }
}
