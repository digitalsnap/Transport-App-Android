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

data class AdminPartnersUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val operators: List<OperatorSummary> = emptyList(),
    /** Detail sheet for one operator with its services and seating. */
    val selected: OperatorSummary? = null,
    val services: List<OperatorService> = emptyList(),
    val isServicesLoading: Boolean = false,
    val servicesError: String? = null,
    val isActing: Boolean = false,
    /** Shown once: the generated partner password. */
    val issuedCredential: AccountCredential? = null,
    /** Shown once: the rotated legacy `pt_…` token. */
    val issuedToken: PartnerTokenIssued? = null,
    val message: String? = null,
)

/** Partners: bus lines, ferries and fastcraft operators — their services, sign-ins and tokens. */
@HiltViewModel
class AdminPartnersViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPartnersUiState())
    val uiState: StateFlow<AdminPartnersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            adminRepository.getOperators()
                .onSuccess { operators -> _uiState.update { it.copy(isLoading = false, operators = operators) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Unable to load partners") }
                }
        }
    }

    /** Used by the partner-portal picker and company switcher; cheap when already loaded. */
    fun ensureOperatorsLoaded() {
        if (_uiState.value.operators.isEmpty() && !_uiState.value.isLoading) load()
    }

    fun openOperator(operator: OperatorSummary) {
        _uiState.update { it.copy(selected = operator, services = emptyList(), servicesError = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(isServicesLoading = true) }
            adminRepository.getOperatorServices(operator.id)
                .onSuccess { services -> _uiState.update { it.copy(isServicesLoading = false, services = services) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isServicesLoading = false, servicesError = throwable.message ?: "Unable to load services")
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
            _uiState.update { it.copy(isActing = true, servicesError = null) }
            adminRepository.createAccount(
                email = email.trim(),
                role = StaffRole.PARTNER,
                operatorId = operator.id,
                password = password?.takeIf { it.isNotBlank() },
            )
                .onSuccess { credential ->
                    _uiState.update {
                        it.copy(isActing = false, issuedCredential = credential, message = "Partner sign-in created")
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isActing = false, servicesError = throwable.message ?: "Unable to create account") }
                }
        }
    }

    /** Rotates the legacy API token; any previous token stops working. */
    fun issueLegacyToken() {
        val operator = _uiState.value.selected ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, servicesError = null) }
            adminRepository.issuePartnerToken(operator.id)
                .onSuccess { issued ->
                    _uiState.update { it.copy(isActing = false, issuedToken = issued, message = "Token issued — shown once") }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isActing = false, servicesError = throwable.message ?: "Unable to issue token") }
                }
        }
    }

    fun dismissCredential() = _uiState.update { it.copy(issuedCredential = null) }

    fun dismissToken() = _uiState.update { it.copy(issuedToken = null) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
