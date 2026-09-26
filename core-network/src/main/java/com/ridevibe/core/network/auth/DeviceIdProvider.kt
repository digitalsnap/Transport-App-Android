package com.ridevibe.core.network.auth

import com.ridevibe.core.network.storage.IdentityStore
import com.ridevibe.core.network.storage.KeyValueStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable per-install identifier sent as `X-Device-Id`, which is how the CRS
 * currently scopes holds and bookings to a user.
 *
 * INTERIM. This is not authentication — the backend's own auth.ts says so: any
 * client that knows a device id can act as that user. It exists so the booking
 * flow works end to end before real auth lands. When Firebase ID tokens replace
 * it, this provider is what the token attaches to, so the account survives the
 * migration rather than orphaning a device's bookings.
 *
 * Generated once and persisted (synchronously — losing it loses this
 * install's bookings). Cleared only by uninstall or clearing app data, which
 * is another reason this is not the endgame.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @IdentityStore private val store: KeyValueStore,
) {
    /** Lazily resolved once per process; `by lazy` is synchronized by default. */
    val deviceId: String by lazy {
        store.get(KEY_DEVICE_ID)
            ?: UUID.randomUUID().toString().also { generated -> store.put(KEY_DEVICE_ID, generated, sync = true) }
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
