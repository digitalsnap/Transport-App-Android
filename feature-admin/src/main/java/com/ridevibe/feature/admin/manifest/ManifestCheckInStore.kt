package com.ridevibe.feature.admin.manifest

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who has boarded which departure, kept on this device only.
 *
 * TODO(backend): POST /partner/api/manifest/{tripId}/check-in does not exist yet; boarded state is
 * device-local — see docs/api/PENDING-BACKEND.md. Until the endpoint lands, two conductors
 * scanning the same trip on two phones will not see each other's check-ins, and a reinstall
 * forgets them. Stored as one preference per trip: `ticketId=scannedAtEpochMillis` pairs.
 *
 * The endpoint must also re-validate the booking server-side (status still CONFIRMED, ticket
 * belongs to that trip) rather than trust the client's verdict: a scan is checked against the
 * manifest snapshot on the phone, which can be minutes old, so a booking cancelled after the last
 * fetch would board here. `PartnerManifestViewModel` refreshes after each boarding to narrow that
 * window, but only the server can close it.
 */
@Singleton
class ManifestCheckInStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Ticket id → scanned-at epoch millis for one departure. */
    fun boardedFor(tripId: String): Map<String, Long> =
        prefs.getString(key(tripId), null)
            ?.split(ENTRY_SEPARATOR)
            ?.mapNotNull { pair ->
                val (ticketId, at) = pair.split(FIELD_SEPARATOR, limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                at.toLongOrNull()?.let { ticketId to it }
            }
            ?.toMap()
            .orEmpty()

    /** Boarded map for every trip in [tripIds]; trips with no scans are absent. */
    fun boardedFor(tripIds: Collection<String>): Map<String, Map<String, Long>> =
        tripIds.distinct().associateWith { boardedFor(it) }.filterValues { it.isNotEmpty() }

    /** Records the scan; returns false when the ticket was already boarded (first scan wins). */
    fun markBoarded(tripId: String, ticketId: String, scannedAtEpochMillis: Long = System.currentTimeMillis()): Boolean {
        val current = boardedFor(tripId)
        if (ticketId in current) return false
        val updated = current + (ticketId to scannedAtEpochMillis)
        prefs.edit()
            .putString(key(tripId), updated.entries.joinToString(ENTRY_SEPARATOR) { "${it.key}$FIELD_SEPARATOR${it.value}" })
            .apply()
        return true
    }

    private fun key(tripId: String) = "trip:$tripId"

    private companion object {
        const val PREFS_NAME = "ridevibe_manifest_checkins"
        const val ENTRY_SEPARATOR = ";"
        const val FIELD_SEPARATOR = "="
    }
}
