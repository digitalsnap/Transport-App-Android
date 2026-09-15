package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.NewService
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.ServiceUpdate
import com.ridevibe.core.domain.repository.PartnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerServicesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val services: List<OperatorService> = emptyList(),
    /** Create or edit in flight. */
    val isSaving: Boolean = false,
    /** Error from the last create/edit call, shown inline on the form. */
    val formError: String? = null,
    /** Service open in the edit sheet (fare / hours / duration only). */
    val editing: OperatorService? = null,
    /** Increments after a successful create so the add form can reset itself. */
    val createdCount: Int = 0,
    val message: String? = null,
)

/** "My services": what the operator runs; creates services and edits fare/hours/duration. */
@HiltViewModel
class PartnerServicesViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerServicesUiState())
    val uiState: StateFlow<PartnerServicesUiState> = _uiState.asStateFlow()

    private var operatorId: Int? = null
    private var started = false

    fun start(operatorId: Int?) {
        if (started && this.operatorId == operatorId) return
        started = true
        this.operatorId = operatorId
        _uiState.value = PartnerServicesUiState()
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            partnerRepository.getServices(operatorId)
                .onSuccess { services -> _uiState.update { it.copy(isLoading = false, services = services) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Unable to load services") }
                }
        }
    }

    /** Creates the service; trips for the next 14 days generate immediately. */
    fun createService(service: NewService) {
        if (service.departureHours.isEmpty()) {
            _uiState.update { it.copy(formError = "Pick at least one departure hour") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, formError = null) }
            partnerRepository.createService(service)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            createdCount = it.createdCount + 1,
                            message = "Service created — ${created.generatedTrips} trips / ${created.generatedSeats} seats generated",
                        )
                    }
                    load()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSaving = false, formError = throwable.message ?: "Unable to create service") }
                }
        }
    }

    fun openEdit(service: OperatorService) = _uiState.update { it.copy(editing = service, formError = null) }

    fun closeEdit() = _uiState.update { it.copy(editing = null, formError = null) }

    /** Applies to future departures with no sold seats only; reports repriced/removed/added counts. */
    fun saveEdit(update: ServiceUpdate) {
        val service = _uiState.value.editing ?: return
        if (update.isEmpty) {
            _uiState.update { it.copy(formError = "Change the fare, hours or duration first") }
            return
        }
        if (update.departureHours?.isEmpty() == true) {
            _uiState.update { it.copy(formError = "Pick at least one departure hour") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, formError = null) }
            partnerRepository.updateService(service.id, update)
                .onSuccess { changed ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            editing = null,
                            message = "Saved — repriced ${changed.repricedTrips}, removed ${changed.removedTrips}, " +
                                "added ${changed.addedTrips} trips",
                        )
                    }
                    load()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSaving = false, formError = throwable.message ?: "Unable to save changes") }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
