package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.AdminOverview
import com.ridevibe.core.domain.model.SupportBooking
import com.ridevibe.core.domain.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminOverviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val overview: AdminOverview? = null,
    /** `searchBookings(limit = 8)` — the "Recent bookings" table. */
    val recentBookings: List<SupportBooking> = emptyList(),
) {
    /** Spoken form of the bar chart: "Bookings per day: Mon 12, Tue 8, …". */
    val chartDescription: String
        get() = overview?.bookingsByDay
            ?.joinToString(prefix = "Bookings per day: ", separator = ", ") { "${PhTime.formatIsoDay(it.day, "EEE")} ${it.count}" }
            ?: "Bookings per day: no data"
}

@HiltViewModel
class AdminOverviewViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminOverviewUiState())
    val uiState: StateFlow<AdminOverviewUiState> = _uiState.asStateFlow()

    private var sessionKey: String? = null

    /** Binds the tab to the signed-in session; a different account gets a clean slate. */
    fun start(sessionKey: String) {
        if (this.sessionKey == sessionKey) return
        this.sessionKey = sessionKey
        _uiState.value = AdminOverviewUiState()
        load()
    }

    /** Re-selecting the tab reloads quietly, keeping the old numbers on screen meanwhile. */
    fun refreshOnResume() {
        if (sessionKey == null || _uiState.value.isLoading) return
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val overview = adminRepository.getOverview()
            overview.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, error = throwable.staffMessage("Unable to load the overview")) }
                return@launch
            }
            val recent = adminRepository.searchBookings(limit = 8)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    overview = overview.getOrNull(),
                    recentBookings = recent.getOrDefault(it.recentBookings),
                    error = recent.exceptionOrNull()?.staffMessage("Unable to load recent bookings"),
                )
            }
        }
    }
}
