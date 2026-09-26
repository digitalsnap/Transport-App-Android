package com.ridevibe.core.network.session

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.session.BookingCart
import com.ridevibe.core.domain.session.BookingCartState
import com.ridevibe.core.domain.session.CartLeg
import com.ridevibe.core.network.storage.InMemoryKeyValueStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsBookingCartStoreTest {

    private val prefs = InMemoryKeyValueStore()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val store = PrefsBookingCartStore(prefs, json)

    private val fullState = BookingCartState(
        isRoundTrip = true,
        origin = "Cubao",
        destination = "Baguio",
        departDateMillis = 1_790_000_000_000L,
        returnDateMillis = 1_790_300_000_000L,
        busClass = BusClass.DELUXE,
        rideKind = RideKind.BUS,
        adults = 2,
        children = 1,
        infants = 1,
        forSelf = false,
        outboundTripId = "TRIP-1",
        outboundSeatIds = listOf("1A", "1B", "1C"),
        outboundHoldExpiresAtEpochMillis = 1_790_000_600_000L,
        outboundFarePhp = 795.0,
        returnTripId = "TRIP-2",
        returnSeatIds = listOf("P1", "P2", "P3"),
        returnHoldExpiresAtEpochMillis = 1_790_000_700_000L,
        returnFarePhp = 700.0,
    )

    @Test
    fun `empty store loads null`() {
        assertNull(store.load())
    }

    @Test
    fun `every field round-trips`() {
        store.save(fullState)
        assertEquals(fullState, store.load())
        assertEquals(fullState, PrefsBookingCartStore(prefs, json).load()) // fresh instance, same prefs
    }

    @Test
    fun `nullable fields round-trip as null`() {
        val minimal = BookingCartState(origin = "PITX", destination = "Batangas Port", departDateMillis = 5L)
        store.save(minimal)
        assertEquals(minimal, store.load())
    }

    @Test
    fun `clear forgets the cart`() {
        store.save(fullState)
        store.clear()
        assertNull(store.load())
        assertTrue(prefs.snapshot().isEmpty())
    }

    @Test
    fun `unreadable json is discarded instead of crashing`() {
        prefs.put("cart_state", "{not json")
        assertNull(store.load())
        assertNull(prefs.get("cart_state"))
    }

    @Test
    fun `an unknown enum name loads as null rather than failing the whole cart`() {
        prefs.put("cart_state", """{"version":1,"origin":"A","destination":"B","busClass":"SLEEPER","rideKind":"PLANE"}""")
        val loaded = store.load()!!
        assertNull(loaded.busClass)
        assertNull(loaded.rideKind)
        assertEquals("A", loaded.origin)
    }

    @Test
    fun `an old format version is dropped`() {
        prefs.put("cart_state", """{"version":0,"origin":"A","destination":"B"}""")
        assertNull(store.load())
    }

    @Test
    fun `BookingCart restores through the store after a process death`() {
        val cart = BookingCart(store)
        cart.prime(true, "Cubao", "Baguio", 1L, 2L, BusClass.LUXURY, 1, 0, 0, true)
        cart.setOutboundLeg("TRIP-9", listOf("2A"), holdExpiresAt = 77L, farePhp = 1055.0, rideKind = RideKind.BUS)

        val revived = BookingCart(PrefsBookingCartStore(prefs, json))

        assertEquals(cart.state.value, revived.state.value)
        assertEquals(CartLeg.RETURN, revived.currentLeg)
        assertEquals(77L, revived.outboundHoldExpiresAtEpochMillis)
    }
}
