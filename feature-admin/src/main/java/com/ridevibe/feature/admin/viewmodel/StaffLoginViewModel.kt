package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.StaffAuthOptions
import com.ridevibe.core.domain.repository.StaffAuthRepository
import com.ridevibe.core.network.api.CrsApiException
import com.ridevibe.feature.admin.auth.GoogleSignInCancelled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Loose RFC-5322 shape: enough to catch a missing `@` or domain before the server is asked. */
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

data class StaffLoginUiState(
    /** Loading `GET /auth/config`. */
    val isLoading: Boolean = true,
    /** `/auth/config` failed; the form still works with defaults and a retry is offered. */
    val optionsError: String? = null,
    val error: String? = null,
    val options: StaffAuthOptions? = null,
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    /** Set once the email field lost focus, so the format error does not shout while typing. */
    val emailTouched: Boolean = false,
    val code: String = "",
    val isSigningIn: Boolean = false,
    val isSigningInWithGoogle: Boolean = false,
    val isRequestingKey: Boolean = false,
    val isVerifyingKey: Boolean = false,
    /** True once a sign-in key has been emailed; reveals the 6-digit entry. */
    val keyRequested: Boolean = false,
    val keyMessage: String? = null,
    /** Present outside production only — displayed with a dev-only hint. */
    val devCode: String? = null,
) {
    val emailValid: Boolean get() = EMAIL_REGEX.matches(email.trim())
    val showEmailError: Boolean get() = emailTouched && email.isNotBlank() && !emailValid
    val isBusy: Boolean get() = isSigningIn || isRequestingKey || isVerifyingKey || isSigningInWithGoogle
    val canSignIn: Boolean get() = emailValid && password.isNotBlank() && !isBusy
    val canRequestKey: Boolean get() = emailValid && !isBusy
    val canVerifyKey: Boolean get() = emailValid && code.length == 6 && !isBusy

    /** Google needs the web-client id to request an ID token; without it the button is pointless. */
    val googleClientId: String? get() = options?.takeIf { it.googleEnabled }?.googleClientId
}

/** Email + password, emailed-key and Google sign-in for `/auth/…`; success is observed through the session flow. */
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
            _uiState.update { it.copy(isLoading = true, optionsError = null) }
            authRepository.getAuthOptions()
                .onSuccess { options -> _uiState.update { it.copy(isLoading = false, options = options) } }
                .onFailure { throwable ->
                    // The form still works without the config; email key stays offered.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            options = StaffAuthOptions(googleEnabled = false, emailKeyEnabled = true),
                            optionsError = throwable.staffMessage("Could not reach the sign-in service."),
                        )
                    }
                }
        }
    }

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onEmailFocusLost() = _uiState.update { it.copy(emailTouched = true) }

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun togglePasswordVisibility() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onCodeChanged(value: String) =
        _uiState.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    fun signIn() {
        val state = _uiState.value
        if (!state.canSignIn) {
            _uiState.update { it.copy(emailTouched = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, error = null) }
            authRepository.signInWithPassword(state.email.trim(), state.password)
                .onSuccess { _uiState.update { it.copy(isSigningIn = false, password = "") } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSigningIn = false, error = throwable.loginMessage("Sign-in failed")) }
                }
        }
    }

    /** "Email me a sign-in key" — succeeds identically whether or not the account exists. */
    fun requestEmailKey() {
        val state = _uiState.value
        if (!state.emailValid) {
            _uiState.update { it.copy(emailTouched = true, error = "Enter a valid email address first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingKey = true, error = null) }
            authRepository.requestEmailCode(state.email.trim())
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
                    _uiState.update { it.copy(isRequestingKey = false, error = throwable.loginMessage("Could not send a key")) }
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
                    _uiState.update { it.copy(isVerifyingKey = false, error = throwable.loginMessage("That key was not accepted")) }
                }
        }
    }

    /** Exchanges the Google ID token the host app obtained for a staff session. */
    fun signInWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _uiState.update { it.copy(error = "Google sign-in did not return a token.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningInWithGoogle = true, error = null) }
            authRepository.signInWithGoogle(idToken)
                .onSuccess { _uiState.update { it.copy(isSigningInWithGoogle = false) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSigningInWithGoogle = false, error = throwable.loginMessage("Google sign-in was not accepted"))
                    }
                }
        }
    }

    /**
     * What the host app's Google flow produced: an ID token to exchange, or why
     * there is none. A closed account chooser ([GoogleSignInCancelled]) is not
     * reported; every other failure lands in the same error line as a wrong
     * password so nothing fails silently.
     */
    fun onGoogleResult(result: Result<String>) {
        result
            .onSuccess(::signInWithGoogle)
            .onFailure { throwable ->
                val reason = if (throwable is GoogleSignInCancelled) null else throwable.message ?: "Google sign-in failed."
                _uiState.update { it.copy(isSigningInWithGoogle = false, error = reason) }
            }
    }

    /**
     * Sign-in has its own wording for the two codes staff actually hit: the
     * rate limiter (429) and a wrong password (401), which must not read as
     * "session expired" the way it does inside the console.
     */
    private fun Throwable.loginMessage(fallback: String): String = when {
        this is CrsApiException && code == 429 -> "Too many attempts, wait a minute and try again."
        this is CrsApiException && code == 401 -> message.ifBlank { "Wrong email or password." }
        else -> staffMessage(fallback)
    }
}
