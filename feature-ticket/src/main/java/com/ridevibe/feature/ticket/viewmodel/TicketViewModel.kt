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
    val ticket: Ticket? = null,
    /** Seconds until an unpaid reservation lapses; null when paid or no expiry. */
    val expirySecondsRemaining: Long? = null,
    val errorMessage: String? = null,
) {
    val isReservation: Boolean get() = ticket?.paymentStatus == PaymentStatus.CASH_ON_BOARD
}

@HiltViewModel
class TicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTicketUseCase: GetTicketUseCase,
) : ViewModel() {

    private val ticketId: String = checkNotNull(savedStateHandle["ticketId"])

    private val _uiState = MutableStateFlow(TicketUiState())
    val uiState: StateFlow<TicketUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getTicketUseCase(ticketId)
                .onSuccess { ticket ->
                    _uiState.update { it.copy(isLoading = false, ticket = ticket) }
                    startExpiryCountdown(ticket)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.message ?: "Unable to load ticket")
                    }
                }
        }
    }

    private fun startExpiryCountdown(ticket: Ticket) {
        countdownJob?.cancel()
        val expiresAt = ticket.reservationExpiresAtEpochMillis ?: return
        if (ticket.paymentStatus != PaymentStatus.CASH_ON_BOARD) return

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
