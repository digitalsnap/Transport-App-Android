package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.core.domain.repository.StaffAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shown on the sign-in screen after the server (or the local expiry) dropped the session. */
const val SESSION_EXPIRED_MESSAGE = "Session expired — sign in again."

data class StaffSessionUiState(
    /** True while the persisted session is being validated against the server. */
    val isLoading: Boolean = true,
    val session: StaffSession? = null,
    val error: String? = null,
    /**
     * Bumps on every sign-out. The tab view models outlive a sign-out (they are
     * scoped to the console's nav entry), so they key their caches on
     * [sessionKey] and drop the previous account's data when it changes.
     */
    val sessionEpoch: Int = 0,
) {
    val sessionKey: String get() = "${session?.token.orEmpty()}#$sessionEpoch"
}

/**
 * Observes the staff session for the console root. The repository persists the
 * session; [refreshSession] drops a stale one on first composition and the
 * root calls [onSessionExpired] when the local expiry passes mid-visit.
 */
@HiltViewModel
class StaffSessionViewModel @Inject constructor(
    private val authRepository: StaffAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffSessionUiState(session = authRepository.session.value))
    val uiState: StateFlow<StaffSessionUiState> = _uiState.asStateFlow()

    private var refreshRequested = false

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
    }

    /** Validates the persisted session once per console visit; a rejected one is cleared by the repository. */
    fun refreshSession() {
        if (refreshRequested) return
        refreshRequested = true
        viewModelScope.launch {
            if (authRepository.session.value == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.refreshSession()
                .onSuccess { session ->
                    // The repository answers null (and clears the store) for an expired or rejected session.
                    _uiState.update {
                        it.copy(isLoading = false, session = session, error = if (session == null) SESSION_EXPIRED_MESSAGE else null)
                    }
                }
                .onFailure { throwable ->
                    // Network trouble keeps the local session; the console will surface API errors itself.
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.staffMessage("Could not check your session with the server."))
                    }
                }
        }
    }

    /** The local expiry passed while the console was open: sign out and say why. */
    fun onSessionExpired() {
        if (_uiState.value.session == null) return
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { it.copy(sessionEpoch = it.sessionEpoch + 1, error = SESSION_EXPIRED_MESSAGE) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { it.copy(sessionEpoch = it.sessionEpoch + 1, error = null) }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}
