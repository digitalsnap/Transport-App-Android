package com.ridevibe.feature.seatmap.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.usecase.GetTripUseCase
import com.ridevibe.core.domain.usecase.ObserveSeatMapUseCase
import com.ridevibe.core.domain.usecase.ReleaseSeatUseCase
import com.ridevibe.core.domain.usecase.SelectSeatUseCase
import com.ridevibe.core.domain.usecase.applySeatEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
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
private const val MAX_RECONNECT_DELAY_MILLIS = 30_000L

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

    /**
     * Seats this screen is releasing on purpose. The server echoes each
     * release as an AVAILABLE event; without this the echo would look like a
     * hold lost to expiry and raise a spurious warning.
     */
    private val pendingReleases: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

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
            val seats = seatsResult.getOrDefault(emptyList())
            // Seats the data layer marked SELECTED are this user's live holds
            // (recognised via lockedByUserId) — e.g. after the screen was
            // recreated mid-selection. Restore them into the selection so they
            // stay deselectable and count toward the required seats.
            val restored = seats.filter { it.status == SeatStatus.SELECTED }.map { it.id }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    trip = tripResult.getOrNull(),
                    seats = seats,
                    selectedSeatIds = (it.selectedSeatIds + restored).distinct(),
                )
            }
            if (restored.isNotEmpty() && holdCountdownJob?.isActive != true) startHoldCountdown()
        }
    }

    /**
     * Live seat events with reconnect. The socket flow ends on any network
     * failure (and when the server closes it); without this loop the screen
     * would silently stop updating. Each reconnect first re-fetches the seat
     * map, because events missed while offline are gone for good.
     */
    private fun listenForSeatEvents() {
        viewModelScope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    observeSeatMapUseCase.observeEvents(tripId).collect { event ->
                        attempt = 0
                        _uiState.update { state -> state.copy(seats = applySeatEvent(state.seats, event)) }
                        onOwnHoldLostIfNeeded(event)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Connection failed or dropped — fall through to the backoff.
                }
                attempt++
                delay(reconnectDelayMillis(attempt))
                resyncSeatMap()
            }
        }
    }

    /** Exponential backoff: 1s, 2s, 4s … capped at 30s. */
    private fun reconnectDelayMillis(attempt: Int): Long =
        (1_000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)

    private suspend fun resyncSeatMap() {
        runCatching { observeSeatMapUseCase.getInitialSeatMap(tripId) }.onSuccess { seats ->
            _uiState.update { state ->
                // Holds the server no longer knows about were lost while offline.
                val stillHeld = state.selectedSeatIds.filter { id ->
                    seats.firstOrNull { it.id == id }?.status == SeatStatus.SELECTED
                }
                if (stillHeld.isEmpty()) holdCountdownJob?.cancel()
                state.copy(
                    seats = seats,
                    selectedSeatIds = stillHeld,
                    holdSecondsRemaining = if (stillHeld.isEmpty()) null else state.holdSecondsRemaining,
                )
            }
        }
    }

    /**
     * A selected seat the server now reports in any state other than "held by
     * me" is no longer ours: the hold expired server-side, or support released
     * it. Drop it from the selection so checkout cannot proceed with a seat we
     * do not hold. Releases this screen initiated itself are expected echoes.
     */
    private fun onOwnHoldLostIfNeeded(event: SeatStatusEvent) {
        if (event.status == SeatStatus.SELECTED) return
        if (pendingReleases.remove(event.seatId)) return
        _uiState.update { state ->
            if (event.seatId !in state.selectedSeatIds) return@update state
            val remaining = state.selectedSeatIds - event.seatId
            if (remaining.isEmpty()) holdCountdownJob?.cancel()
            val label = state.seats.firstOrNull { it.id == event.seatId }?.label ?: event.seatId
            state.copy(
                selectedSeatIds = remaining,
                holdSecondsRemaining = if (remaining.isEmpty()) null else state.holdSecondsRemaining,
                errorMessage = "Seat $label is no longer held for you.",
            )
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
                            selectedSeatIds = (state.selectedSeatIds + seat.id).distinct(),
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
            pendingReleases.add(seat.id)
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
            pendingReleases.addAll(held)
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
        // Screen popped without completing checkout: give held seats back so
        // they aren't locked forever. The repository ignores this for seats
        // that were meanwhile booked (OCCUPIED) or locked by someone else,
        // so it is safe to fire after a successful booking too.
        // viewModelScope is already cancelled here, hence the one-shot scope.
        val held = _uiState.value.selectedSeatIds
        if (held.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                held.forEach { releaseSeatUseCase(tripId, it) }
            }
        }
        super.onCleared()
    }
}
