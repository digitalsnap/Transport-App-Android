package com.ridevibe.feature.ticket.viewmodel

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.usecase.GetTicketUseCase
import com.ridevibe.feature.ticket.format.TicketFormatter
import com.ridevibe.feature.ticket.share.TicketExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One leg of the itinerary: loaded, still loading, or failed (retryable on its own). */
data class TicketLeg(
    val ticketId: String,
    val ticket: Ticket? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** "Outbound" / "Return" / "Leg N", decided by route shape once loaded. */
    val label: String = "",
)

data class TicketUiState(
    /** True until every leg has resolved once; per-leg reloads use [TicketLeg.isLoading]. */
    val isLoading: Boolean = true,
    /** One entry per ticket id in the route — a round trip has two. */
    val legs: List<TicketLeg> = emptyList(),
    /** Seconds until an unpaid reservation lapses; null when paid or no expiry. */
    val expirySecondsRemaining: Long? = null,
    /** The cash-on-board hold ran out: the QR is no longer valid. */
    val reservationExpired: Boolean = false,
    /** Route arguments missing or malformed; nothing to load. */
    val errorMessage: String? = null,
    val boostBrightness: Boolean = false,
    val isExporting: Boolean = false,
    /** One-shot Snackbar text; cleared with [TicketViewModel.onMessageShown]. */
    val snackbarMessage: String? = null,
    /** One-shot: the screen launches it (with a chooser) and calls [TicketViewModel.onShareHandled]. */
    val shareIntent: Intent? = null,
    /** One-shot: the screen launches it and calls [TicketViewModel.onCalendarHandled]. */
    val calendarIntent: Intent? = null,
) {
    val tickets: List<Ticket> get() = legs.mapNotNull { it.ticket }
    val isReservation: Boolean get() = tickets.any { it.paymentStatus == PaymentStatus.CASH_ON_BOARD }

    /** Support cancelled or refunded any leg: the whole itinerary is off. */
    val cancelledStatus: BookingStatus?
        get() = tickets.map { it.status }.firstOrNull { it != BookingStatus.CONFIRMED }

    /** Share, save and the QR only make sense for a live ticket. */
    val actionsEnabled: Boolean
        get() = tickets.isNotEmpty() && cancelledStatus == null && !reservationExpired && !isExporting

    /** What greys out the QR, if anything. */
    val qrOverlay: String?
        get() = when {
            cancelledStatus == BookingStatus.CANCELLED -> "CANCELLED"
            cancelledStatus == BookingStatus.REFUNDED -> "REFUNDED"
            reservationExpired -> "LAPSED"
            else -> null
        }
}

