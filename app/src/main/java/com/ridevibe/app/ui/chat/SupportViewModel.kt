package com.ridevibe.app.ui.chat

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

/** A support topic row: title + one-line description (design page 8). The screen maps [id] to an icon. */
data class SupportTopic(val id: String, val title: String, val description: String)

data class SupportUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val topics: List<SupportTopic> = emptyList(),
    val bookings: List<Ticket> = emptyList(),
    /** The booking the request is about; null = none attached (no bookings, or the rider opted out). */
    val selectedBooking: Ticket? = null,
)

// CheckoutRepository is injected directly (no use-case layer): a plain list
// fetch to pick which booking a support request is about.
@HiltViewModel
class SupportViewModel @Inject constructor(
    private val checkoutRepository: CheckoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportUiState(topics = TOPICS))
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val now = System.currentTimeMillis()
                // Soonest upcoming trip first: the booking a request is most likely about.
                val bookings = (checkoutRepository.getMyBookings().getOrNull() ?: checkoutRepository.cachedBookings())
                    .sortedWith(compareBy({ it.trip.departureEpochMillis < now }, { it.trip.departureEpochMillis }))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bookings = bookings,
                        selectedBooking = it.selectedBooking ?: bookings.firstOrNull(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load your bookings") }
            }
        }
    }

    fun onBookingSelected(booking: Ticket) = _uiState.update { it.copy(selectedBooking = booking) }

    /** "Continue without a booking": the chat opens with the topic only. */
    fun clearSelectedBooking() = _uiState.update { it.copy(selectedBooking = null) }

    companion object {
        const val TOPIC_REBOOK = "rebook"
        const val TOPIC_REFUND = "refund"
        const val TOPIC_PAYMENT = "payment"
        const val TOPIC_SEAT = "seat"
        const val TOPIC_LOST_ITEM = "lost_item"
        const val TOPIC_REPORT = "report"
        const val TOPIC_OTHER = "other"

        /** Fixed for now; becomes server-driven when the support backend lands. */
        val TOPICS = listOf(
            SupportTopic(TOPIC_REBOOK, "Rebook a trip", "Move to another date or departure time"),
            SupportTopic(TOPIC_REFUND, "Request a refund", "Cancelled or missed trip"),
            SupportTopic(TOPIC_PAYMENT, "Payment or wallet issue", "Failed charge, missing credits, promo code"),
            SupportTopic(TOPIC_SEAT, "Seat or ticket problem", "Wrong seat, QR not scanning, name change"),
            SupportTopic(TOPIC_LOST_ITEM, "Lost item", "Left something on board"),
            SupportTopic(TOPIC_REPORT, "Report a conductor or bus", "Safety, conduct, or vehicle condition"),
            SupportTopic(TOPIC_OTHER, "Something else", "Describe it in your own words"),
        )
    }
}

/** Booking reference shown on the context card and attached to the chat. */
fun Ticket.supportLabel(): String = "$id · ${trip.origin} → ${trip.destination}"
