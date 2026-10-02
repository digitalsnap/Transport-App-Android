package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.AccountCredential
import com.ridevibe.core.domain.model.CuratedJourney
import com.ridevibe.core.domain.model.LiveHold
import com.ridevibe.core.domain.model.OperatorSummary
import com.ridevibe.core.domain.model.PassengerAccount
import com.ridevibe.core.domain.model.RouteSummary
import com.ridevibe.core.domain.model.StaffAccount
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sub-tabs of the Data view, in the order the console shows them. */
enum class DataSection(val label: String) {
    ACCOUNTS("Accounts"),
    HOLDS("Active holds"),
    USERS("Users"),
    TERMINALS("Terminals"),
    ROUTES("Routes"),
    JOURNEYS("Journeys"),
}

/** Minimum the server accepts for a staff password set by hand. */
const val MIN_STAFF_PASSWORD_LENGTH = 8

/** A tapped row opened in the small detail sheet. */
sealed interface DataDetail {
    data class User(val user: PassengerAccount) : DataDetail
    data class Hold(val hold: LiveHold) : DataDetail
    data class Route(val route: RouteSummary) : DataDetail
}

data class AdminDataUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val section: DataSection = DataSection.ACCOUNTS,
    val accounts: List<StaffAccount> = emptyList(),
    val holds: List<LiveHold> = emptyList(),
    val users: List<PassengerAccount> = emptyList(),
    val terminals: List<TerminalLocation> = emptyList(),
    val routes: List<RouteSummary> = emptyList(),
    val routeQuery: String = "",
    val journeys: List<CuratedJourney> = emptyList(),
    /** Operator picker for PARTNER accounts. */
    val operators: List<OperatorSummary> = emptyList(),
    val isActing: Boolean = false,
    /** Shown once: generated password from create / reset. */
    val issuedCredential: AccountCredential? = null,
    val detail: DataDetail? = null,
    val message: String? = null,
)

/** Data: reference data and live operational records, plus staff account administration. */
@HiltViewModel
class AdminDataViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDataUiState())
    val uiState: StateFlow<AdminDataUiState> = _uiState.asStateFlow()

    private val loadedSections = mutableSetOf<DataSection>()
    private var sessionKey: String? = null

    fun start(sessionKey: String) {
        if (this.sessionKey == sessionKey) return
        this.sessionKey = sessionKey
        loadedSections.clear()
        _uiState.value = AdminDataUiState()
        load()
    }

    fun selectSection(section: DataSection) {
        _uiState.update { it.copy(section = section, error = null, detail = null) }
        if (section !in loadedSections) load() else _uiState.update { it.copy(isLoading = false) }
    }

    fun onRouteQueryChanged(value: String) = _uiState.update { it.copy(routeQuery = value) }

    /** Reloads the current section. */
    fun load() {
        val section = _uiState.value.section
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result: Result<Unit> = when (section) {
                DataSection.ACCOUNTS -> adminRepository.getAccounts().map { rows ->
                    _uiState.update { it.copy(accounts = rows) }
                }
                DataSection.HOLDS -> adminRepository.getLiveHolds().map { rows -> _uiState.update { it.copy(holds = rows) } }
                DataSection.USERS -> adminRepository.getPassengerAccounts().map { rows -> _uiState.update { it.copy(users = rows) } }
                DataSection.TERMINALS -> adminRepository.getTerminals().map { rows -> _uiState.update { it.copy(terminals = rows) } }
                DataSection.ROUTES -> adminRepository.getRoutes(_uiState.value.routeQuery.trim()).map { rows ->
                    _uiState.update { it.copy(routes = rows) }
                }
                DataSection.JOURNEYS -> adminRepository.getJourneys().map { rows -> _uiState.update { it.copy(journeys = rows) } }
            }
            result
                .onSuccess {
                    loadedSections += section
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.staffMessage("Unable to load ${section.label.lowercase()}"))
                    }
                }
        }
    }

    /** Operators for the PARTNER account picker; loaded lazily when the create dialog opens. */
    fun ensureOperatorsLoaded() {
        if (_uiState.value.operators.isNotEmpty()) return
        viewModelScope.launch {
            adminRepository.getOperators().onSuccess { operators -> _uiState.update { it.copy(operators = operators) } }
        }
    }

    fun openDetail(detail: DataDetail) = _uiState.update { it.copy(detail = detail) }

    fun closeDetail() = _uiState.update { it.copy(detail = null) }

    /** Omit [password] to have one generated and returned once. PARTNER needs [operatorId]. */
    fun createAccount(email: String, role: StaffRole, operatorId: Int?, password: String?) {
        if (email.isBlank()) {
            _uiState.update { it.copy(message = "Enter an email address for the new account.") }
            return
        }
        if (role == StaffRole.PARTNER && operatorId == null) {
            _uiState.update { it.copy(message = "Pick the company this partner account belongs to.") }
            return
        }
        act("Account created for ${email.trim()}") {
            adminRepository.createAccount(
                email = email.trim(),
                role = role,
                operatorId = if (role == StaffRole.PARTNER) operatorId else null,
                password = password?.takeIf { it.isNotBlank() },
            ).map { credential -> credential }
        }
    }

    /** Null [password] asks the server to generate one (returned once); otherwise the given one is set. */
    fun resetPassword(account: StaffAccount, password: String?) {
        val chosen = password?.takeIf { it.isNotBlank() }
        if (chosen != null && chosen.length < MIN_STAFF_PASSWORD_LENGTH) {
            _uiState.update { it.copy(message = "Password must be at least $MIN_STAFF_PASSWORD_LENGTH characters.") }
            return
        }
        val successMessage = if (chosen == null) "New password generated for ${account.email}" else "Password set for ${account.email}"
        act(successMessage) {
            adminRepository.resetPassword(account.id, chosen).map { credential -> credential }
        }
    }

    fun deleteAccount(account: StaffAccount) {
        act("Account deleted") {
            adminRepository.deleteAccount(account.id).map { null as AccountCredential? }
        }
    }

    private fun act(successMessage: String, action: suspend () -> Result<AccountCredential?>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, error = null) }
            action()
                .onSuccess { credential ->
                    _uiState.update { it.copy(isActing = false, issuedCredential = credential, message = successMessage) }
                    if (_uiState.value.section == DataSection.ACCOUNTS) load() else loadedSections -= DataSection.ACCOUNTS
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isActing = false, error = throwable.staffMessage("Action failed")) }
                }
        }
    }

    fun dismissCredential() = _uiState.update { it.copy(issuedCredential = null) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
