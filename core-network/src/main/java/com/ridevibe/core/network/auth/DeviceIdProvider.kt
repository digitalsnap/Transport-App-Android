package com.ridevibe.core.network.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Generated once and persisted. Cleared only by uninstall or clearing app data,
 * which loses that install's bookings — another reason this is not the endgame.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Lazily resolved once per process; `by lazy` is synchronized by default. */
    val deviceId: String by lazy {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { generated ->
                prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            }
    }

    private companion object {
        const val PREFS_NAME = "ridevibe_identity"
        const val KEY_DEVICE_ID = "device_id"
    }
}
