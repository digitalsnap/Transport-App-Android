package com.ridevibe.core.network.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.model.StaffSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the staff [StaffSession] (the `s_…` token plus who it belongs to)
 * in EncryptedSharedPreferences, and exposes it as a [StateFlow] that the
 * repositories, the token interceptor, and the UI all observe.
 *
 * Separate from [DeviceIdProvider] on purpose: the passenger identity
 * (X-Device-Id) never changes when a staff member signs in or out.
 *
 * Loading is lazy and synchronized (Kotlin `lazy` default), so nothing touches
 * the keystore until the first reader. If the encrypted file cannot be opened
 * (a corrupt or rotated keystore entry — happens after some backups/restores)
 * the file is deleted and recreated ONCE; if that fails too the store degrades
 * to in-memory for this process rather than crashing at startup.
 */
@Singleton
class StaffSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Null when the encrypted prefs could not be created at all (memory-only mode). */
    private val prefs: SharedPreferences? by lazy { openEncryptedPrefs() }

    private val state: MutableStateFlow<StaffSession?> by lazy { MutableStateFlow(load()) }

    /** Null when signed out. Emits on save/clear and holds the persisted session after restart. */
    val session: StateFlow<StaffSession?> get() = state

    fun save(session: StaffSession) {
        prefs?.let { p ->
            val editor = p.edit()
                .putString(KEY_TOKEN, session.token)
                .putString(KEY_EMAIL, session.email)
                .putString(KEY_ROLE, session.role.name)
            val operatorId = session.operatorId
            if (operatorId != null) editor.putInt(KEY_OPERATOR_ID, operatorId) else editor.remove(KEY_OPERATOR_ID)
            val operatorName = session.operatorName
            if (operatorName != null) editor.putString(KEY_OPERATOR_NAME, operatorName) else editor.remove(KEY_OPERATOR_NAME)
            editor.apply()
        }
        state.value = session
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        state.value = null
    }

    private fun load(): StaffSession? {
        val p = prefs ?: return null
        return runCatching {
            val token = p.getString(KEY_TOKEN, null) ?: return null
            val email = p.getString(KEY_EMAIL, null) ?: return null
            val role = p.getString(KEY_ROLE, null)
                ?.let { raw -> StaffRole.values().firstOrNull { it.name == raw } }
                ?: return null
            StaffSession(
                token = token,
                email = email,
                role = role,
                operatorId = if (p.contains(KEY_OPERATOR_ID)) p.getInt(KEY_OPERATOR_ID, 0) else null,
                operatorName = p.getString(KEY_OPERATOR_NAME, null),
            )
        }.getOrNull()
    }

    private fun openEncryptedPrefs(): SharedPreferences? {
        runCatching { createEncryptedPrefs() }
            .onSuccess { return it }
            .onFailure { Log.w(TAG, "Encrypted staff session prefs unreadable; resetting", it) }
        // Corrupt keyset or keystore entry: drop the file and try once more.
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
        return runCatching { createEncryptedPrefs() }
            .onFailure { Log.e(TAG, "Encrypted prefs unavailable; staff session will not persist", it) }
            .getOrNull()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val TAG = "StaffSessionStore"
        const val PREFS_NAME = "ridevibe_staff_session"
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_ROLE = "role"
        const val KEY_OPERATOR_ID = "operator_id"
        const val KEY_OPERATOR_NAME = "operator_name"
    }
}
