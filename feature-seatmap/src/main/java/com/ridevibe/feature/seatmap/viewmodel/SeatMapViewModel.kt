package com.ridevibe.feature.seatmap.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.PartyError
import com.ridevibe.core.domain.model.PartyRules
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.SeatRepository
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

/** Health of the live seat-event feed, shown as a banner when it is not [LIVE]. */
enum class SeatFeedStatus {
    LIVE,
    RECONNECTING,
    /** Reconnect attempts exhausted; the rider must tap Retry. */
    OFFLINE,
}

/**
 * What the seat map hands to the nav graph when the rider confirms. Carries
 * everything `BookingCart.setOutboundLeg` / `setReturnLeg` need so the graph
 * does not have to reach back into this screen's state.
 */
data class SeatMapSelection(
    val tripId: String,
    val seatIds: List<String>,
    /** Null for sea passage (nothing is held) or when the server sent no expiry. */
    val holdExpiresAtEpochMillis: Long?,
    val farePhp: Double,
    val rideKind: RideKind,
) {
    val seatIdsCsv: String get() = seatIds.joinToString(",")
}

data class SeatMapUiState(
    val isLoading: Boolean = true,
    /** Trip or seat map failed to load: the screen shows a full-screen retry. */
    val loadError: String? = null,
    val trip: Trip? = null,
    val seats: List<Seat> = emptyList(),
    /** Seats to pick, from the search form: adults + children. */
    val requiredSeatCount: Int = 1,
    /** Lap-held infants: they need no seat but the summary must still mention them. */
    val infants: Int = 0,
    /** A [PartyRules] breach in the request itself (e.g. more seats than one booking may hold). */
    val partyError: String? = null,
    val selectedSeatIds: List<String> = emptyList(),
    /** Server-driven hold expiry for the whole selection: the earliest of the held seats' expiries. */
    val holdExpiresAtEpochMillis: Long? = null,
    val holdSecondsRemaining: Int? = null,
    /** The hold lapsed while seats were still selected; the screen blocks until the rider picks again. */
    val holdExpired: Boolean = false,
    val feedStatus: SeatFeedStatus = SeatFeedStatus.LIVE,
    /** Transient message for the snackbar; cleared once shown. */
    val errorMessage: String? = null,
) {
    val selectionComplete: Boolean get() = selectedSeatIds.size == requiredSeatCount
    val totalFarePhp: Double get() = (trip?.farePhp ?: 0.0) * selectedSeatIds.size

    /** Ferries and fastcrafts sell passage; the nav graph normally skips this screen for them. */
    val sellsPassage: Boolean get() = trip?.rideKind?.sellsPassage == true

    /** Cabin width from the data, so a 2+1 luxury coach (3 columns) renders without a phantom seat. */
    val columnCount: Int get() = seats.maxOfOrNull { it.column } ?: DEFAULT_COLUMN_COUNT

    val canProceed: Boolean get() = !holdExpired && partyError == null && (sellsPassage || selectionComplete)

    companion object {
        const val DEFAULT_COLUMN_COUNT = 4
    }
}

/**
 * Last-resort hold length, used only when neither the lock response nor any
 * socket event carried an expiry: the CRS documents a 10-minute TTL
 * (`x-open-questions.hold-semantics` in docs/api/openapi.yaml).
 */
private const val FALLBACK_HOLD_MILLIS = 10 * 60 * 1_000L
private const val MAX_RECONNECT_DELAY_MILLIS = 30_000L
private const val MAX_RECONNECT_ATTEMPTS = 8
private const val KEY_HOLD_EXPIRES_AT = "holdExpiresAt"

