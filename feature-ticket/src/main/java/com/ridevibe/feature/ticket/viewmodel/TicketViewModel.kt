package com.ridevibe.feature.ticket.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.usecase.GetTicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TicketUiState(
    val isLoading: Boolean = true,
    /** One ticket per leg — a round trip shows two (outbound then return). */
    val tickets: List<Ticket> = emptyList(),
    /** Seconds until an unpaid reservation lapses; null when paid or no expiry. */
    val expirySecondsRemaining: Long? = null,
    val errorMessage: String? = null,
) {
    val isReservation: Boolean get() = tickets.any { it.paymentStatus == PaymentStatus.CASH_ON_BOARD }
}

@HiltViewModel
class TicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTicketUseCase: GetTicketUseCase,
) : ViewModel() {

    private val ticketIds: List<String> =
        checkNotNull(savedStateHandle.get<String>("ticketIds")).split(",").filter { it.isNotBlank() }

    private val _uiState = MutableStateFlow(TicketUiState())
    val uiState: StateFlow<TicketUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val tickets = mutableListOf<Ticket>()
            for (id in ticketIds) {
                getTicketUseCase(id)
                    .onSuccess { tickets += it }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = throwable.message ?: "Unable to load ticket")
                        }
                        return@launch
                    }
            }
            // Outbound first for round trips.
            val sorted = tickets.sortedBy { it.trip.departureEpochMillis }
            _uiState.update { it.copy(isLoading = false, tickets = sorted) }
            startExpiryCountdown(sorted)
        }
    }

    private fun startExpiryCountdown(tickets: List<Ticket>) {
        countdownJob?.cancel()
        val expiresAt = tickets
            .filter { it.paymentStatus == PaymentStatus.CASH_ON_BOARD }
            .mapNotNull { it.reservationExpiresAtEpochMillis }
            .minOrNull() ?: return

        countdownJob = viewModelScope.launch {
            while (true) {
                val remaining = (expiresAt - System.currentTimeMillis()) / 1_000
                _uiState.update { it.copy(expirySecondsRemaining = remaining.coerceAtLeast(0)) }
                if (remaining <= 0) break
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
