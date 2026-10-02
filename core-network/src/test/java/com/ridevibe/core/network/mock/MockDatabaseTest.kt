package com.ridevibe.core.network.mock

import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.network.api.CrsApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mock is what every demo and tester sees, so its product rules are
 * pinned here: seat layouts per class, passage on sea services, the sold-out
 * buckets, lapsed cash reservations, idempotent booking and the support
 * console's reach into rider tickets.
 */
class MockDatabaseTest {

    private val db = MockDatabase()
    private val tomorrow = System.currentTimeMillis() + 2 * 86_400_000L
    private val juan = Passenger(fullName = "Juan Dela Cruz", type = PassengerType.REGULAR)

    private fun busTrips(origin: String, destination: String, busClass: BusClass? = null): List<Trip> =
        db.searchTrips(origin, destination, tomorrow, busClass)

    @Test
    fun `bus layouts are 2+2 x 11 for ordinary and deluxe and 2+1 x 10 for luxury`() {
        val ordinary = busTrips("Cubao", "Baguio", BusClass.ORDINARY).first()
        val luxury = busTrips("Cubao", "Baguio", BusClass.LUXURY).first()

        val ordinarySeats = db.seatMap(ordinary.id)
        assertEquals(44, ordinarySeats.size)
        assertEquals(11, ordinarySeats.maxOf { it.row })
        assertEquals(setOf(1, 2, 3, 4), ordinarySeats.map { it.column }.toSet())

        val luxurySeats = db.seatMap(luxury.id)
        assertEquals(30, luxurySeats.size)
        assertEquals(10, luxurySeats.maxOf { it.row })
        assertEquals(setOf(1, 2, 3), luxurySeats.map { it.column }.toSet())
    }

    @Test
    fun `sea services sell passage — no seat map, capacity counter instead`() {
        val sailing = db.searchTrips("Batangas Port", "Calapan", tomorrow, null).first { it.rideKind == RideKind.FERRY }

        assertTrue(db.seatMap(sailing.id).isEmpty())
        assertEquals(db.availableSpaceCount(sailing), sailing.availableSeatCount)
    }

    @Test
    fun `booking passage decrements spaces and uses P-labels`() {
        val sailing = db.searchTrips("Batangas Port", "Calapan", tomorrow, null)
            .first { it.rideKind.sellsPassage && it.availableSeatCount >= 2 }
        val before = sailing.availableSeatCount

        val ticket = db.createTicket(sailing.id, listOf("P1", "P2"), juan, emptyList(), 0, PaymentMethod.GCASH, "ref-sea")

        assertEquals(listOf("P1", "P2"), ticket.seatLabels)
        assertEquals(before - 2, db.availableSpaceCount(sailing))
    }

    @Test
    fun `some generated bus trips are sold out and some nearly full`() {
        val trips = listOf("Cubao" to "Baguio", "Pasay" to "Baguio", "PITX" to "Naga", "Cubao" to "Dagupan", "Pasay" to "Legazpi")
            .flatMap { (o, d) -> busTrips(o, d) }
            .filter { it.rideKind == RideKind.BUS }
        assertTrue("need a decent sample, got ${trips.size}", trips.size > 40)

        val soldOut = trips.count { it.availableSeatCount == 0 }
        val nearlyFull = trips.count { it.availableSeatCount in 1..2 }
        assertTrue("expected some sold-out trips, got $soldOut of ${trips.size}", soldOut > 0)
        assertTrue("expected some nearly-full trips, got $nearlyFull of ${trips.size}", nearlyFull > 0)
        assertTrue("not everything may be sold out", soldOut < trips.size / 2)
    }

    @Test
    fun `unknown corridor is empty but advertised demo corridors keep inventory`() {
        assertTrue(db.searchTrips("Cubao", "Nowhere", tomorrow, null).isEmpty())
        assertTrue(db.searchTrips("Manila", "Boracay", tomorrow, null).isNotEmpty())
        assertTrue(db.searchTrips("Manila", "Baguio", tomorrow, null).isNotEmpty())
    }

    @Test
    fun `bus classes come from the merged corpus`() {
        assertEquals(BusClass.values().toList(), db.availableBusClasses())
    }

