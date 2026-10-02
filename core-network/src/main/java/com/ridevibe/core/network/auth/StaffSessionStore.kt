package com.ridevibe.core.network.auth

import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.core.network.storage.KeyValueStore
import com.ridevibe.core.network.storage.StaffSessionPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the staff [StaffSession] (the `s_…` token plus who it belongs to
 * and when it lapses) in the encrypted `ridevibe_staff_session` preferences,
 * and exposes it as a [StateFlow] that the repositories, the token
 * interceptor, and the UI all observe.
 *
 * Separate from [DeviceIdProvider] on purpose: the passenger identity
 * (X-Device-Id) never changes when a staff member signs in or out.
 *
 * Writes go through `commit` (synchronous): a staff member who signs in and
 * is immediately backgrounded must not come back signed out.
 */
@Singleton
class StaffSessionStore @Inject constructor(
    @StaffSessionPrefs private val store: KeyValueStore,
) {
    private val state: MutableStateFlow<StaffSession?> by lazy { MutableStateFlow(load()) }

    /** Null when signed out. Emits on save/clear and holds the persisted session after restart. */
    val session: StateFlow<StaffSession?> get() = state

    /** True when a session is stored but its server-side expiry has passed — treat as signed out. */
    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        state.value?.isExpired(nowEpochMillis) == true

    fun save(session: StaffSession) {
        store.putAll(
            mapOf(
                KEY_TOKEN to session.token,
                KEY_EMAIL to session.email,
                KEY_ROLE to session.role.name,
                KEY_OPERATOR_ID to session.operatorId?.toString(),
                KEY_OPERATOR_NAME to session.operatorName,
                KEY_EXPIRES_AT to session.expiresAtEpochMillis?.toString(),
            ),
            sync = true,
        )
        state.value = session
    }

    fun clear() {
        store.clear(sync = true)
        state.value = null
    }

    private fun load(): StaffSession? = runCatching {
        val token = store.get(KEY_TOKEN) ?: return null
        val email = store.get(KEY_EMAIL) ?: return null
        val role = store.get(KEY_ROLE)
            ?.let { raw -> StaffRole.values().firstOrNull { it.name == raw } }
            ?: return null
        StaffSession(
            token = token,
            email = email,
            role = role,
            operatorId = store.get(KEY_OPERATOR_ID)?.toIntOrNull(),
            operatorName = store.get(KEY_OPERATOR_NAME),
            expiresAtEpochMillis = store.get(KEY_EXPIRES_AT)?.toLongOrNull(),
        )
    }.getOrNull()

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_ROLE = "role"
        const val KEY_OPERATOR_ID = "operator_id"
        const val KEY_OPERATOR_NAME = "operator_name"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
