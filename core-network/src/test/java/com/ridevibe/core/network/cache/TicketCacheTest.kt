package com.ridevibe.core.network.cache

import com.ridevibe.core.network.dto.TicketDto
import com.ridevibe.core.network.dto.TripDto
import com.ridevibe.core.network.storage.InMemoryKeyValueStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketCacheTest {

    private val prefs = InMemoryKeyValueStore()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cache = TicketCache(prefs, json)

    private fun ticket(id: String, departure: Long, status: String? = "CONFIRMED") = TicketDto(
        id = id,
        trip = TripDto(
            id = "T-$id",
            operatorName = "Victory Liner",
            origin = "Cubao",
            destination = "Baguio",
            departureEpochMillis = departure,
            arrivalEpochMillis = departure + 1,
            busClass = "ORDINARY",
            farePhp = 485.0,
            availableSeatCount = 0,
        ),
        seatLabels = listOf("1A"),
        passengerFullName = "Juan Dela Cruz",
        passengerType = "REGULAR",
        paymentStatus = "PAID",
        qrPayload = "RIDEVIBE|$id",
        status = status,
        clientReference = "ref-$id",
    )

    @Test
    fun `empty cache reads as empty with no fetch time`() {
        assertTrue(cache.tickets().isEmpty())
        assertNull(cache.lastFetchedAtEpochMillis())
    }

    @Test
    fun `replaceAll round-trips and records when it happened`() {
        val tickets = listOf(ticket("A", 10L), ticket("B", 30L), ticket("C", 20L))
        cache.replaceAll(tickets, fetchedAtEpochMillis = 999L)

        val reloaded = TicketCache(prefs, json)
        assertEquals(listOf("B", "C", "A"), reloaded.tickets().map { it.id }) // newest departure first
        assertEquals(999L, reloaded.lastFetchedAtEpochMillis())
        assertEquals(tickets.first { it.id == "B" }, reloaded.tickets().first())
    }

    @Test
    fun `upsert replaces a ticket with the same id and keeps the rest`() {
        cache.replaceAll(listOf(ticket("A", 10L), ticket("B", 30L)))
        cache.upsert(ticket("A", 10L, status = "CANCELLED"))

        val byId = cache.tickets().associateBy { it.id }
        assertEquals(2, byId.size)
        assertEquals("CANCELLED", byId.getValue("A").status)
        assertEquals("CONFIRMED", byId.getValue("B").status)
    }

    @Test
    fun `cache keeps at most fifty tickets, dropping the oldest departures`() {
        cache.replaceAll((1..60).map { ticket("T$it", it * 1_000L) })
        val kept = cache.tickets()
        assertEquals(TicketCache.MAX_TICKETS, kept.size)
        assertEquals("T60", kept.first().id)
        assertEquals("T11", kept.last().id)

        cache.upsert(ticket("NEW", 61_000L))
        assertEquals(TicketCache.MAX_TICKETS, cache.tickets().size)
        assertEquals("NEW", cache.tickets().first().id)
    }

    @Test
    fun `clear empties both keys`() {
        cache.replaceAll(listOf(ticket("A", 1L)))
        cache.clear()
        assertTrue(cache.tickets().isEmpty())
        assertNull(cache.lastFetchedAtEpochMillis())
        assertTrue(prefs.snapshot().isEmpty())
    }

    @Test
    fun `corrupt json is discarded instead of crashing My Bookings`() {
        prefs.put("tickets", "[{oops")
        prefs.put("fetched_at", "5")
        assertTrue(cache.tickets().isEmpty())
        assertNull(prefs.get("tickets"))
    }
}
