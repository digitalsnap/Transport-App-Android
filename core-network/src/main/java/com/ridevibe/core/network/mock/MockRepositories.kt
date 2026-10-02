package com.ridevibe.core.network.mock

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Itinerary
import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.model.SupportMessage
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.UserProfile
import com.ridevibe.core.domain.model.Vehicle
import com.ridevibe.core.domain.model.Wallet
import com.ridevibe.core.domain.repository.CheckoutRepository
import com.ridevibe.core.domain.repository.ItineraryRepository
import com.ridevibe.core.domain.repository.ProfileRepository
import com.ridevibe.core.domain.repository.SeatRepository
import com.ridevibe.core.domain.repository.SupportRepository
import com.ridevibe.core.domain.repository.TripRepository
import com.ridevibe.core.domain.repository.WalletRepository
import com.ridevibe.core.network.api.CrsApiException
import com.ridevibe.core.network.api.apiResult
import com.ridevibe.core.network.cache.TicketCache
import com.ridevibe.core.network.dto.TicketDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

// MOCK DATA LAYER — DELETE BEFORE GOING LIVE (see MockDatabase.kt).
//
// Every method does its work off the main thread (Dispatchers.Default), like
// a real network call, and every refusal is a CrsApiException with the code
// and message the CRS would send — so the screens behave identically against
// either backend.

/** Simulated network latency so loading states are visible, like a real API. */
private const val FAKE_LATENCY_MS = 500L

/** A mock "server call": off the main thread, with latency, refusals shaped as the CRS would. */
private suspend fun <T> mockCall(latencyMs: Long = FAKE_LATENCY_MS, block: () -> T): Result<T> =
    withContext(Dispatchers.Default) {
        apiResult {
            delay(latencyMs)
            block()
        }
    }

/** For the mock-only repositories whose interfaces have no Result: still off the main thread. */
private suspend fun <T> mockWork(latencyMs: Long = 0L, block: () -> T): T =
    withContext(Dispatchers.Default) {
        if (latencyMs > 0) delay(latencyMs)
        block()
    }

private fun refuse(code: Int, message: String): Nothing = throw CrsApiException(code, message)

@Singleton
class MockTripRepository @Inject constructor(
    private val db: MockDatabase,
) : TripRepository {

    override suspend fun searchTrips(
        origin: String,
        destination: String,
        departureDateEpochMillis: Long,
        busClass: BusClass?,
    ): Result<List<Trip>> = mockCall { db.searchTrips(origin, destination, departureDateEpochMillis, busClass) }

    override suspend fun getTrip(tripId: String): Result<Trip> =
        mockCall(FAKE_LATENCY_MS / 2) { db.getTrip(tripId) ?: refuse(404, "Trip not found") }

    override suspend fun searchRelated(
        query: String,
        departureDateEpochMillis: Long,
        returnDateEpochMillis: Long?,
    ): Result<List<Trip>> = mockCall { db.searchRelated(query, departureDateEpochMillis, returnDateEpochMillis) }

    override suspend fun findJourneys(query: String): Result<List<Journey>> =
        mockCall(latencyMs = 0L) { db.journeys(query) }

    override suspend fun getLocations(): Result<List<TerminalLocation>> =
        mockCall(latencyMs = 0L) { db.locations() }

    override suspend fun getAvailableBusClasses(): Result<List<BusClass>> =
        mockCall(latencyMs = 0L) { db.availableBusClasses() }
}

