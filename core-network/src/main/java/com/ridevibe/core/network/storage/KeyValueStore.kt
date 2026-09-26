package com.ridevibe.core.network.storage

import android.content.SharedPreferences
import javax.inject.Qualifier

/**
 * The slice of SharedPreferences the data layer actually uses: string values
 * keyed by name. Stores that need numbers or JSON encode them as strings.
 *
 * Exists so the classes built on it (identity, staff session, cart, ticket
 * cache) can be unit-tested on the JVM with an in-memory implementation —
 * there is no Robolectric in this project and `android.content` is stubbed
 * out in local tests.
 */
interface KeyValueStore {
    fun get(key: String): String?

    /** [sync] writes through before returning (`commit`) — for values that must survive an immediate process death. */
    fun put(key: String, value: String, sync: Boolean = false)

    fun remove(key: String, sync: Boolean = false)

    /** Writes every entry in one transaction; a null value removes its key. */
    fun putAll(values: Map<String, String?>, sync: Boolean = false) {
        values.forEach { (key, value) -> if (value == null) remove(key, sync) else put(key, value, sync) }
    }

    fun clear(sync: Boolean = false)

    fun contains(key: String): Boolean = get(key) != null
}

/** Plain SharedPreferences file (not encrypted). */
class SharedPreferencesKeyValueStore(
    private val prefs: SharedPreferences,
) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String, sync: Boolean) = prefs.edit().putString(key, value).finish(sync)

    override fun remove(key: String, sync: Boolean) = prefs.edit().remove(key).finish(sync)

    override fun putAll(values: Map<String, String?>, sync: Boolean) {
        val editor = prefs.edit()
        values.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putString(key, value) }
        editor.finish(sync)
    }

    override fun clear(sync: Boolean) = prefs.edit().clear().finish(sync)

    private fun SharedPreferences.Editor.finish(sync: Boolean) {
        if (sync) commit() else apply()
    }
}

// ── Hilt qualifiers: one per preferences file ────────────────────────────────

/** `ridevibe_identity` — the passenger device id and the last echoed user id. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IdentityStore

/** `ridevibe_staff_session` (encrypted) — the staff console's session token. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class StaffSessionPrefs

/** `ridevibe_cart` — the booking in progress. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class CartStore

/** `ridevibe_ticket_cache` — the last fetched tickets, for offline viewing. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class TicketCacheStore
