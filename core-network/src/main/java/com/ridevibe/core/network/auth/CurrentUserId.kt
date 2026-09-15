package com.ridevibe.core.network.auth

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
 */
@Singleton
class CurrentUserId @Inject constructor() {
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    /** Current value, for synchronous mapping in the data layer. */
    val value: String? get() = _userId.value

    fun update(id: String?) {
        if (!id.isNullOrBlank()) _userId.value = id
    }
}
