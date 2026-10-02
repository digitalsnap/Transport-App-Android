package com.ridevibe.app.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingsUiState(
    /** First load with nothing to show yet. */
    val isLoading: Boolean = true,
    /** A refresh (pull or resume) while a list is already on screen. */
    val isRefreshing: Boolean = false,
    /** Fetch failed and there was nothing cached to fall back on. */
    val error: String? = null,
    /** Fetch failed but the last successful list is being shown from the ticket cache. */
    val isOffline: Boolean = false,
    val upcoming: List<Ticket> = emptyList(),
    val past: List<Ticket> = emptyList(),
)

// CheckoutRepository is injected directly (no use-case layer): a list fetch
// with a date split, no domain logic to encapsulate yet.
@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val checkoutRepository: CheckoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingsUiState())
    val uiState: StateFlow<BookingsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /**
     * Called on first show, on resume (a ticket bought meanwhile must appear)
     * and on pull-to-refresh. ON_RESUME also fires right after the first
     * composition, so an in-flight load is simply left to finish.
     */
    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val hasList = _uiState.value.upcoming.isNotEmpty() || _uiState.value.past.isNotEmpty()
            _uiState.update { it.copy(isLoading = !hasList, isRefreshing = hasList, error = null) }
            try {
                val tickets = checkoutRepository.getMyBookings().getOrThrow()
                _uiState.update { it.copy(isOffline = false).withTickets(tickets) }
            } catch (e: Exception) {
                // A rider at the terminal with no signal still needs the QR: show what we last fetched.
                val cached = checkoutRepository.cachedBookings()
                _uiState.update {
                    if (cached.isNotEmpty()) {
                        it.copy(isOffline = true).withTickets(cached)
                    } else {
                        it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Could not load your bookings")
                    }
                }
            }
        }
    }

    /**
     * "Upcoming" is anything departing on today's PH calendar day or later, so
     * a trip the rider is about to board (or just boarded) stays at the top
     * until the day ends instead of dropping into history at departure time.
     */
    private fun BookingsUiState.withTickets(tickets: List<Ticket>): BookingsUiState {
        val todayStart = PhTime.todayStartMillis()
        val (upcoming, past) = tickets.partition { it.trip.departureEpochMillis >= todayStart }
        return copy(
            isLoading = false,
            isRefreshing = false,
            error = null,
            upcoming = upcoming.sortedBy { t -> t.trip.departureEpochMillis },
            past = past.sortedByDescending { t -> t.trip.departureEpochMillis },
        )
    }
}
