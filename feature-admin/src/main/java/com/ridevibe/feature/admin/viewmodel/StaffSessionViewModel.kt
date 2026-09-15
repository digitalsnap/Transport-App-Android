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

data class StaffSessionUiState(
    /** True while the persisted session is being validated against the server. */
    val isLoading: Boolean = true,
    val session: StaffSession? = null,
    val error: String? = null,
)

/**
 * Observes the staff session for the console root. The repository persists the
 * session; [refreshSession] drops a stale one on first composition.
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.refreshSession()
                .onSuccess { session -> _uiState.update { it.copy(isLoading = false, session = session) } }
                .onFailure { throwable ->
                    // Network trouble keeps the local session; the console will surface API errors itself.
                    _uiState.update { it.copy(isLoading = false, error = throwable.message) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}
