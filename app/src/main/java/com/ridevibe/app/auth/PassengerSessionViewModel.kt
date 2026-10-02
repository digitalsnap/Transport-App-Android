package com.ridevibe.app.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.ridevibe.core.domain.model.UserProfile
import com.ridevibe.core.domain.repository.ProfileRepository
import com.ridevibe.core.domain.session.BookingCart
import com.ridevibe.core.network.auth.CurrentUserId
import com.ridevibe.core.network.cache.TicketCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PassengerSessionUiState(
    val session: PassengerSession? = null,
    /** True while "Delete account" is wiping local data. */
    val isBusy: Boolean = false,
    /** One-shot: the screen navigates to Welcome, then calls [PassengerSessionViewModel.onSignedOutHandled]. */
    val signedOut: Boolean = false,
    val error: String? = null,
)

/**
 * The passenger session for the whole activity: the nav graph obtains it once
 * (activity-scoped) and hands it to Welcome and Profile so both see the same
 * instance. Sign-out fans out to every identity the device holds: the local
 * session, Google, Facebook and the CRS user id echoed on past bookings.
 */
@HiltViewModel
class PassengerSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PassengerSessionStore,
    private val currentUserId: CurrentUserId,
    private val bookingCart: BookingCart,
    private val ticketCache: TicketCache,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassengerSessionUiState(session = store.session.value))
    val uiState: StateFlow<PassengerSessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.session.collect { session -> _uiState.update { it.copy(session = session) } }
        }
    }

    /**
     * Whether launch can skip Welcome. A Google account the SDK still remembers
     * counts even if our own store was cleared, so the rider is not asked to
     * sign in twice.
     */
    fun hasExistingSession(): Boolean {
        if (store.session.value != null) return true
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        // The SDK kept the account but our store never got it (process death
        // between the sign-in result and the save): rebuild it so Profile agrees.
        onGoogleSignedIn(account)
        return true
    }

    fun onGoogleSignedIn(account: GoogleSignInAccount) {
        store.save(
            PassengerSession(
                provider = SignInProvider.GOOGLE,
                displayName = account.displayName,
                email = account.email,
                photoUrl = account.photoUrl?.toString(),
                idToken = account.idToken,
            ),
        )
    }

    fun onFacebookSignedIn(displayName: String?, email: String?, accessToken: String?) {
        store.save(
            PassengerSession(
                provider = SignInProvider.FACEBOOK,
                displayName = displayName,
                email = email,
                idToken = accessToken,
            ),
        )
    }

    /** "Get Started" without an account: the app works, Profile shows a Guest chip. */
    fun continueAsGuest() {
        if (store.session.value == null) store.save(PassengerSession(provider = SignInProvider.GUEST))
    }

    fun signOut() {
        clearIdentities()
        _uiState.update { it.copy(signedOut = true) }
    }

    /**
     * Sign-out plus a wipe of everything this install keeps about the rider:
     * profile, booking cart, cached tickets. Server-side deletion needs the
     * account backend (docs/DEVELOPER-ACTIONS.md §B); until then this is the whole story.
     */
    fun deleteAccount() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            try {
                profileRepository.saveProfile(UserProfile()).getOrThrow()
                bookingCart.reset()
                ticketCache.clear()
                clearIdentities()
                _uiState.update { it.copy(isBusy = false, signedOut = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false, error = e.message ?: "Could not delete local data") }
            }
        }
    }

    fun onSignedOutHandled() = _uiState.update { it.copy(signedOut = false) }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    private fun clearIdentities() {
        store.clear()
        currentUserId.clear()
        // Fire-and-forget: the local session is already gone; the SDKs only forget their tokens.
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        // LoginManager.getInstance() throws when the SDK never initialised (no Facebook sign-in this process).
        if (FacebookSdk.isInitialized()) LoginManager.getInstance().logOut()
    }
}
