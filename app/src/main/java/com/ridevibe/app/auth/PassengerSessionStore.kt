package com.ridevibe.app.auth

import com.ridevibe.core.network.storage.KeyValueStore
import com.ridevibe.core.network.storage.PassengerSessionPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who is signed in on this device, kept in encrypted preferences (the entry
 * carries the provider tokens) so the app can skip Welcome on the next launch.
 *
 * TODO(auth): the backend has no passenger accounts yet; this session is device-local
 * and the idToken is never sent. See docs/DEVELOPER-ACTIONS.md §B.
 */
@Singleton
class PassengerSessionStore @Inject constructor(
    @PassengerSessionPrefs private val store: KeyValueStore,
) {
    private val _session = MutableStateFlow(load())
    val session: StateFlow<PassengerSession?> = _session.asStateFlow()

    fun save(session: PassengerSession) {
        store.putAll(
            mapOf(
                KEY_PROVIDER to session.provider.name,
                KEY_NAME to session.displayName,
                KEY_EMAIL to session.email,
                KEY_PHOTO to session.photoUrl,
                KEY_ID_TOKEN to session.idToken,
            ),
            sync = true,
        )
        _session.value = session
    }

    fun clear() {
        store.clear(sync = true)
        _session.value = null
    }

    private fun load(): PassengerSession? {
        val providerName = store.get(KEY_PROVIDER) ?: return null
        val provider = SignInProvider.values().firstOrNull { it.name == providerName } ?: return null
        return PassengerSession(
            provider = provider,
            displayName = store.get(KEY_NAME),
            email = store.get(KEY_EMAIL),
            photoUrl = store.get(KEY_PHOTO),
            idToken = store.get(KEY_ID_TOKEN),
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
