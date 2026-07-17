package com.ridevibe.app.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingsUiState(
    val isLoading: Boolean = true,
    val upcoming: List<Ticket> = emptyList(),
    val past: List<Ticket> = emptyList(),
)

// CheckoutRepository is injected directly (no use-case layer): a plain list
// fetch with a time-based split, no domain logic to encapsulate yet.
@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val checkoutRepository: CheckoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingsUiState())
    val uiState: StateFlow<BookingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val now = System.currentTimeMillis()
            val (upcoming, past) = checkoutRepository.getMyBookings()
                .partition { it.trip.departureEpochMillis >= now }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    upcoming = upcoming.sortedBy { t -> t.trip.departureEpochMillis },
                    past = past.sortedByDescending { t -> t.trip.departureEpochMillis },
                )
            }
        }
    }
}
