package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
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

/** The API has `limit` but no offset, so one page is all there is; past it we ask for a narrower search. */
const val SUPPORT_PAGE_SIZE = 50

/** Shortcuts from the Overview tiles: land on Support with the matching filter already applied. */
enum class SupportFilterPreset { CANCELLED, REFUNDS_PENDING }

data class AdminSupportUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    /** Null = "All". REFUNDED is not a server status — see [AdminSupportViewModel.search]. */
    val statusFilter: BookingStatus? = null,
    /** Null = any refund state. Applied client-side over the page. */
    val refundFilter: RefundStatus? = null,
    /** Booking-date window (PH days, inclusive), applied client-side. */
    val dateFromIso: String? = null,
    val dateToIso: String? = null,
    /** The page as the server returned it. */
    val results: List<SupportBooking> = emptyList(),
    val hasSearched: Boolean = false,
    /** Detail sheet: the booking from `getBooking` (includes co-passengers). */
    val selected: SupportBooking? = null,
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    val isActing: Boolean = false,
    /** One-shot snackbar text; cleared with [AdminSupportViewModel.consumeMessage]. */
    val message: String? = null,
) {
    val showDetail: Boolean get() = selected != null || isDetailLoading

    /** True when the server page was full: there may be more rows than we can show. */
    val isTruncated: Boolean get() = results.size >= SUPPORT_PAGE_SIZE

    /** [results] after the client-side filters (refund state, REFUNDED chip, date window). */
    val filteredResults: List<SupportBooking>
        get() {
            val from = dateFromIso?.let { PhTime.dayRange(it) }?.first
            val to = dateToIso?.let { PhTime.dayRange(it) }?.last
            return results.filter { booking ->
                (statusFilter != BookingStatus.REFUNDED || booking.refundStatus == RefundStatus.REFUNDED) &&
                    (refundFilter == null || booking.refundStatus == refundFilter) &&
                    (from == null || booking.createdAtEpochMillis >= from) &&
                    (to == null || booking.createdAtEpochMillis <= to)
            }
        }
}

/** Customer support: find a booking, cancel it, record refunds, reissue QR tickets. */
@HiltViewModel
class AdminSupportViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminSupportUiState())
    val uiState: StateFlow<AdminSupportUiState> = _uiState.asStateFlow()

    private var sessionKey: String? = null

    fun start(sessionKey: String) {
        if (this.sessionKey == sessionKey) return
        this.sessionKey = sessionKey
        _uiState.value = AdminSupportUiState()
        search()
    }

    /** Overview tile shortcut; replaces the current filters and searches again. */
    fun applyPreset(preset: SupportFilterPreset) {
        _uiState.update {
            when (preset) {
                SupportFilterPreset.CANCELLED -> it.copy(statusFilter = BookingStatus.CANCELLED, refundFilter = null)
                SupportFilterPreset.REFUNDS_PENDING -> it.copy(statusFilter = null, refundFilter = RefundStatus.REQUESTED)
            }
        }
        search()
    }

    fun onQueryChanged(value: String) = _uiState.update { it.copy(query = value) }

    fun onStatusFilterChanged(status: BookingStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
        search()
    }

    /** Client-side: no round trip needed. */
    fun onRefundFilterChanged(status: RefundStatus?) = _uiState.update { it.copy(refundFilter = status) }

    fun onDateFromChanged(isoDay: String?) = _uiState.update { it.copy(dateFromIso = isoDay) }

    fun onDateToChanged(isoDay: String?) = _uiState.update { it.copy(dateToIso = isoDay) }

    fun clearDateRange() = _uiState.update { it.copy(dateFromIso = null, dateToIso = null) }

    fun search() {
        val state = _uiState.value
        // The staff API only knows CONFIRMED / CANCELLED. REFUNDED is derived from refundStatus,
        // so that chip fetches everything and filteredResults narrows it down.
        val serverStatus = state.statusFilter?.takeIf { it != BookingStatus.REFUNDED }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            adminRepository.searchBookings(query = state.query.trim(), status = serverStatus, limit = SUPPORT_PAGE_SIZE)
                .onSuccess { rows -> _uiState.update { it.copy(isLoading = false, results = rows, hasSearched = true) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, hasSearched = true, error = throwable.staffMessage("Search failed")) }
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
            .onSuccess { booking ->
                _uiState.update { state ->
                    // Keep the list row in step with the detail without re-running the search.
                    state.copy(
                        isDetailLoading = false,
                        selected = booking,
                        results = state.results.map { if (it.id == booking.id) booking else it },
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(isDetailLoading = false, detailError = throwable.staffMessage("Unable to load booking"))
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
        // A status change can move the row out of the current status filter, so the list reloads too.
        mutate(booking.id, refreshList = true) {
            adminRepository.cancelBooking(booking.id, reason.trim())
                .map { freed -> "Cancelled — freed seats: ${freed.joinToString(", ").ifBlank { "none" }}" }
        }
    }

    /** Bookkeeping only — no payment provider is integrated. */
    fun setRefundStatus(status: RefundStatus, note: String) {
        val booking = _uiState.value.selected ?: return
        mutate(booking.id, refreshList = false) {
            adminRepository.setRefundStatus(booking.id, status, note.trim().ifBlank { null })
                .map { applied -> "Refund status: ${applied.name} (bookkeeping only — no PSP integrated)" }
        }
    }

    fun reissueQr() {
        val booking = _uiState.value.selected ?: return
        mutate(booking.id, refreshList = false) {
            adminRepository.reissueQr(booking.id).map { "New QR payload issued" }
        }
    }

    private fun mutate(bookingId: String, refreshList: Boolean, action: suspend () -> Result<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, detailError = null) }
            action()
                .onSuccess { message ->
                    _uiState.update { it.copy(isActing = false, message = message) }
                    loadDetail(bookingId)
                    if (refreshList) search()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isActing = false, detailError = throwable.staffMessage("Action failed")) }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
