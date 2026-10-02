package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.AccountCredential
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.OperatorSummary
import com.ridevibe.core.domain.model.PartnerTokenIssued
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PartnerSort(val label: String) {
    NAME("Name"),
    SERVICES("Most services"),
    RATING("Best rated"),
}

data class AdminPartnersUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val operators: List<OperatorSummary> = emptyList(),
    val query: String = "",
    val sort: PartnerSort = PartnerSort.NAME,
    /** Detail sheet for one operator with its services and seating. */
    val selected: OperatorSummary? = null,
    val services: List<OperatorService> = emptyList(),
    val isServicesLoading: Boolean = false,
    val servicesError: String? = null,
    val isCreatingSignIn: Boolean = false,
    val isIssuingToken: Boolean = false,
    /** Shown once: the generated partner password. */
    val issuedCredential: AccountCredential? = null,
    /** Shown once: the rotated legacy `pt_…` token. */
    val issuedToken: PartnerTokenIssued? = null,
    val message: String? = null,
) {
    val isActing: Boolean get() = isCreatingSignIn || isIssuingToken

    /** [operators] after the search box and sort — both local, the list is small. */
    val shownOperators: List<OperatorSummary>
        get() {
            val needle = query.trim().lowercase()
            val matching = operators.filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
            return when (sort) {
                PartnerSort.NAME -> matching.sortedBy { it.name.lowercase() }
                PartnerSort.SERVICES -> matching.sortedByDescending { it.services }
                PartnerSort.RATING -> matching.sortedByDescending { it.avgRating ?: -1.0 }
            }
        }
}

/** Partners: bus lines, ferries and fastcraft operators — their services, sign-ins and tokens. */
@HiltViewModel
class AdminPartnersViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPartnersUiState())
    val uiState: StateFlow<AdminPartnersUiState> = _uiState.asStateFlow()

    private var sessionKey: String? = null

    fun start(sessionKey: String) {
        if (this.sessionKey == sessionKey) return
        this.sessionKey = sessionKey
        _uiState.value = AdminPartnersUiState()
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            adminRepository.getOperators()
                .onSuccess { operators -> _uiState.update { it.copy(isLoading = false, operators = operators) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.staffMessage("Unable to load partners")) }
                }
        }
    }

    /** Used by the partner-portal picker and company switcher; cheap when already loaded. */
    fun ensureOperatorsLoaded(sessionKey: String) {
        if (this.sessionKey != sessionKey) {
            start(sessionKey)
            return
        }
        if (_uiState.value.operators.isEmpty() && !_uiState.value.isLoading) load()
    }

    fun onQueryChanged(value: String) = _uiState.update { it.copy(query = value) }

    fun onSortChanged(sort: PartnerSort) = _uiState.update { it.copy(sort = sort) }

    fun openOperator(operator: OperatorSummary) {
        _uiState.update { it.copy(selected = operator, services = emptyList(), servicesError = null) }
        loadServices(operator.id)
    }

    fun reloadServices() {
        val operator = _uiState.value.selected ?: return
        loadServices(operator.id)
    }

    private fun loadServices(operatorId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isServicesLoading = true, servicesError = null) }
            adminRepository.getOperatorServices(operatorId)
                .onSuccess { services -> _uiState.update { it.copy(isServicesLoading = false, services = services) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isServicesLoading = false, servicesError = throwable.staffMessage("Unable to load services"))
                    }
                }
        }
    }

    fun closeOperator() = _uiState.update { it.copy(selected = null, services = emptyList(), servicesError = null) }

    /** Email + password account for this company's staff; omit the password to have one generated (shown once). */
    fun createPartnerSignIn(email: String, password: String?) {
        val operator = _uiState.value.selected ?: return
        if (email.isBlank()) {
            _uiState.update { it.copy(message = "Enter an email address for the partner sign-in.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSignIn = true, servicesError = null) }
            adminRepository.createAccount(
                email = email.trim(),
                role = StaffRole.PARTNER,
                operatorId = operator.id,
                password = password?.takeIf { it.isNotBlank() },
            )
                .onSuccess { credential ->
                    _uiState.update {
                        it.copy(isCreatingSignIn = false, issuedCredential = credential, message = "Partner sign-in created")
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isCreatingSignIn = false, servicesError = throwable.staffMessage("Unable to create account"))
                    }
                }
        }
    }

    /** Rotates the legacy API token; any previous token stops working. */
    fun issueLegacyToken() {
        val operator = _uiState.value.selected ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isIssuingToken = true, servicesError = null) }
            adminRepository.issuePartnerToken(operator.id)
                .onSuccess { issued ->
                    _uiState.update { it.copy(isIssuingToken = false, issuedToken = issued, message = "Token issued — shown once") }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isIssuingToken = false, servicesError = throwable.staffMessage("Unable to issue token")) }
                }
        }
    }

    fun dismissCredential() = _uiState.update { it.copy(issuedCredential = null) }

    fun dismissToken() = _uiState.update { it.copy(issuedToken = null) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
