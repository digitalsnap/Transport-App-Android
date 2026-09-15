package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.core.domain.model.SupportBooking
import com.ridevibe.core.domain.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminSupportUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    /** Null = "All". */
    val statusFilter: BookingStatus? = null,
    val results: List<SupportBooking> = emptyList(),
    val hasSearched: Boolean = false,
    /** Detail sheet: the booking from `getBooking` (includes co-passengers). */
    val selected: SupportBooking? = null,
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    val isActing: Boolean = false,
    /** One-shot snackbar text; cleared with [consumeMessage]. */
    val message: String? = null,
) {
    val showDetail: Boolean get() = selected != null || isDetailLoading
}

/** Customer support: find a booking, cancel it, record refunds, reissue QR tickets. */
@HiltViewModel
class AdminSupportViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminSupportUiState())
    val uiState: StateFlow<AdminSupportUiState> = _uiState.asStateFlow()

    init {
        search()
    }

    fun onQueryChanged(value: String) = _uiState.update { it.copy(query = value) }

    fun onStatusFilterChanged(status: BookingStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
        search()
    }

    fun search() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            adminRepository.searchBookings(query = state.query.trim(), status = state.statusFilter, limit = 50)
                .onSuccess { rows -> _uiState.update { it.copy(isLoading = false, results = rows, hasSearched = true) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, hasSearched = true, error = throwable.message ?: "Search failed") }
                }
        }
    }

    fun openBooking(bookingId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, detailError = null) }
            loadDetail(bookingId)
        }
    }

    fun closeDetail() = _uiState.update { it.copy(selected = null, isDetailLoading = false, detailError = null) }

    private suspend fun loadDetail(bookingId: String) {
        adminRepository.getBooking(bookingId)
            .onSuccess { booking -> _uiState.update { it.copy(isDetailLoading = false, selected = booking) } }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(isDetailLoading = false, detailError = throwable.message ?: "Unable to load booking")
                }
            }
    }

    /** Frees the seats back into inventory; [reason] is required by the API. */
    fun cancelBooking(reason: String) {
        val booking = _uiState.value.selected ?: return
        if (reason.isBlank()) {
            _uiState.update { it.copy(message = "Enter a cancellation reason first.") }
            return
        }
        mutate(booking.id) {
            adminRepository.cancelBooking(booking.id, reason.trim())
                .map { freed -> "Cancelled — freed seats: ${freed.joinToString(", ").ifBlank { "none" }}" }
        }
    }

    /** Bookkeeping only — no payment provider is integrated. */
    fun setRefundStatus(status: RefundStatus, note: String) {
        val booking = _uiState.value.selected ?: return
        mutate(booking.id) {
            adminRepository.setRefundStatus(booking.id, status, note.trim().ifBlank { null })
                .map { applied -> "Refund status: ${applied.name} (bookkeeping only — no PSP integrated)" }
        }
    }

    fun reissueQr() {
        val booking = _uiState.value.selected ?: return
        mutate(booking.id) {
            adminRepository.reissueQr(booking.id).map { "New QR payload issued" }
        }
    }

    private fun mutate(bookingId: String, action: suspend () -> Result<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, detailError = null) }
            action()
                .onSuccess { message ->
                    _uiState.update { it.copy(isActing = false, message = message) }
                    loadDetail(bookingId)
                    search()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isActing = false, detailError = throwable.message ?: "Action failed") }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
