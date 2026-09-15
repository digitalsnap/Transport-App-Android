package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.ManifestEntry
import com.ridevibe.core.domain.repository.PartnerRepository
import com.ridevibe.feature.admin.ui.shiftIsoDay
import com.ridevibe.feature.admin.ui.todayPhIso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerManifestUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val dateIso: String = todayPhIso(),
    val entries: List<ManifestEntry> = emptyList(),
)

/** Boarding manifest: the day's bookings across the operator's departures. */
@HiltViewModel
class PartnerManifestViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerManifestUiState())
    val uiState: StateFlow<PartnerManifestUiState> = _uiState.asStateFlow()

    private var operatorId: Int? = null
    private var started = false

    fun start(operatorId: Int?) {
        if (started && this.operatorId == operatorId) return
        started = true
        this.operatorId = operatorId
        _uiState.value = PartnerManifestUiState()
        load()
    }

    fun previousDay() = changeDay(-1)

    fun nextDay() = changeDay(+1)

    private fun changeDay(delta: Int) {
        _uiState.update { it.copy(dateIso = shiftIsoDay(it.dateIso, delta)) }
        load()
    }

    fun load() {
        val dateIso = _uiState.value.dateIso
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            partnerRepository.getManifest(dateIso = dateIso, operatorId = operatorId)
                .onSuccess { entries -> _uiState.update { it.copy(isLoading = false, entries = entries) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Unable to load the manifest") }
                }
        }
    }
}
