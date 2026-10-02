package com.ridevibe.core.network.auth

import com.ridevibe.core.network.storage.IdentityStore
import com.ridevibe.core.network.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The passenger's own CRS user id, as disclosed by the `X-User-Id` response
 * header on every authenticated call (hold, release, book, my bookings).
 *
 * This is how the app recognises its own holds: seats and seat events carry
 * `lockedByUserId`, and the server never sends the raw device id there since
 * other viewers would see it. Null until the first authenticated call succeeds,
 * so a fresh install that has never held a seat simply sees every lock as
 * someone else's — which is correct.
 *
 * The last echoed id is kept in the `ridevibe_identity` preferences next to
 * the device id, so a rider who reopens the app after a process death still
 * sees the seats they were holding as their own.
 */
@Singleton
class CurrentUserId @Inject constructor(
    @IdentityStore private val store: KeyValueStore,
) {
    private val _userId = MutableStateFlow(store.get(KEY_USER_ID))
    val userId: StateFlow<String?> = _userId.asStateFlow()

    /** Current value, for synchronous mapping in the data layer. */
    val value: String? get() = _userId.value

    fun update(id: String?) {
        if (id.isNullOrBlank() || id == _userId.value) return
        _userId.value = id
        store.put(KEY_USER_ID, id)
    }

    /** Forget the identity, e.g. when the device id is rotated. */
    fun clear() {
        _userId.value = null
        store.remove(KEY_USER_ID)
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
    }
}