@HiltViewModel
class SeatMapViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getTripUseCase: GetTripUseCase,
    private val observeSeatMapUseCase: ObserveSeatMapUseCase,
    private val selectSeatUseCase: SelectSeatUseCase,
    private val releaseSeatUseCase: ReleaseSeatUseCase,
    private val seatRepository: SeatRepository,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val requiredSeatCount: Int =
        (savedStateHandle.get<String>("seatCount")?.toIntOrNull() ?: 1).coerceAtLeast(1)
    private val infants: Int =
        (savedStateHandle.get<String>("infants")?.toIntOrNull() ?: 0).coerceAtLeast(0)

    private val _uiState = MutableStateFlow(
        SeatMapUiState(
            requiredSeatCount = requiredSeatCount,
            infants = infants,
            partyError = PartyError.TOO_MANY_SEATS.message
                .takeIf { requiredSeatCount > PartyRules.MAX_SEATS_PER_BOOKING },
            // Survives process death so a rider coming back from a call sees the
            // real remaining time, not a fresh 10 minutes.
            holdExpiresAtEpochMillis = savedStateHandle[KEY_HOLD_EXPIRES_AT],
        ),
    )
    val uiState: StateFlow<SeatMapUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    /** False until the first START; later STARTs must resync because events missed while stopped are gone. */
    private var hasObservedBefore = false

    /**
     * Seats this screen is releasing on purpose. The server echoes each
     * release as an AVAILABLE event; without this the echo would look like a
     * hold lost to expiry and raise a spurious warning.
     */
    private val pendingReleases: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    init {
        load()
        runHoldCountdown()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            val trip = getTripUseCase(tripId).getOrElse { throwable ->
                _uiState.update { it.copy(isLoading = false, loadError = throwable.message ?: "Unable to load trip") }
                return@launch
            }
            val seats = observeSeatMapUseCase.getInitialSeatMap(tripId).getOrElse { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, trip = trip, loadError = throwable.message ?: "Unable to load seats")
                }
                return@launch
            }
            // Seats the data layer marked SELECTED are this user's live holds
            // (recognised via lockedByUserId) — e.g. after the screen was
            // recreated mid-selection. Restore them into the selection so they
            // stay deselectable and count toward the required seats. Holds
            // beyond the party size (a stale hold from an earlier, larger
            // search) are given back, otherwise `selectionComplete` could never
            // become true and the screen would deadlock.
            val held = seats
                .filter { it.status == SeatStatus.SELECTED }
                .sortedWith(compareBy({ it.row }, { it.column }))
                .map { it.id }
            val kept = held.take(requiredSeatCount)
            val extras = held.drop(requiredSeatCount)
            releaseQuietly(extras)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    trip = trip,
                    seats = seats.markAvailable(extras),
                    selectedSeatIds = kept,
                ).withHoldExpiry()
            }
        }
    }

    // ---- Lifecycle ---------------------------------------------------------

    /** Screen visible: open the live feed. A re-open first re-fetches the map because events missed while stopped are gone. */
    fun onScreenStarted() {
        if (observeJob?.isActive == true) return
        startObserving(resyncFirst = hasObservedBefore)
        hasObservedBefore = true
    }

    /** Screen hidden: stop the socket so a backgrounded app holds no connection. Holds stay server-side. */
    fun onScreenStopped() {
        observeJob?.cancel()
        observeJob = null
    }

    /** Rider tapped Retry on the offline banner after the reconnect cap was hit. */
    fun retryConnection() {
        startObserving(resyncFirst = true)
    }

    /**
     * Live seat events with reconnect. The socket flow ends on any network
     * failure (and when the server closes it); without this loop the screen
     * would silently stop updating. Each reconnect first re-fetches the seat
     * map, because events missed while offline are gone for good. After
     * [MAX_RECONNECT_ATTEMPTS] straight failures the loop gives up and the
     * banner offers a manual Retry instead of hammering the server.
     */
    private fun startObserving(resyncFirst: Boolean) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            if (resyncFirst) resyncSeatMap()
            _uiState.update { it.copy(feedStatus = SeatFeedStatus.LIVE) }
            var attempt = 0
            while (isActive) {
                try {
                    observeSeatMapUseCase.observeEvents(tripId).collect { event ->
                        attempt = 0
                        _uiState.update { state ->
                            state.copy(seats = applySeatEvent(state.seats, event)).withHoldExpiry()
                        }
                        onOwnHoldLostIfNeeded(event)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Connection failed or dropped — fall through to the backoff.
                }
                attempt++
                if (attempt > MAX_RECONNECT_ATTEMPTS) {
                    _uiState.update { it.copy(feedStatus = SeatFeedStatus.OFFLINE) }
                    return@launch
                }
                _uiState.update { it.copy(feedStatus = SeatFeedStatus.RECONNECTING) }
                delay(reconnectDelayMillis(attempt))
                // A successful REST resync proves the network is back; the
                // banner clears here rather than waiting for a seat event that
                // may never come on a quiet trip.
                if (resyncSeatMap()) _uiState.update { it.copy(feedStatus = SeatFeedStatus.LIVE) }
            }
        }
    }

    /** Exponential backoff: 1s, 2s, 4s … capped at 30s. */
    private fun reconnectDelayMillis(attempt: Int): Long =
        (1_000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)

    /** Re-fetches the snapshot; true on success. Failure is surfaced, not swallowed, so a stale map is never mistaken for a live one. */
    private suspend fun resyncSeatMap(): Boolean {
        val seats = observeSeatMapUseCase.getInitialSeatMap(tripId).getOrElse { throwable ->
            _uiState.update {
                it.copy(errorMessage = "Couldn't refresh seats: ${throwable.message ?: "no connection"}")
            }
            return false
        }
        _uiState.update { state ->
            // Holds the server no longer knows about were lost while offline.
            val stillHeld = state.selectedSeatIds.filter { id ->
                seats.firstOrNull { it.id == id }?.status == SeatStatus.SELECTED
            }
            val lostAny = stillHeld.size < state.selectedSeatIds.size
            state.copy(
                seats = seats,
                selectedSeatIds = stillHeld,
                errorMessage = if (lostAny && !state.holdExpired) "Some seats are no longer held for you." else state.errorMessage,
            ).withHoldExpiry()
        }
        return true
    }

    /**
     * A selected seat the server now reports in any state other than "held by
     * me" is no longer ours: the hold expired server-side, or support released
     * it. Drop it from the selection so checkout cannot proceed with a seat we
     * do not hold. Releases this screen initiated itself are expected echoes,
     * and while the expiry dialog is up the loss is already being explained.
     */
    private fun onOwnHoldLostIfNeeded(event: SeatStatusEvent) {
        if (event.status == SeatStatus.SELECTED) return
        if (pendingReleases.remove(event.seatId)) return
        _uiState.update { state ->
            if (event.seatId !in state.selectedSeatIds) return@update state
            val label = state.seats.firstOrNull { it.id == event.seatId }?.label ?: event.seatId
            state.copy(
                selectedSeatIds = state.selectedSeatIds - event.seatId,
                errorMessage = if (state.holdExpired) state.errorMessage else "Seat $label is no longer held for you.",
            ).withHoldExpiry()
        }
    }

    // ---- Selection ---------------------------------------------------------

    fun onSeatClicked(seat: Seat) {
        val state = _uiState.value
        when {
            state.holdExpired -> Unit // the dialog owns the screen until the rider picks again

            state.partyError != null -> _uiState.update { it.copy(errorMessage = state.partyError) }

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
                .onSuccess { serverExpiry ->
                    _uiState.update { state ->
                        state.copy(
                            selectedSeatIds = (state.selectedSeatIds + seat.id).distinct(),
                            errorMessage = null,
                            seats = state.seats.map {
                                if (it.id == seat.id) {
                                    it.copy(
                                        status = SeatStatus.SELECTED,
                                        // The lock response is authoritative; a socket event may
                                        // still overwrite it later with the same or a newer value.
                                        lockExpiresAtEpochMillis = serverExpiry ?: it.lockExpiresAtEpochMillis,
                                    )
                                } else {
                                    it
                                }
                            },
                        ).withHoldExpiry()
                    }
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
                state.copy(
                    selectedSeatIds = state.selectedSeatIds - seat.id,
                    // A "you can only select N seats" nudge is moot once a seat is freed.
                    errorMessage = null,
                    seats = state.seats.markAvailable(listOf(seat.id)),
                ).withHoldExpiry()
            }
        }
    }

    /**
     * Validates and packages the selection for the nav graph; null when the
     * rider cannot proceed yet (the reason lands in [SeatMapUiState.errorMessage]).
     * Sea passage never reaches this screen through the normal flow, but if it
     * does the rider must not dead-end: hand back `P1…Pn` like the graph would.
     */
    fun proceed(): SeatMapSelection? {
        val state = _uiState.value
        val trip = state.trip ?: return null
        if (state.holdExpired) return null
        state.partyError?.let { message ->
            _uiState.update { it.copy(errorMessage = message) }
            return null
        }
        if (trip.rideKind.sellsPassage) {
            return SeatMapSelection(
                tripId = tripId,
                seatIds = (1..requiredSeatCount).map { "P$it" },
                holdExpiresAtEpochMillis = null,
                farePhp = trip.farePhp,
                rideKind = trip.rideKind,
            )
        }
        if (!state.selectionComplete) {
            _uiState.update {
                it.copy(errorMessage = "Pick ${it.requiredSeatCount} seat${if (it.requiredSeatCount == 1) "" else "s"} to continue.")
            }
            return null
        }
        return SeatMapSelection(
            tripId = tripId,
            seatIds = state.selectedSeatIds,
            holdExpiresAtEpochMillis = state.holdExpiresAtEpochMillis,
            farePhp = trip.farePhp,
            rideKind = trip.rideKind,
        )
    }

    /** The snackbar has shown the message; drop it so a recomposition does not replay it. */
    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ---- Hold window -------------------------------------------------------

    /**
     * The hold clock is wall-clock based: `expiry - now`, recomputed every
     * second, so it neither drifts nor restarts when the screen is recreated.
     * `collectLatest` restarts the loop whenever the expiry itself changes
     * (a new seat with an earlier expiry, or the selection emptying).
     */
    private fun runHoldCountdown() {
        viewModelScope.launch {
            uiState.map { it.holdExpiresAtEpochMillis }.distinctUntilChanged().collectLatest { expiry ->
                savedStateHandle[KEY_HOLD_EXPIRES_AT] = expiry
                if (expiry == null) {
                    _uiState.update { it.copy(holdSecondsRemaining = null) }
                    return@collectLatest
                }
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val remaining = ((expiry - now + 999) / 1_000).toInt().coerceAtLeast(0)
                    _uiState.update { it.copy(holdSecondsRemaining = remaining) }
                    if (remaining == 0) {
                        onHoldExpired()
                        return@collectLatest
                    }
                    delay(1_000 - now % 1_000)
                }
            }
        }
    }

    /**
     * Do not silently clear: the rider may be mid-thought on the seat map, so
     * the dialog explains what happened and the selection is only released
     * when they acknowledge it. A restored expiry with nothing selected (the
     * app was gone for longer than the hold) just clears.
     */
    private fun onHoldExpired() {
        _uiState.update { state ->
            if (state.selectedSeatIds.isEmpty()) {
                state.copy(holdExpiresAtEpochMillis = null, holdSecondsRemaining = null)
            } else {
                state.copy(holdExpired = true, holdSecondsRemaining = 0)
            }
        }
    }

    /** "Pick again" on the expiry dialog: give every held seat back and start over. */
    fun onHoldExpiredAcknowledged() {
        viewModelScope.launch {
            val held = _uiState.value.selectedSeatIds
            releaseQuietly(held)
            _uiState.update { state ->
                state.copy(
                    selectedSeatIds = emptyList(),
                    holdExpired = false,
                    holdExpiresAtEpochMillis = null,
                    holdSecondsRemaining = null,
                    seats = state.seats.markAvailable(held),
                )
            }
        }
    }

    /**
     * The hold window for the whole selection is the earliest server expiry
     * among the held seats: when the first one lapses the party can no longer
     * sit together, so the rider must re-pick. Falls back to the previous
     * expiry (e.g. restored from SavedStateHandle) and only then to the
     * documented TTL.
     */
    private fun SeatMapUiState.withHoldExpiry(): SeatMapUiState {
        if (selectedSeatIds.isEmpty()) return copy(holdExpiresAtEpochMillis = null)
        val serverExpiry = seats
            .filter { it.id in selectedSeatIds }
            .mapNotNull { it.lockExpiresAtEpochMillis }
            .minOrNull()
        return copy(
            holdExpiresAtEpochMillis = serverExpiry
                ?: holdExpiresAtEpochMillis
                ?: (System.currentTimeMillis() + FALLBACK_HOLD_MILLIS),
        )
    }

    // ---- Helpers -----------------------------------------------------------

    /** Releases [seatIds] server-side without treating the echoed AVAILABLE events as lost holds. */
    private fun releaseQuietly(seatIds: List<String>) {
        if (seatIds.isEmpty()) return
        pendingReleases.addAll(seatIds)
        viewModelScope.launch { seatIds.forEach { releaseSeatUseCase(tripId, it) } }
    }

    private fun List<Seat>.markAvailable(seatIds: Collection<String>): List<Seat> =
        if (seatIds.isEmpty()) {
            this
        } else {
            map {
                if (it.id in seatIds) {
                    it.copy(status = SeatStatus.AVAILABLE, lockedByUserId = null, lockExpiresAtEpochMillis = null)
                } else {
                    it
                }
            }
        }

    override fun onCleared() {
        observeJob?.cancel()
        // Screen popped without completing checkout: give held seats back so
        // they aren't locked forever, then close the socket. The repository
        // ignores the release for seats that were meanwhile booked (OCCUPIED)
        // or locked by someone else, so it is safe to fire after a successful
        // booking too. viewModelScope is already cancelled here, hence the
        // one-shot scope.
        val held = _uiState.value.selectedSeatIds
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            held.forEach { releaseSeatUseCase(tripId, it) }
            seatRepository.disconnect(tripId)
        }
        super.onCleared()
    }
}