@Singleton
class MockSeatRepository @Inject constructor(
    private val db: MockDatabase,
) : SeatRepository {

    /** Empty for ferries and fastcrafts: passage has no seat map. */
    override suspend fun getSeatMap(tripId: String): Result<List<Seat>> = mockCall {
        db.getTrip(tripId) ?: refuse(404, "Trip not found")
        db.seatMap(tripId).map { it.asSeenByMe() }
    }

    /**
     * Emits real seat updates from the mock db, plus a simulated "other
     * passenger" who locks then releases a random seat every ~15 seconds —
     * demonstrates the live seat-locking UX without a WebSocket.
     */
    override fun observeSeatEvents(tripId: String): Flow<SeatStatusEvent> = merge(
        db.seatEvents.filter { it.tripId == tripId },
        simulatedOtherPassenger(tripId),
    ).map { it.asSeenByMe() }

    // Same rule as SeatRepositoryImpl: a lock held by this user is shown as
    // SELECTED, so the seat map recognises its own holds on reload.
    private fun Seat.asSeenByMe(): Seat =
        if (status == SeatStatus.LOCKED && lockedByUserId == MOCK_CURRENT_USER_ID) copy(status = SeatStatus.SELECTED) else this

    private fun SeatStatusEvent.asSeenByMe(): SeatStatusEvent =
        if (status == SeatStatus.LOCKED && lockedByUserId == MOCK_CURRENT_USER_ID) copy(status = SeatStatus.SELECTED) else this

    private fun simulatedOtherPassenger(tripId: String): Flow<SeatStatusEvent> = flow {
        while (true) {
            delay(15_000)
            val target = db.availableSeats(tripId).randomOrNull(Random) ?: continue
            db.updateSeat(
                tripId,
                target.id,
                SeatStatus.LOCKED,
                lockedBy = MOCK_OTHER_PASSENGER_ID,
                lockExpiresAt = System.currentTimeMillis() + MOCK_HOLD_TTL_MS,
            )
            delay(8_000)
            // Give the seat back unless someone (the demo passenger) took it meanwhile.
            val stillLockedByOther = db.seatMap(tripId)
                .firstOrNull { it.id == target.id }?.lockedByUserId == MOCK_OTHER_PASSENGER_ID
            if (stillLockedByOther) {
                db.updateSeat(tripId, target.id, SeatStatus.AVAILABLE, lockedBy = null)
            }
        }
    }

    /** Holds the seat for the server TTL and returns the expiry, like a `200 SeatLockResponse`. */
    override suspend fun lockSeat(tripId: String, seatId: String): Result<Long?> = mockCall(FAKE_LATENCY_MS / 2) {
        val trip = db.getTrip(tripId) ?: refuse(404, "Trip not found")
        if (trip.rideKind.sellsPassage) refuse(409, "This sailing sells passage, not seats")
        val seat = db.seatMap(tripId).firstOrNull { it.id == seatId } ?: refuse(404, "Seat not found")
        if (seat.status == SeatStatus.OCCUPIED) refuse(409, "Seat ${seat.label} is already sold")
        if (seat.status == SeatStatus.LOCKED && seat.lockedByUserId != MOCK_CURRENT_USER_ID) {
            refuse(409, "Seat ${seat.label} is held by another passenger")
        }
        val heldByMe = db.seatMap(tripId).count { it.status == SeatStatus.LOCKED && it.lockedByUserId == MOCK_CURRENT_USER_ID }
        if (heldByMe >= MAX_ACTIVE_HOLDS && seat.lockedByUserId != MOCK_CURRENT_USER_ID) {
            refuse(409, "Too many active holds (max $MAX_ACTIVE_HOLDS)")
        }
        val expiresAt = System.currentTimeMillis() + MOCK_HOLD_TTL_MS
        db.updateSeat(tripId, seatId, SeatStatus.LOCKED, lockedBy = MOCK_CURRENT_USER_ID, lockExpiresAt = expiresAt)
        expiresAt
    }

    override suspend fun releaseSeat(tripId: String, seatId: String): Result<Unit> = mockCall(latencyMs = 0L) {
        // Only release a seat this user is holding. Guards against ever
        // flipping an OCCUPIED (paid) seat or another passenger's lock back
        // to AVAILABLE — e.g. when a ViewModel cleans up after booking.
        val seat = db.seatMap(tripId).firstOrNull { it.id == seatId }
        if (seat?.status == SeatStatus.LOCKED && seat.lockedByUserId == MOCK_CURRENT_USER_ID) {
            db.updateSeat(tripId, seatId, SeatStatus.AVAILABLE, lockedBy = null)
        }
    }

    override suspend fun disconnect(tripId: String) = Unit // nothing to close in-memory

    private companion object {
        /** The CRS's per-user active-hold cap (openapi.yaml `x-rate-limits`). */
        const val MAX_ACTIVE_HOLDS = 12
    }
}

