package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

@HiltViewModel
class AdminOverviewViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminOverviewUiState())
    val uiState: StateFlow<AdminOverviewUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val overview = adminRepository.getOverview()
            overview.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Unable to load overview") }
                return@launch
            }
            val recent = adminRepository.searchBookings(limit = 8)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    overview = overview.getOrNull(),
                    recentBookings = recent.getOrDefault(emptyList()),
                    error = recent.exceptionOrNull()?.message,
                )
            }
        }
    }
}