@HiltViewModel
class TicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTicketUseCase: GetTicketUseCase,
    private val exporter: TicketExporter,
) : ViewModel() {

    private val ticketIds: List<String> =
        savedStateHandle.get<String>("ticketIds")?.split(",")?.filter { it.isNotBlank() }.orEmpty()

    private val _uiState = MutableStateFlow(
        if (ticketIds.isEmpty()) {
            TicketUiState(isLoading = false, errorMessage = "No ticket to show")
        } else {
            TicketUiState(legs = ticketIds.map { TicketLeg(ticketId = it) })
        },
    )
    val uiState: StateFlow<TicketUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        if (ticketIds.isNotEmpty()) load()
    }

    /** Loads every leg in parallel; one failing never hides the others. */
    fun load() {
        if (ticketIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, legs = state.legs.map { it.copy(isLoading = true, error = null) })
            }
            ticketIds.indices.map { index -> async { fetchLeg(index) } }.awaitAll()
            _uiState.update { it.copy(isLoading = false) }
            startExpiryCountdown()
        }
    }

    /** One leg failed while the other loaded: retry just that one. */
    fun retryLeg(index: Int) {
        if (index !in ticketIds.indices) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(legs = state.legs.replaceAt(index) { it.copy(isLoading = true, error = null) })
            }
            fetchLeg(index)
            startExpiryCountdown()
        }
    }

    private suspend fun fetchLeg(index: Int) {
        val result = getTicketUseCase(ticketIds[index])
        _uiState.update { state ->
            val legs = state.legs.replaceAt(index) { leg ->
                result.fold(
                    onSuccess = { leg.copy(ticket = it, isLoading = false, error = null) },
                    onFailure = { leg.copy(isLoading = false, error = it.message ?: "Unable to load ticket") },
                )
            }
            state.copy(legs = relabel(legs))
        }
    }

    private fun relabel(legs: List<TicketLeg>): List<TicketLeg> {
        val first = legs.firstOrNull()?.ticket
        return legs.mapIndexed { index, leg -> leg.copy(label = TicketFormatter.legLabel(index, leg.ticket, first)) }
    }

    private fun startExpiryCountdown() {
        countdownJob?.cancel()
        val expiresAt = _uiState.value.tickets
            .filter { it.paymentStatus == PaymentStatus.CASH_ON_BOARD && it.status == BookingStatus.CONFIRMED }
            .mapNotNull { it.reservationExpiresAtEpochMillis }
            .minOrNull()
        if (expiresAt == null) {
            _uiState.update { it.copy(expirySecondsRemaining = null, reservationExpired = false) }
            return
        }
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val remaining = ((expiresAt - System.currentTimeMillis()) / 1_000).coerceAtLeast(0)
                _uiState.update { it.copy(expirySecondsRemaining = remaining, reservationExpired = remaining == 0L) }
                if (remaining == 0L) break
                delay(1_000)
            }
        }
    }

    fun toggleBrightness() = _uiState.update { it.copy(boostBrightness = !it.boostBrightness) }

    // ---- Export actions ---------------------------------------------------------------------

    fun share() {
        val state = _uiState.value
        if (!state.actionsEnabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val loaded = state.legs.filter { it.ticket != null }
            val uris = loaded.mapNotNull { leg -> exporter.writeShareFile(leg.ticket!!, leg.label.takeIf { loaded.size > 1 }) }
            val text = TicketFormatter.shareText(loaded.map { it.ticket!! }, loaded.map { it.label })
            _uiState.update {
                if (uris.isEmpty()) {
                    it.copy(isExporting = false, snackbarMessage = "Couldn't prepare the ticket image to share.")
                } else {
                    it.copy(isExporting = false, shareIntent = exporter.shareIntent(uris, text))
                }
            }
        }
    }

    fun onShareHandled() = _uiState.update { it.copy(shareIntent = null) }

    /** True when the screen must request WRITE_EXTERNAL_STORAGE first (API 24–28 only). */
    fun needsStoragePermission(): Boolean = exporter.needsLegacyStoragePermission()

    fun saveToDevice() {
        val state = _uiState.value
        if (!state.actionsEnabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val loaded = state.legs.filter { it.ticket != null }
            val results = loaded.map { leg -> exporter.saveToPictures(leg.ticket!!, leg.label.takeIf { loaded.size > 1 }) }
            val failure = results.firstOrNull { it.isFailure }
            val message = if (failure == null) {
                val where = results.first().getOrThrow()
                if (results.size == 1) "Saved to $where" else "Saved ${results.size} tickets to Pictures/RideVibe"
            } else {
                failure.exceptionOrNull()?.message ?: "Couldn't save the ticket."
            }
            _uiState.update { it.copy(isExporting = false, snackbarMessage = message) }
        }
    }

    fun addToCalendar(index: Int = 0) {
        val ticket = _uiState.value.legs.getOrNull(index)?.ticket ?: return
        _uiState.update { it.copy(calendarIntent = exporter.calendarIntent(ticket)) }
    }

    fun onCalendarHandled() = _uiState.update { it.copy(calendarIntent = null) }

    /** The screen reports what it could not do (no share target, no calendar app, permission denied). */
    fun showMessage(message: String) = _uiState.update { it.copy(snackbarMessage = message) }

    fun onMessageShown() = _uiState.update { it.copy(snackbarMessage = null) }

    private fun List<TicketLeg>.replaceAt(index: Int, transform: (TicketLeg) -> TicketLeg): List<TicketLeg> =
        mapIndexed { i, leg -> if (i == index) transform(leg) else leg }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
