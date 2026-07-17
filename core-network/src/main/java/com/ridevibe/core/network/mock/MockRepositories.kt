package com.ridevibe.core.network.mock

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.UserProfile
import com.ridevibe.core.domain.model.Vehicle
import com.ridevibe.core.domain.repository.CheckoutRepository
import com.ridevibe.core.domain.repository.ProfileRepository
import com.ridevibe.core.domain.repository.SeatRepository
import com.ridevibe.core.domain.repository.TripRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

// MOCK DATA LAYER — DELETE BEFORE GOING LIVE (see MockDatabase.kt).

/** Simulated network latency so loading states are visible, like a real API. */
private const val FAKE_LATENCY_MS = 500L

private const val CURRENT_USER_ID = "me"

@Singleton
class MockTripRepository @Inject constructor(
    private val db: MockDatabase,
) : TripRepository {

    override suspend fun searchTrips(
        origin: String,
        destination: String,
        departureDateEpochMillis: Long,
        busClass: BusClass?,
    ): Result<List<Trip>> {
        delay(FAKE_LATENCY_MS)
        return Result.success(db.searchTrips(origin, destination, departureDateEpochMillis, busClass))
    }

    override suspend fun getTrip(tripId: String): Result<Trip> {
        delay(FAKE_LATENCY_MS / 2)
        return db.getTrip(tripId)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Trip not found: $tripId"))
    }

    override suspend fun getLocations(): List<TerminalLocation> = db.locations()

    override suspend fun getAvailableBusClasses(): List<BusClass> = db.availableBusClasses()
}

@Singleton
class MockSeatRepository @Inject constructor(
    private val db: MockDatabase,
) : SeatRepository {

    override suspend fun getSeatMap(tripId: String): List<Seat> {
        delay(FAKE_LATENCY_MS)
        return db.seatMap(tripId)
    }

    /**
     * Emits real seat updates from the mock db, plus a simulated "other
     * passenger" who locks then releases a random seat every ~15 seconds —
     * demonstrates the live seat-locking UX without a WebSocket.
     */
    override fun observeSeatEvents(tripId: String): Flow<SeatStatusEvent> = merge(
        db.seatEvents.filter { it.tripId == tripId },
        simulatedOtherPassenger(tripId),
    )

    private fun simulatedOtherPassenger(tripId: String): Flow<SeatStatusEvent> = flow {
        while (true) {
            delay(15_000)
            val target = db.availableSeats(tripId).randomOrNull(Random) ?: continue
            db.updateSeat(tripId, target.id, SeatStatus.LOCKED, lockedBy = "other-passenger")
            delay(8_000)
            // Give the seat back unless someone (the demo passenger) took it meanwhile.
            val stillLockedByOther = db.seatMap(tripId)
                .firstOrNull { it.id == target.id }?.lockedByUserId == "other-passenger"
            if (stillLockedByOther) {
                db.updateSeat(tripId, target.id, SeatStatus.AVAILABLE, lockedBy = null)
            }
        }
    }

    override suspend fun lockSeat(tripId: String, seatId: String): Result<Unit> {
        delay(FAKE_LATENCY_MS / 2)
        val seat = db.seatMap(tripId).firstOrNull { it.id == seatId }
            ?: return Result.failure(IllegalArgumentException("Unknown seat $seatId"))
        if (seat.status == SeatStatus.OCCUPIED || seat.lockedByUserId == "other-passenger") {
            return Result.failure(IllegalStateException("Seat $seatId is no longer available"))
        }
        db.updateSeat(tripId, seatId, SeatStatus.LOCKED, lockedBy = CURRENT_USER_ID)
        return Result.success(Unit)
    }

    override suspend fun releaseSeat(tripId: String, seatId: String): Result<Unit> {
        db.updateSeat(tripId, seatId, SeatStatus.AVAILABLE, lockedBy = null)
        return Result.success(Unit)
    }

    override suspend fun disconnect(tripId: String) = Unit // nothing to close in-memory
}

@Singleton
class MockCheckoutRepository @Inject constructor(
    private val db: MockDatabase,
) : CheckoutRepository {

    override suspend fun confirmBooking(
        tripId: String,
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
        infantCount: Int,
        paymentMethod: PaymentMethod,
        promoCode: String?,
    ): Result<Ticket> {
        delay(FAKE_LATENCY_MS)
        return db.createTicket(tripId, seatIds, primaryPassenger, coPassengers, infantCount, paymentMethod)
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Trip not found: $tripId"))
    }

    override suspend fun getTicket(ticketId: String): Result<Ticket> {
        delay(FAKE_LATENCY_MS / 2)
        return db.getTicket(ticketId)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Ticket not found: $ticketId"))
    }

    override suspend fun getMyBookings(): List<Ticket> {
        delay(FAKE_LATENCY_MS / 2)
        return db.allTickets()
    }
}

@Singleton
class MockProfileRepository @Inject constructor(
    private val db: MockDatabase,
) : ProfileRepository {

    override suspend fun getProfile(): UserProfile = db.getProfile()

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        db.saveProfile(profile)
        return Result.success(Unit)
    }

    override suspend fun getVehicles(): List<Vehicle> = db.getVehicles()

    override suspend fun addVehicle(plateNumber: String, ltoCertificateUri: String): Result<Vehicle> {
        if (plateNumber.isBlank()) return Result.failure(IllegalArgumentException("Plate number is required"))
        if (ltoCertificateUri.isBlank()) {
            return Result.failure(IllegalArgumentException("LTO Certificate of Registration is required"))
        }
        return Result.success(db.addVehicle(plateNumber, ltoCertificateUri))
    }

    override suspend fun removeVehicle(vehicleId: String): Result<Unit> {
        db.removeVehicle(vehicleId)
        return Result.success(Unit)
    }
}
