package com.ridevibe.core.network.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ridevibe.core.network.log.RideVibeLog

/**
 * EncryptedSharedPreferences behind [KeyValueStore], opened lazily so nothing
 * touches the keystore until the first reader (Kotlin `lazy` is synchronized).
 *
 * If the encrypted file cannot be opened (a corrupt or rotated keystore entry —
 * happens after some backups/restores) the file is deleted and recreated ONCE;
 * if that fails too the store degrades to in-memory for this process rather
 * than crashing at startup.
 */
class EncryptedPrefsKeyValueStore(
    private val context: Context,
    private val fileName: String,
) : KeyValueStore {

    private val delegate: KeyValueStore by lazy {
        openEncryptedPrefs()?.let { SharedPreferencesKeyValueStore(it) } ?: InMemoryKeyValueStore()
    }

    override fun get(key: String): String? = delegate.get(key)

    override fun put(key: String, value: String, sync: Boolean) = delegate.put(key, value, sync)

    override fun remove(key: String, sync: Boolean) = delegate.remove(key, sync)

    override fun putAll(values: Map<String, String?>, sync: Boolean) = delegate.putAll(values, sync)

    override fun clear(sync: Boolean) = delegate.clear(sync)

    private fun openEncryptedPrefs(): SharedPreferences? {
        runCatching { createEncryptedPrefs() }
            .onSuccess { return it }
            .onFailure { RideVibeLog.w("Encrypted prefs '$fileName' unreadable; resetting", it) }
        // Corrupt keyset or keystore entry: drop the file and try once more.
        runCatching { context.deleteSharedPreferences(fileName) }
        return runCatching { createEncryptedPrefs() }
            .onFailure { RideVibeLog.w("Encrypted prefs '$fileName' unavailable; values will not persist", it) }
            .getOrNull()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
