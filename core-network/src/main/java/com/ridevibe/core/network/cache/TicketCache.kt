package com.ridevibe.core.network.cache

import com.ridevibe.core.network.dto.TicketDto
import com.ridevibe.core.network.log.RideVibeLog
import com.ridevibe.core.network.storage.KeyValueStore
import com.ridevibe.core.network.storage.TicketCacheStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last tickets the app fetched, kept as JSON in the `ridevibe_ticket_cache`
 * preferences so My Bookings and a ticket's QR still open with no signal —
 * a rider at a provincial terminal must be able to show the conductor a
 * ticket bought on Wi-Fi the night before.
 *
 * Capped at [MAX_TICKETS], keeping the latest departures. Only a successful
 * fetch replaces the list; single-ticket lookups upsert into it.
 */
@Singleton
class TicketCache @Inject constructor(
    @TicketCacheStore private val store: KeyValueStore,
    private val json: Json,
) {
    private val serializer = ListSerializer(TicketDto.serializer())

    fun tickets(): List<TicketDto> {
        val raw = store.get(KEY_TICKETS) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .onFailure {
                RideVibeLog.w("Discarding unreadable ticket cache", it)
                clear()
            }
            .getOrDefault(emptyList())
    }

    /** When [replaceAll] last ran, or null if never. */
    fun lastFetchedAtEpochMillis(): Long? = store.get(KEY_FETCHED_AT)?.toLongOrNull()

    /** A full, successful `GET /v1/bookings` result. */
    fun replaceAll(tickets: List<TicketDto>, fetchedAtEpochMillis: Long = System.currentTimeMillis()) {
        write(tickets)
        store.put(KEY_FETCHED_AT, fetchedAtEpochMillis.toString())
    }

    /** A ticket fetched or issued on its own; replaces any cached copy with the same id. */
    fun upsert(ticket: TicketDto) {
        write(tickets().filterNot { it.id == ticket.id } + ticket)
    }

    fun clear() {
        store.remove(KEY_TICKETS)
        store.remove(KEY_FETCHED_AT)
    }

    private fun write(tickets: List<TicketDto>) {
        val kept = tickets
            .sortedByDescending { it.trip.departureEpochMillis }
            .take(MAX_TICKETS)
        store.put(KEY_TICKETS, json.encodeToString(serializer, kept))
    }

    companion object {
        const val MAX_TICKETS = 50
        private const val KEY_TICKETS = "tickets"
        private const val KEY_FETCHED_AT = "fetched_at"
    }
}
