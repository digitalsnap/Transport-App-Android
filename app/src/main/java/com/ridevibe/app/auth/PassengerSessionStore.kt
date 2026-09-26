package com.ridevibe.app.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who is signed in on this device, kept in the plain `ridevibe_passenger_session`
 * preferences so the app can skip Welcome on the next launch.
 *
 * TODO(auth): the backend has no passenger accounts yet; this session is device-local
 * and the idToken is never sent. See docs/CHECKLIST.md §1.
 */
@Singleton
class PassengerSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ridevibe_passenger_session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(load())
    val session: StateFlow<PassengerSession?> = _session.asStateFlow()

    fun save(session: PassengerSession) {
        prefs.edit()
            .putString(KEY_PROVIDER, session.provider.name)
            .putString(KEY_NAME, session.displayName)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_PHOTO, session.photoUrl)
            .putString(KEY_ID_TOKEN, session.idToken)
            .apply()
        _session.value = session
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun load(): PassengerSession? {
        val providerName = prefs.getString(KEY_PROVIDER, null) ?: return null
        val provider = SignInProvider.values().firstOrNull { it.name == providerName } ?: return null
        return PassengerSession(
            provider = provider,
            displayName = prefs.getString(KEY_NAME, null),
            email = prefs.getString(KEY_EMAIL, null),
            photoUrl = prefs.getString(KEY_PHOTO, null),
            idToken = prefs.getString(KEY_ID_TOKEN, null),
        )
    }

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_NAME = "display_name"
        const val KEY_EMAIL = "email"
        const val KEY_PHOTO = "photo_url"
        const val KEY_ID_TOKEN = "id_token"
    }
}
