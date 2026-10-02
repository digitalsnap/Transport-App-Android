package com.ridevibe.core.domain.session

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.RideKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingCartTest {

    private class InMemoryStore(var saved: BookingCartState? = null) : BookingCartStore {
        var saveCount = 0
        var clearCount = 0
        override fun load(): BookingCartState? = saved
        override fun save(state: BookingCartState) {
            saved = state
            saveCount++
        }
        override fun clear() {
            saved = null
            clearCount++
        }
    }

    private fun BookingCart.primeRoundTrip() = prime(
        isRoundTrip = true,
        origin = "Cubao",
        destination = "Baguio",
        departDateMillis = 1_000L,
        returnDateMillis = 2_000L,
        busClass = BusClass.DELUXE,
        adults = 2,
        children = 1,
        infants = 1,
        forSelf = true,
    )

    @Test
    fun `round trip walks outbound then return then checkout`() {
        val cart = BookingCart(InMemoryStore())
        cart.primeRoundTrip()
        assertEquals(CartLeg.OUTBOUND, cart.currentLeg)
        assertEquals(3, cart.state.value.seatCount)

        cart.setOutboundLeg("T-OUT", listOf("1A", "1B", "1C"), holdExpiresAt = 99L, farePhp = 795.0, rideKind = RideKind.BUS)
        assertEquals(CartLeg.RETURN, cart.currentLeg)
        assertEquals("T-OUT", cart.outboundTripId)
        assertEquals(795.0, cart.outboundFarePhp)
        assertEquals(RideKind.BUS, cart.rideKind)

        cart.setReturnLeg("T-RET", listOf("P1", "P2", "P3"), holdExpiresAt = 120L, farePhp = 700.0)
        assertTrue(cart.state.value.hasReturnLeg)
        assertEquals(listOf("P1", "P2", "P3"), cart.returnSeatIds)
        assertEquals(120L, cart.returnHoldExpiresAtEpochMillis)
    }

    @Test
    fun `one way never asks for a return leg`() {
        val cart = BookingCart(InMemoryStore())
        cart.prime(false, "PITX", "Batangas Port", 1L, null, null, 1, 0, 0, true)
        assertEquals(CartLeg.ONE_WAY, cart.currentLeg)
        cart.setOutboundLeg("T1", listOf("3C"))
        assertEquals(CartLeg.ONE_WAY, cart.currentLeg)
    }

    @Test
    fun `re-picking the outbound drops a return leg picked for the old outbound`() {
        val cart = BookingCart(InMemoryStore())
        cart.primeRoundTrip()
        cart.setOutboundLeg("T-OUT", listOf("1A"))
        cart.setReturnLeg("T-RET", listOf("2A"))

        cart.setOutboundLeg("T-OUT-2", listOf("5A"))

        assertNull(cart.returnTripId)
        assertTrue(cart.returnSeatIds.isEmpty())
        assertEquals(CartLeg.RETURN, cart.currentLeg)
    }

    @Test
    fun `clearReturnLeg keeps the outbound`() {
        val cart = BookingCart(InMemoryStore())
        cart.primeRoundTrip()
        cart.setOutboundLeg("T-OUT", listOf("1A"))
        cart.setReturnLeg("T-RET", listOf("2A"), farePhp = 1.0)

        cart.clearReturnLeg()

        assertEquals("T-OUT", cart.outboundTripId)
        assertNull(cart.returnTripId)
        assertNull(cart.returnFarePhp)
    }

    @Test
    fun `reset clears every field and the store`() {
        val store = InMemoryStore()
        val cart = BookingCart(store)
        cart.primeRoundTrip()
        cart.setOutboundLeg("T-OUT", listOf("1A"), holdExpiresAt = 5L, farePhp = 9.0, rideKind = RideKind.FERRY)
        cart.setReturnLeg("T-RET", listOf("2A"), holdExpiresAt = 6L, farePhp = 8.0)

        cart.reset()

        assertEquals(BookingCartState(), cart.state.value)
        assertEquals(1, store.clearCount)
        assertNull(store.saved)
    }

    @Test
    fun `every mutation is persisted and a new cart restores from the store`() {
        val store = InMemoryStore()
        val cart = BookingCart(store)
        cart.primeRoundTrip()
        cart.setOutboundLeg("T-OUT", listOf("1A", "1B"), holdExpiresAt = 42L, farePhp = 795.0)
        assertEquals(2, store.saveCount)

        val restored = BookingCart(store)

        assertEquals(cart.state.value, restored.state.value)
        assertEquals("T-OUT", restored.outboundTripId)
        assertEquals(42L, restored.outboundHoldExpiresAtEpochMillis)
        assertEquals(CartLeg.RETURN, restored.currentLeg)
    }

    @Test
    fun `priming a new search discards legs from the previous one`() {
        val cart = BookingCart(InMemoryStore())
        cart.primeRoundTrip()
        cart.setOutboundLeg("T-OUT", listOf("1A"))

        cart.prime(false, "Pasay", "Naga", 3L, null, null, 1, 0, 0, false)

        assertNull(cart.outboundTripId)
        assertEquals("Pasay", cart.origin)
        assertEquals(false, cart.forSelf)
    }
}