@Singleton
class MockCheckoutRepository @Inject constructor(
    private val db: MockDatabase,
    private val ticketCache: TicketCache,
) : CheckoutRepository {

    override suspend fun confirmBooking(
        tripId: String,
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
        infantCount: Int,
        paymentMethod: PaymentMethod,
        promoCode: String?,
        clientReference: String,
    ): Result<Ticket> = mockCall {
        db.createTicket(tripId, seatIds, primaryPassenger, coPassengers, infantCount, paymentMethod, clientReference)
            .also { ticketCache.upsert(TicketDto.from(it)) }
    }

    override suspend fun getTicket(ticketId: String): Result<Ticket> = mockCall(FAKE_LATENCY_MS / 2) {
        (db.getTicket(ticketId) ?: refuse(404, "Ticket not found"))
            .also { ticketCache.upsert(TicketDto.from(it)) }
    }

    // Writes through to the same cache as the real client, so the offline
    // My Bookings path is demoable on mocks too.
    override suspend fun getMyBookings(): Result<List<Ticket>> = mockCall(FAKE_LATENCY_MS / 2) {
        db.allTickets().also { tickets -> ticketCache.replaceAll(tickets.map { TicketDto.from(it) }) }
    }

    override fun cachedBookings(): List<Ticket> =
        ticketCache.tickets().map { it.toDomain() }.sortedByDescending { it.trip.departureEpochMillis }
}

@Singleton
class MockProfileRepository @Inject constructor(
    private val db: MockDatabase,
) : ProfileRepository {

    override suspend fun getProfile(): UserProfile = mockWork { db.getProfile() }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = mockCall(latencyMs = 0L) { db.saveProfile(profile) }

    override suspend fun getVehicles(): List<Vehicle> = mockWork { db.getVehicles() }

    override suspend fun addVehicle(plateNumber: String, ltoCertificateUri: String): Result<Vehicle> =
        mockCall(latencyMs = 0L) {
            if (plateNumber.isBlank()) refuse(400, "Plate number is required")
            if (ltoCertificateUri.isBlank()) refuse(400, "LTO Certificate of Registration is required")
            db.addVehicle(plateNumber, ltoCertificateUri)
        }

    override suspend fun removeVehicle(vehicleId: String): Result<Unit> = mockCall(latencyMs = 0L) { db.removeVehicle(vehicleId) }
}

@Singleton
class MockItineraryRepository @Inject constructor(
    private val db: MockDatabase,
) : ItineraryRepository {

    override suspend fun getItineraries(): List<Itinerary> = mockWork { db.itineraries() }

    override suspend fun addItinerary(journey: Journey, startDateMillis: Long): Itinerary =
        mockWork { db.addItinerary(journey, startDateMillis) }

    override suspend fun setLegDone(itineraryId: String, legIndex: Int, done: Boolean) =
        mockWork { db.setLegDone(itineraryId, legIndex, done) }

    override suspend fun removeItinerary(itineraryId: String) = mockWork { db.removeItinerary(itineraryId) }
}

@Singleton
class MockWalletRepository @Inject constructor(
    private val db: MockDatabase,
) : WalletRepository {

    override suspend fun getWallet(): Wallet = mockWork(FAKE_LATENCY_MS / 2) {
        Wallet(balancePhp = db.walletBalancePhp(), transactions = db.walletTransactions())
    }
}

@Singleton
class MockSupportRepository @Inject constructor(
    private val db: MockDatabase,
) : SupportRepository {

    private val cannedReplies = listOf(
        "Thanks for reaching out! An agent will reply within a few minutes.",
        "Got it — we're checking that for you now.",
        "Thanks for the details. For urgent departures, you can also call the terminal hotline shown on your ticket.",
    )
    private var replyIndex = 0

    override suspend fun getMessages(): List<SupportMessage> = mockWork { db.supportMessages() }

    override suspend fun sendMessage(text: String): List<SupportMessage> = withContext(Dispatchers.Default) {
        db.addSupportMessage(
            SupportMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                fromUser = true,
                timestampEpochMillis = System.currentTimeMillis(),
            ),
        )
        // Simulated agent acknowledgement until a real support channel exists.
        delay(900)
        db.addSupportMessage(
            SupportMessage(
                id = UUID.randomUUID().toString(),
                text = cannedReplies[replyIndex++ % cannedReplies.size],
                fromUser = false,
                timestampEpochMillis = System.currentTimeMillis(),
            ),
        )
        db.supportMessages()
    }
}
