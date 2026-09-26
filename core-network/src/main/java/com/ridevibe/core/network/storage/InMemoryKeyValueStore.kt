package com.ridevibe.core.network.storage

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-lifetime store. Used as the last-resort fallback when an encrypted
 * preferences file cannot be opened, and by unit tests in place of
 * SharedPreferences.
 */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = ConcurrentHashMap<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String, sync: Boolean) {
        values[key] = value
    }

    override fun remove(key: String, sync: Boolean) {
        values.remove(key)
    }

    override fun clear(sync: Boolean) = values.clear()

    /** For tests: what is currently persisted. */
    fun snapshot(): Map<String, String> = values.toMap()
}
