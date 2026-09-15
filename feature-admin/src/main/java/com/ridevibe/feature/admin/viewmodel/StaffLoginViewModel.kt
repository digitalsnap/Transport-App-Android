package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.StaffAuthOptions
import com.ridevibe.core.domain.repository.StaffAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffLoginUiState(
    /** Loading `GET /auth/config`. */
    val isLoading: Boolean = true,
    val error: String? = null,
    val options: StaffAuthOptions? = null,
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val isSigningIn: Boolean = false,
    val isRequestingKey: Boolean = false,
    val isVerifyingKey: Boolean = false,
    /** True once a sign-in key has been emailed; reveals the 6-digit entry. */
    val keyRequested: Boolean = false,
    val keyMessage: String? = null,
    /** Present outside production only — displayed with a dev-only hint. */
    val devCode: String? = null,
) {
    val isBusy: Boolean get() = isSigningIn || isRequestingKey || isVerifyingKey
    val canSignIn: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isBusy
    val canRequestKey: Boolean get() = email.isNotBlank() && !isBusy
    val canVerifyKey: Boolean get() = email.isNotBlank() && code.length == 6 && !isBusy
}

/** Email + password and emailed-key sign-in for `/auth/…`; success is observed through the session flow. */
@HiltViewModel
class StaffLoginViewModel @Inject constructor(
    private val authRepository: StaffAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffLoginUiState())
    val uiState: StateFlow<StaffLoginUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.getAuthOptions()
                .onSuccess { options -> _uiState.update { it.copy(isLoading = false, options = options) } }
                .onFailure { throwable ->
                    // The form still works without the config; email key stays offered.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            options = StaffAuthOptions(googleEnabled = false, emailKeyEnabled = true),
                            error = throwable.message,
                        )
                    }
                }
        }
    }

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun onCodeChanged(value: String) =
        _uiState.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    fun signIn() {
        val state = _uiState.value
        if (!state.canSignIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, error = null) }
            authRepository.signInWithPassword(state.email.trim(), state.password)
                .onSuccess { _uiState.update { it.copy(isSigningIn = false, password = "") } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSigningIn = false, error = throwable.message ?: "Sign-in failed") }
                }
        }
    }

    /** "Email me a sign-in key" — succeeds identically whether or not the account exists. */
    fun requestEmailKey() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email address first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingKey = true, error = null) }
            authRepository.requestEmailCode(email)
                .onSuccess { requested ->
                    _uiState.update {
                        it.copy(
                            isRequestingKey = false,
                            keyRequested = true,
                            keyMessage = requested.message,
                            devCode = requested.devCode,
                            code = "",
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isRequestingKey = false, error = throwable.message ?: "Could not send a key") }
                }
        }
    }

    fun verifyEmailKey() {
        val state = _uiState.value
        if (!state.canVerifyKey) return
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingKey = true, error = null) }
            authRepository.signInWithEmailCode(state.email.trim(), state.code)
                .onSuccess { _uiState.update { it.copy(isVerifyingKey = false, code = "") } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isVerifyingKey = false, error = throwable.message ?: "That key was not accepted") }
                }
        }
    }
}