    @Test
    fun `booking is idempotent on the client reference`() {
        val trip = busTrips("Cubao", "Baguio").first { it.availableSeatCount > 3 }
        val seat = db.seatMap(trip.id).first { it.status == SeatStatus.AVAILABLE }
        db.updateSeat(trip.id, seat.id, SeatStatus.LOCKED, MOCK_CURRENT_USER_ID)

        val first = db.createTicket(trip.id, listOf(seat.id), juan, emptyList(), 0, PaymentMethod.GCASH, "attempt-1")
        val retry = db.createTicket(trip.id, listOf(seat.id), juan, emptyList(), 0, PaymentMethod.GCASH, "attempt-1")

        assertEquals(first.id, retry.id)
        assertEquals("attempt-1", first.clientReference)
        assertEquals(BookingStatus.CONFIRMED, first.status)
        assertEquals(SeatStatus.OCCUPIED, db.seatMap(trip.id).first { it.id == seat.id }.status)
    }

    @Test
    fun `a seat held by someone else cannot be booked`() {
        val trip = busTrips("Cubao", "Baguio").first { it.availableSeatCount > 3 }
        val seat = db.seatMap(trip.id).first { it.status == SeatStatus.AVAILABLE }
        db.updateSeat(trip.id, seat.id, SeatStatus.LOCKED, MOCK_OTHER_PASSENGER_ID)

        val error = runCatching {
            db.createTicket(trip.id, listOf(seat.id), juan, emptyList(), 0, PaymentMethod.GCASH, "attempt-x")
        }.exceptionOrNull()

        assertTrue(error is CrsApiException)
        assertEquals(409, (error as CrsApiException).code)
    }

    @Test
    fun `lapsed cash-on-board reservations are cancelled and free their seats`() {
        val trip = busTrips("Cubao", "Baguio").first { it.availableSeatCount > 3 }
        val seat = db.seatMap(trip.id).first { it.status == SeatStatus.AVAILABLE }
        // Booking requires a live hold by this rider, exactly like the CRS.
        db.updateSeat(trip.id, seat.id, SeatStatus.LOCKED, MOCK_CURRENT_USER_ID)
        val ticket = db.createTicket(trip.id, listOf(seat.id), juan, emptyList(), 0, PaymentMethod.CASH_ON_BOARD, "cash-1")
        assertNotNull(ticket.reservationExpiresAtEpochMillis)
        assertEquals(SeatStatus.OCCUPIED, db.seatMap(trip.id).first { it.id == seat.id }.status)

        db.sweepExpiredReservations(nowEpochMillis = ticket.reservationExpiresAtEpochMillis!! + 1)

        assertEquals(BookingStatus.CANCELLED, db.getTicket(ticket.id)?.status)
        assertNull(db.getTicket(ticket.id)?.reservationExpiresAtEpochMillis)
        assertEquals(SeatStatus.AVAILABLE, db.seatMap(trip.id).first { it.id == seat.id }.status)
    }

    @Test
    fun `seeded history covers student, senior, sea passage and cash on board`() {
        val history = db.allTickets()
        assertTrue(history.any { it.primaryPassenger.type == PassengerType.STUDENT })
        assertTrue(history.any { it.primaryPassenger.type == PassengerType.SENIOR_CITIZEN })
        assertTrue(history.any { it.trip.rideKind.sellsPassage })
        assertTrue(history.any { it.paymentStatus.name == "CASH_ON_BOARD" && it.status == BookingStatus.CONFIRMED })
    }

    @Test
    fun `support cancel and refund reach the rider's ticket`() {
        val staff = MockStaffDatabase(db)
        val trip = busTrips("Cubao", "Baguio").first { it.availableSeatCount > 3 }
        val seat = db.seatMap(trip.id).first { it.status == SeatStatus.AVAILABLE }
        // Booking requires a live hold by this rider, exactly like the CRS.
        db.updateSeat(trip.id, seat.id, SeatStatus.LOCKED, MOCK_CURRENT_USER_ID)
        val ticket = db.createTicket(trip.id, listOf(seat.id), juan, emptyList(), 0, PaymentMethod.GCASH, "support-1")

        val freed = staff.cancelBooking(ticket.id, "Passenger request")
        assertEquals(ticket.seatLabels, freed)
        assertEquals(BookingStatus.CANCELLED, db.getTicket(ticket.id)?.status)
        assertEquals(SeatStatus.AVAILABLE, db.seatMap(trip.id).first { it.id == seat.id }.status)

        staff.setRefundStatus(ticket.id, RefundStatus.REFUNDED, "GCash refund sent")
        assertEquals(BookingStatus.REFUNDED, db.getTicket(ticket.id)?.status)
        assertTrue(staff.searchBookings("", BookingStatus.REFUNDED, 50).any { it.id == ticket.id })
    }
}
