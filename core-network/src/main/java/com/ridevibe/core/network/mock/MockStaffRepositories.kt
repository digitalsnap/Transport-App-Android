package com.ridevibe.core.network.mock

import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.AccountCredential
import com.ridevibe.core.domain.model.AdminOverview
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.CuratedJourney
import com.ridevibe.core.domain.model.DayCount
import com.ridevibe.core.domain.model.EmailCodeRequested
import com.ridevibe.core.domain.model.ExtraTripCreated
import com.ridevibe.core.domain.model.LiveHold
import com.ridevibe.core.domain.model.ManifestEntry
import com.ridevibe.core.domain.model.NewService
import com.ridevibe.core.domain.model.OnsiteSaleIssued
import com.ridevibe.core.domain.model.OnsiteSaleRequest
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.OperatorSummary
import com.ridevibe.core.domain.model.PartnerOverview
import com.ridevibe.core.domain.model.PartnerTokenIssued
import com.ridevibe.core.domain.model.PassengerAccount
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.RouteSummary
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.ServiceChanged
import com.ridevibe.core.domain.model.ServiceCreated
import com.ridevibe.core.domain.model.ServiceUpdate
import com.ridevibe.core.domain.model.StaffAccount
import com.ridevibe.core.domain.model.StaffAuthOptions
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.core.domain.model.SupportBooking
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.core.domain.repository.AdminRepository
import com.ridevibe.core.domain.repository.PartnerRepository
import com.ridevibe.core.domain.repository.StaffAuthRepository
import com.ridevibe.core.network.api.CrsApiException
import com.ridevibe.core.network.api.apiResult
import com.ridevibe.core.network.auth.StaffSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

// MOCK DATA LAYER — DELETE BEFORE GOING LIVE (see MockDatabase.kt).
//
// In-memory stand-in for the CRS staff surfaces (/auth, /admin/api,
// /partner/api). Sign-ins mirror the backend's DEV-ONLY dummy accounts
// (src/accounts.ts ensureDevAccounts): admin@admin.com / admin and
// partner@partner.com / partner (Victory Liner). Errors are thrown as
// CrsApiException with the same codes and messages the server uses, so the UI
// behaves identically against either.

/** Simulated network latency so loading states are visible, like a real API. */
private const val STAFF_LATENCY_MS = 350L
private const val DAY_MS = 86_400_000L
private const val TRIP_HORIZON_DAYS = 14

/** The one-time key the mock mailer "sends"; mirrors the backend's devCode outside production. */
private const val DEV_EMAIL_CODE = "123456"

/** Mirrors the server's `expiresInDays: 7` on every session. */
private const val STAFF_SESSION_DAYS = 7

/** A mock "server call": off the main thread, with latency, refusals shaped as the CRS would. */
private suspend fun <T> mockCall(block: () -> T): Result<T> = withContext(Dispatchers.Default) {
    apiResult {
        delay(STAFF_LATENCY_MS)
        block()
    }
}

private fun refuse(code: Int, message: String): Nothing = throw CrsApiException(code, message)

// PH calendar arithmetic lives in core-domain (com.ridevibe.core.domain.format.PhTime).

// ── In-memory staff world ─────────────────────────────────────────────────────

/**
 * Shared state behind the three mock staff repositories. Operators, services
 * and accounts are fixtures; trips are generated over the 14-day horizon like
 * the backend's seeder; seat occupancy is seeded deterministically per trip
 * the first time a trip is looked at. Passenger tickets booked through the
 * mock rider flow ([MockDatabase]) show up as support bookings too.
 */
@Singleton
class MockStaffDatabase @Inject constructor(
    private val db: MockDatabase,
) {
    internal data class Operator(val id: Int, val name: String)

    internal data class Service(
        val id: Int,
        val operatorId: Int,
        val origin: String,
        val destination: String,
        val busClass: BusClass,
        val rideKind: RideKind,
        val farePhp: Double,
        val rating: Double?,
        val departureHours: List<Int>,
        val durationMinutes: Int,
    ) {
        val mode: String get() = if (rideKind == RideKind.BUS) "LAND" else "SEA"
        val capacity: Int get() = if (busClass == BusClass.LUXURY) 30 else 44
        val seatLayout: String
            get() = when {
                rideKind != RideKind.BUS -> "Open seating · $capacity capacity"
                busClass == BusClass.LUXURY -> "2x1 (30 seats)"
                else -> "2x2 (44 seats)"
            }
    }

    /** [serviceId] is null for trips that only exist in the rider-side [MockDatabase]. */
    internal data class Trip(
        val id: String,
        val serviceId: Int?,
        val operatorId: Int,
        val operatorName: String,
        val origin: String,
        val destination: String,
        val busClass: BusClass,
        val rideKind: RideKind,
        val departureEpochMillis: Long,
        val arrivalEpochMillis: Long,
        val farePhp: Double,
        val capacity: Int,
    ) {
        val isExternal: Boolean get() = serviceId == null
    }

    internal data class Seat(
        val id: String,
        val label: String,
        val row: Int,
        val column: Int,
        val bookingId: String? = null,
        val heldByUserId: String? = null,
        val holdExpiresAt: Long? = null,
    ) {
        fun status(now: Long): SeatStatus = when {
            bookingId != null -> SeatStatus.OCCUPIED
            heldByUserId != null && (holdExpiresAt ?: 0L) > now -> SeatStatus.LOCKED
            else -> SeatStatus.AVAILABLE
        }
    }

    internal data class Booking(
        val id: String,
        val tripId: String,
        val userId: String,
        val passengerFullName: String,
        val passengerType: PassengerType,
        val infantCount: Int,
        val paymentMethod: PaymentMethod,
        val paymentStatus: PaymentStatus,
        val qrPayload: String,
        val promoCode: String?,
        val status: BookingStatus,
        val refundStatus: RefundStatus,
        val refundNote: String?,
        val cancelReason: String?,
        val seatLabels: List<String>,
        val createdAt: Long,
        val cancelledAt: Long?,
        val refundedAt: Long?,
        val coPassengers: List<CoPassenger>,
    ) {
        val partySize: Int get() = 1 + coPassengers.size
    }

    internal data class Account(
        val id: String,
        val email: String,
        val role: StaffRole,
        val operatorId: Int?,
        val password: String?,
        val createdAt: Long,
        val lastLoginAt: Long?,
    )

    private val lock = Any()

    private val operators = listOf(
        Operator(1, "Victory Liner"),
        Operator(2, "Genesis Transport"),
        Operator(3, "DLTB Co."),
        Operator(4, "JAM Liner"),
        Operator(5, "Five Star"),
        Operator(6, "2GO Travel"),
    )

    private var nextServiceId = 1
    private val services = mutableListOf<Service>().apply {
        fun svc(op: Int, o: String, d: String, cls: BusClass, kind: RideKind, fare: Double, rating: Double, hours: List<Int>, mins: Int) =
            add(Service(nextServiceId++, op, o, d, cls, kind, fare, rating, hours.sorted(), mins))

        svc(1, "Cubao", "Baguio", BusClass.ORDINARY, RideKind.BUS, 485.0, 4.5, listOf(5, 9, 13, 21), 360)
        svc(1, "Pasay", "Baguio", BusClass.ORDINARY, RideKind.BUS, 485.0, 4.5, listOf(6, 10, 22), 375)
        svc(1, "Pasay", "Baguio", BusClass.LUXURY, RideKind.BUS, 1546.0, 4.9, listOf(9, 23), 285)
        svc(1, "Cubao", "Olongapo", BusClass.ORDINARY, RideKind.BUS, 280.0, 4.4, listOf(5, 9, 15), 210)
        svc(2, "Cubao", "Baguio", BusClass.DELUXE, RideKind.BUS, 795.0, 4.6, listOf(6, 10, 14, 22), 330)
        svc(3, "PITX", "Batangas Port", BusClass.ORDINARY, RideKind.BUS, 250.0, 4.3, listOf(5, 9, 13, 17), 150)
        svc(3, "PITX", "Naga", BusClass.ORDINARY, RideKind.BUS, 850.0, 4.3, listOf(7, 17, 20), 540)
        svc(4, "PITX", "Batangas Port", BusClass.ORDINARY, RideKind.BUS, 230.0, 4.2, listOf(6, 10, 14, 18), 150)
        svc(5, "Cubao", "Dagupan", BusClass.ORDINARY, RideKind.BUS, 585.0, 4.4, listOf(5, 11, 17), 270)
        svc(5, "Cubao", "Dagupan", BusClass.DELUXE, RideKind.BUS, 700.0, 4.6, listOf(7, 13, 19), 255)
        svc(6, "Batangas Port", "Caticlan", BusClass.DELUXE, RideKind.FERRY, 1550.0, 4.4, listOf(19, 21), 540)
        svc(6, "Manila North Harbor", "Cebu Port", BusClass.DELUXE, RideKind.FERRY, 2800.0, 4.5, listOf(13, 21), 1320)
    }

    private val trips = LinkedHashMap<String, Trip>()
    private val seats = HashMap<String, MutableList<Seat>>()
    private val bookings = LinkedHashMap<String, Booking>()
    private val materializedDates = mutableSetOf<String>()
    private var seeded = false

    private val accounts = mutableListOf(
        Account("acct-0001", "admin@admin.com", StaffRole.ADMIN, null, "admin", daysAgo(120), daysAgo(0)),
        Account("acct-0002", "partner@partner.com", StaffRole.PARTNER, 1, "partner", daysAgo(90), daysAgo(1)),
        Account("acct-0003", "support@ridevibe.ph", StaffRole.ADMIN, null, null, daysAgo(30), null),
    )
    private val pendingEmailCodes = mutableSetOf<String>()

    private val passengerNames = listOf(
        "Maria Santos", "Jose Reyes", "Ana Cruz", "Carlo Mendoza", "Liza Garcia",
        "Ramon Bautista", "Grace Villanueva", "Paolo Aquino", "Nina Torres", "Miguel Flores",
    )
    private val passengerUserIds = (1..8).map { UUID.nameUUIDFromBytes("rider-$it".toByteArray()).toString() }

    // ── Auth ────────────────────────────────────────────────────────────────

    fun authenticate(email: String, password: String): StaffSession = synchronized(lock) {
        val account = accountByEmail(email)
        if (account?.password == null || account.password != password) refuse(401, "Wrong email or password")
        openSession(account)
    }

    /** Mirrors the server: same reply whether or not the account exists; devCode only when it does. */
    fun requestEmailCode(email: String): EmailCodeRequested = synchronized(lock) {
        val message = "If that email has an account, a sign-in key is on its way."
        val account = accountByEmail(email) ?: return EmailCodeRequested(message)
        pendingEmailCodes += account.email
        EmailCodeRequested(message, devCode = DEV_EMAIL_CODE)
    }

    fun verifyEmailCode(email: String, code: String): StaffSession = synchronized(lock) {
        val normalized = email.trim().lowercase()
        if (normalized !in pendingEmailCodes) refuse(401, "Key expired or not requested — ask for a new one")
        if (code.trim() != DEV_EMAIL_CODE) refuse(401, "Wrong sign-in key")
        pendingEmailCodes -= normalized
        val account = accountByEmail(normalized) ?: refuse(401, "No account for that email")
        openSession(account)
    }

    private fun accountByEmail(email: String): Account? {
        val normalized = email.trim().lowercase()
        return accounts.firstOrNull { it.email == normalized }
    }

    private fun openSession(account: Account): StaffSession {
        val index = accounts.indexOf(account)
        if (index >= 0) accounts[index] = account.copy(lastLoginAt = System.currentTimeMillis())
        return StaffSession(
            token = "s_mock_" + UUID.randomUUID().toString().replace("-", ""),
            email = account.email,
            role = account.role,
            operatorId = account.operatorId,
            operatorName = account.operatorId?.let { operatorName(it) },
            expiresAtEpochMillis = System.currentTimeMillis() + STAFF_SESSION_DAYS * DAY_MS,
        )
    }

    // ── Admin: overview & operators ─────────────────────────────────────────

    fun adminOverview(): AdminOverview = synchronized(lock) {
        prepare()
        val now = System.currentTimeMillis()
        val today = PhTime.todayIso()
        val byDay = (6 downTo 0).map { back ->
            val day = PhTime.plusDays(today, -back)
            val range = PhTime.dayRange(day)!!
            DayCount(day, bookings.values.count { it.createdAt in range })
        }
        AdminOverview(
            operators = operators.size,
            terminals = db.locations().size,
            routes = routeKeys().size,
            services = services.size,
            journeys = journeys().size,
            upcomingTrips = trips.values.count { it.departureEpochMillis > now },
            seats = trips.values.sumOf { it.capacity },
            activeHolds = seats.values.sumOf { list -> list.count { it.status(now) == SeatStatus.LOCKED } },
            users = bookings.values.map { it.userId }.distinct().size,
            bookings = bookings.size,
            cancelledBookings = bookings.values.count { it.status == BookingStatus.CANCELLED },
            refundsPending = bookings.values.count { it.refundStatus == RefundStatus.REQUESTED },
            bookingsByDay = byDay,
        )
    }

    fun operatorSummaries(): List<OperatorSummary> = synchronized(lock) {
        operators.sortedBy { it.name }.map { op ->
            val own = services.filter { it.operatorId == op.id }
            OperatorSummary(
                id = op.id,
                name = op.name,
                services = own.size,
                routes = own.map { it.origin to it.destination }.distinct().size,
                busClasses = own.map { it.busClass.name }.distinct().sorted(),
                rideKinds = own.map { it.rideKind.name }.distinct().sorted(),
                avgRating = own.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it * 100) / 100.0 },
                minFarePhp = own.minOfOrNull { it.farePhp },
                maxFarePhp = own.maxOfOrNull { it.farePhp },
            )
        }
    }

    fun operatorServices(operatorId: Int): List<OperatorService> = synchronized(lock) {
        services.filter { it.operatorId == operatorId }
            .sortedWith(compareBy({ it.origin }, { it.destination }, { it.busClass }))
            .map { it.toDomain() }
    }

    fun issuePartnerToken(operatorId: Int): PartnerTokenIssued = synchronized(lock) {
        val name = operatorName(operatorId) ?: refuse(404, "No such operator")
        PartnerTokenIssued(
            operatorId = operatorId,
            operatorName = name,
            token = "pt_" + randomHex(32),
            note = "Shown once — share it with the operator; reissuing invalidates it.",
        )
    }

    // ── Admin: accounts ─────────────────────────────────────────────────────

    fun accounts(): List<StaffAccount> = synchronized(lock) {
        accounts.sortedWith(compareBy({ it.role }, { it.email })).map { a ->
            StaffAccount(
                id = a.id,
                email = a.email,
                role = a.role,
                operatorId = a.operatorId,
                operatorName = a.operatorId?.let { operatorName(it) },
                hasPassword = a.password != null,
                createdAtEpochMillis = a.createdAt,
                lastLoginAtEpochMillis = a.lastLoginAt,
            )
        }
    }

    fun createAccount(email: String, role: StaffRole, operatorId: Int?, password: String?): AccountCredential =
        synchronized(lock) {
            val normalized = email.trim().lowercase()
            if (!normalized.contains('@')) refuse(400, "email must be a valid address")
            if ((role == StaffRole.PARTNER) != (operatorId != null)) {
                refuse(400, "PARTNER accounts need operatorId; ADMIN accounts must not have one")
            }
            if (operatorId != null && operatorName(operatorId) == null) refuse(404, "No such operator")
            if (password != null && password.length < 8) refuse(400, "password must be at least 8 characters")
            if (accountByEmail(normalized) != null) refuse(409, "An account with that email already exists")
            val generated = password ?: generatePassword()
            accounts += Account(
                id = "acct-" + UUID.randomUUID().toString().take(8),
                email = normalized,
                role = role,
                operatorId = operatorId,
                password = generated,
                createdAt = System.currentTimeMillis(),
                lastLoginAt = null,
            )
            AccountCredential(
                email = normalized,
                role = role,
                operatorId = operatorId,
                generatedPassword = if (password == null) generated else null,
            )
        }

    fun resetPassword(accountId: String, password: String?): AccountCredential = synchronized(lock) {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0) refuse(404, "No such account")
        if (password != null && password.length < 8) refuse(400, "password must be at least 8 characters")
        val generated = password ?: generatePassword()
        accounts[index] = accounts[index].copy(password = generated)
        AccountCredential(email = accounts[index].email, generatedPassword = if (password == null) generated else null)
    }

    fun deleteAccount(accountId: String): Unit = synchronized(lock) {
        if (!accounts.removeAll { it.id == accountId }) refuse(404, "No such account")
    }

    // ── Admin: bookings (support console) ───────────────────────────────────

    fun searchBookings(query: String, status: BookingStatus?, limit: Int): List<SupportBooking> = synchronized(lock) {
        prepare()
        val needle = query.trim().lowercase()
        bookings.values.asSequence()
            // REFUNDED is a rider-facing status; on the support side it is the refund flag.
            .filter {
                status == null || it.status == status ||
                    (status == BookingStatus.REFUNDED && it.refundStatus == RefundStatus.REFUNDED)
            }
            .filter { b ->
                needle.isEmpty() ||
                    b.id.lowercase().contains(needle) ||
                    b.passengerFullName.lowercase().contains(needle) ||
                    b.userId.lowercase().contains(needle) ||
                    b.qrPayload.lowercase().contains(needle)
            }
            .sortedByDescending { it.createdAt }
            .take(limit.coerceIn(1, 200))
            .mapNotNull { toSupportBooking(it) }
            .toList()
    }

    fun booking(bookingId: String): SupportBooking = synchronized(lock) {
        prepare()
        bookings[bookingId]?.let { toSupportBooking(it, includeCoPassengers = true) } ?: refuse(404, "No such booking")
    }

    fun cancelBooking(bookingId: String, reason: String): List<String> = synchronized(lock) {
        prepare()
        if (reason.isBlank()) refuse(400, "a cancellation reason is required")
        val booking = bookings[bookingId] ?: refuse(404, "No such booking")
        if (booking.status == BookingStatus.CANCELLED) refuse(409, "Booking is already cancelled")
        bookings[bookingId] = booking.copy(
            status = BookingStatus.CANCELLED,
            cancelledAt = System.currentTimeMillis(),
            cancelReason = reason.trim(),
        )
        trips[booking.tripId]?.let { freeSeats(it, booking.seatLabels, bookingId) }
        booking.seatLabels
    }

    fun setRefundStatus(bookingId: String, status: RefundStatus, note: String?): RefundStatus = synchronized(lock) {
        prepare()
        val booking = bookings[bookingId] ?: refuse(404, "No such booking")
        bookings[bookingId] = booking.copy(
            refundStatus = status,
            refundNote = note?.takeIf { it.isNotBlank() },
            refundedAt = if (status == RefundStatus.REFUNDED) System.currentTimeMillis() else booking.refundedAt,
        )
        // The rider's own ticket (if this booking came from the rider flow) must read REFUNDED too.
        if (status == RefundStatus.REFUNDED) db.setTicketStatus(bookingId, BookingStatus.REFUNDED)
        status
    }

    fun reissueQr(bookingId: String): String = synchronized(lock) {
        prepare()
        val booking = bookings[bookingId] ?: refuse(404, "No such booking")
        val payload = "RIDEVIBE|v0|$bookingId|${randomHex(12)}"
        bookings[bookingId] = booking.copy(qrPayload = payload)
        payload
    }

    // ── Admin: trips, seats, reference data ─────────────────────────────────

    fun adminTrips(dateIso: String?, operator: String, place: String, limit: Int, offset: Int): List<TripOccupancy> =
        synchronized(lock) {
            prepare()
            val date = dateIso ?: PhTime.todayIso()
            val opNeedle = operator.trim().lowercase()
            val placeNeedle = place.trim().lowercase()
            tripsOn(date)
                .filter { opNeedle.isEmpty() || it.operatorName.lowercase().contains(opNeedle) }
                .filter {
                    placeNeedle.isEmpty() ||
                        it.origin.lowercase().contains(placeNeedle) ||
                        it.destination.lowercase().contains(placeNeedle)
                }
                .drop(offset.coerceAtLeast(0))
                .take(limit.coerceIn(1, 500))
                .map { toOccupancy(it, includeOperator = true) }
        }

    fun adminTripSeats(tripId: String): List<AllocatedSeat> = synchronized(lock) {
        prepare()
        val trip = trips[tripId] ?: refuse(404, "No such trip")
        val now = System.currentTimeMillis()
        seatView(trip).map { s ->
            AllocatedSeat(
                id = s.id,
                label = s.label,
                row = s.row,
                column = s.column,
                status = s.status(now),
                bookingId = s.bookingId,
                heldByUserId = s.heldByUserId?.takeIf { s.status(now) == SeatStatus.LOCKED },
                holdExpiresAtEpochMillis = s.holdExpiresAt?.takeIf { s.status(now) == SeatStatus.LOCKED },
            )
        }
    }

    fun terminals(): List<TerminalLocation> = db.locations().sortedWith(compareBy({ it.region }, { it.name }))

    fun routes(query: String): List<RouteSummary> = synchronized(lock) {
        val needle = query.trim().lowercase()
        routeKeys().mapIndexed { index, key ->
            val own = services.filter { it.origin == key.first && it.destination == key.second }
            RouteSummary(
                id = index + 1,
                origin = key.first,
                destination = key.second,
                mode = own.firstOrNull()?.mode ?: "LAND",
                services = own.size,
            )
        }.filter {
            needle.isEmpty() || it.origin.lowercase().contains(needle) || it.destination.lowercase().contains(needle)
        }
    }

    fun journeys(): List<CuratedJourney> = db.journeySeeds.mapIndexed { index, (keywords, journey) ->
        CuratedJourney(
            id = (index + 1).toString(),
            title = journey.title,
            from = journey.from,
            to = journey.to,
            matchKeywords = keywords,
            legs = journey.legs,
        )
    }

    fun passengerAccounts(): List<PassengerAccount> = synchronized(lock) {
        prepare()
        bookings.values.groupBy { it.userId }.map { (userId, own) ->
            PassengerAccount(
                id = userId,
                createdAtEpochMillis = own.minOf { it.createdAt } - DAY_MS,
                bookings = own.size,
                cancelled = own.count { it.status == BookingStatus.CANCELLED },
            )
        }.sortedByDescending { it.createdAtEpochMillis }
    }

    fun liveHolds(): List<LiveHold> = synchronized(lock) {
        prepare()
        val now = System.currentTimeMillis()
        seats.entries.flatMap { (tripId, list) ->
            val trip = trips[tripId] ?: return@flatMap emptyList()
            list.filter { it.status(now) == SeatStatus.LOCKED }.map { s ->
                LiveHold(
                    seatId = s.id,
                    label = s.label,
                    tripId = tripId,
                    origin = trip.origin,
                    destination = trip.destination,
                    departureEpochMillis = trip.departureEpochMillis,
                    userId = s.heldByUserId ?: "",
                    expiresAtEpochMillis = s.holdExpiresAt ?: now,
                )
            }
        }.sortedBy { it.expiresAtEpochMillis }
    }

    // ── Partner ─────────────────────────────────────────────────────────────

    fun operatorName(operatorId: Int): String? = operators.firstOrNull { it.id == operatorId }?.name

    fun partnerOverview(operatorId: Int, readOnly: Boolean): PartnerOverview = synchronized(lock) {
        prepare()
        val name = operatorName(operatorId) ?: refuse(404, "No such operator")
        val now = System.currentTimeMillis()
        val own = services.filter { it.operatorId == operatorId }
        val upcoming = trips.values.filter { it.operatorId == operatorId && it.departureEpochMillis > now }
        var sold = 0
        var revenue = 0.0
        upcoming.forEach { trip ->
            val soldHere = seatView(trip).count { it.status(now) == SeatStatus.OCCUPIED }
            sold += soldHere
            revenue += soldHere * trip.farePhp
        }
        PartnerOverview(
            operatorId = operatorId,
            operatorName = name,
            readOnly = readOnly,
            services = own.size,
            routes = own.map { it.origin to it.destination }.distinct().size,
            upcomingTrips = upcoming.size,
            departuresNext24h = upcoming.count { it.departureEpochMillis < now + DAY_MS },
            seatsSoldUpcoming = sold,
            upcomingRevenuePhp = revenue,
        )
    }

    fun createService(operatorId: Int, new: NewService): ServiceCreated = synchronized(lock) {
        prepare()
        val origin = new.origin.trim()
        val destination = new.destination.trim()
        if (origin.isEmpty() || destination.isEmpty()) refuse(400, "origin and destination are required")
        if (new.farePhp <= 0.0 || new.farePhp > 100_000.0) refuse(400, "farePhp must be positive")
        val hours = new.departureHours.distinct().sorted()
        if (hours.isEmpty() || hours.any { it !in 0..23 }) refuse(400, "departureHours must be 1–24 unique hours (0–23)")
        if (hours.size != new.departureHours.size) refuse(400, "departureHours must be unique")
        if (new.durationMinutes !in 15..4320) refuse(400, "durationMinutes must be between 15 and 4320")
        val duplicate = services.any {
            it.operatorId == operatorId && it.origin == origin && it.destination == destination &&
                it.busClass == new.busClass && it.rideKind == new.rideKind && it.farePhp == new.farePhp &&
                it.departureHours == hours && it.durationMinutes == new.durationMinutes
        }
        if (duplicate) refuse(409, "An identical service already exists")
        val service = Service(
            id = nextServiceId++,
            operatorId = operatorId,
            origin = origin,
            destination = destination,
            busClass = new.busClass,
            rideKind = new.rideKind,
            farePhp = new.farePhp,
            rating = null,
            departureHours = hours,
            durationMinutes = new.durationMinutes,
        )
        services += service
        val generated = generateTrips(service, PhTime.todayIso(), TRIP_HORIZON_DAYS, seedOccupancy = false)
        ServiceCreated(
            service = service.toDomain(),
            generatedTrips = generated,
            generatedSeats = generated * service.capacity,
        )
    }

    fun updateService(operatorId: Int, serviceId: Int, update: ServiceUpdate): ServiceChanged = synchronized(lock) {
        prepare()
        if (update.isEmpty) refuse(400, "nothing to update")
        val index = services.indexOfFirst { it.id == serviceId && it.operatorId == operatorId }
        if (index < 0) refuse(404, "No such service")
        var service = services[index]
        val now = System.currentTimeMillis()
        var repriced = 0
        var removed = 0

        fun futureUnsold(): List<Trip> = trips.values.filter {
            it.serviceId == serviceId && it.departureEpochMillis > now && soldCount(it) == 0
        }

        update.farePhp?.let { fare ->
            if (fare <= 0.0 || fare > 100_000.0) refuse(400, "farePhp must be positive")
            service = service.copy(farePhp = fare)
            futureUnsold().forEach { trip ->
                trips[trip.id] = trip.copy(farePhp = fare)
                repriced++
            }
        }
        update.durationMinutes?.let { minutes ->
            if (minutes !in 15..4320) refuse(400, "durationMinutes must be between 15 and 4320")
            service = service.copy(durationMinutes = minutes)
            futureUnsold().forEach { trip ->
                trips[trip.id] = trip.copy(arrivalEpochMillis = trip.departureEpochMillis + minutes * 60_000L)
            }
        }
        update.departureHours?.let { raw ->
            val hours = raw.distinct().sorted()
            if (hours.isEmpty() || hours.any { it !in 0..23 }) refuse(400, "departureHours must be 1–24 unique hours (0–23)")
            service = service.copy(departureHours = hours)
            futureUnsold()
                .filter { PhTime.hourOf(it.departureEpochMillis) !in hours && heldCount(it) == 0 }
                .forEach { trip ->
                    trips.remove(trip.id)
                    seats.remove(trip.id)
                    removed++
                }
        }
        services[index] = service
        val added = generateTrips(service, PhTime.todayIso(), TRIP_HORIZON_DAYS, seedOccupancy = false)
        ServiceChanged(service = service.toDomain(), repricedTrips = repriced, removedTrips = removed, addedTrips = added)
    }

    fun addExtraTrip(operatorId: Int, serviceId: Int, dateIso: String, timeHm: String): ExtraTripCreated =
        synchronized(lock) {
            prepare()
            val service = services.firstOrNull { it.id == serviceId && it.operatorId == operatorId }
                ?: refuse(404, "No such service")
            if (!PhTime.isValidIso(dateIso)) refuse(400, "date must be YYYY-MM-DD")
            if (!PhTime.isValidHm(timeHm)) refuse(400, "time must be HH:MM (PH)")
            val (hour, minute) = timeHm.split(":").map { it.toInt() }
            val departure = PhTime.at(dateIso, hour, minute) ?: refuse(400, "invalid date/time")
            if (departure < System.currentTimeMillis()) refuse(400, "departure is in the past")
            val tripId = "rs${serviceId}_${dateIso.replace("-", "")}_${timeHm.replace(":", "")}"
            if (trips.containsKey(tripId) || trips.values.any { it.serviceId == serviceId && it.departureEpochMillis == departure }) {
                refuse(409, "A departure at that time already exists")
            }
            val trip = newTrip(service, tripId, departure)
            trips[tripId] = trip
            seats[tripId] = emptySeatMap(trip)
            ExtraTripCreated(tripId = tripId, seats = trip.capacity)
        }

    fun partnerTrips(operatorId: Int, dateIso: String?): List<TripOccupancy> = synchronized(lock) {
        prepare()
        tripsOn(dateIso ?: PhTime.todayIso())
            .filter { it.operatorId == operatorId }
            .map { toOccupancy(it, includeOperator = false) }
    }

    fun partnerTripSeats(operatorId: Int, tripId: String): List<AllocatedSeat> = synchronized(lock) {
        prepare()
        val trip = trips[tripId]?.takeIf { it.operatorId == operatorId } ?: refuse(404, "No such trip")
        val now = System.currentTimeMillis()
        seatView(trip).map { s ->
            AllocatedSeat(
                id = null,
                label = s.label,
                row = s.row,
                column = s.column,
                status = s.status(now),
                passengerName = s.bookingId?.let { bookings[it]?.passengerFullName },
            )
        }
    }

    fun recordOnsiteSale(operatorId: Int, tripId: String, sale: OnsiteSaleRequest): OnsiteSaleIssued = synchronized(lock) {
        prepare()
        val trip = trips[tripId]?.takeIf { it.operatorId == operatorId } ?: refuse(404, "No such trip")
        val labels = sale.seatLabels?.takeIf { it.isNotEmpty() }
        val count = sale.count
        if ((labels != null) == (count != null)) refuse(400, "send either seatLabels or count")
        val now = System.currentTimeMillis()
        val view = seatView(trip)
        val picked: List<Seat> = if (labels != null) {
            if (labels.size > 20) refuse(400, "at most 20 seats per sale")
            val found = view.filter { it.label in labels }
            if (found.size != labels.size) refuse(409, "One or more seats do not exist on this trip")
            if (found.any { it.status(now) == SeatStatus.OCCUPIED }) refuse(409, "One or more seats are already sold")
            if (found.any { it.status(now) == SeatStatus.LOCKED }) refuse(409, "One or more seats are being bought in the app right now")
            found
        } else {
            val wanted = count!!
            if (wanted !in 1..20) refuse(400, "count must be between 1 and 20")
            val free = view.filter { it.status(now) == SeatStatus.AVAILABLE }.sortedWith(compareBy({ it.row }, { it.column }))
            if (free.size < wanted) refuse(409, "Only ${free.size} seat(s) left on this departure")
            free.take(wanted)
        }
        val ticketId = "tkt_${UUID.randomUUID()}"
        val seatLabels = picked.map { it.label }
        bookings[ticketId] = Booking(
            id = ticketId,
            tripId = tripId,
            userId = "onsite:$operatorId",
            passengerFullName = sale.passengerFullName.trim().ifEmpty { "Walk-in passenger" },
            passengerType = PassengerType.REGULAR,
            infantCount = 0,
            paymentMethod = PaymentMethod.CASH_ON_BOARD,
            paymentStatus = PaymentStatus.PAID, // paid at the counter
            qrPayload = "RIDEVIBE|v0|$ticketId",
            promoCode = null,
            status = BookingStatus.CONFIRMED,
            refundStatus = RefundStatus.NONE,
            refundNote = null,
            cancelReason = null,
            seatLabels = seatLabels,
            createdAt = now,
            cancelledAt = null,
            refundedAt = null,
            coPassengers = emptyList(),
        )
        occupySeats(trip, seatLabels, ticketId)
        OnsiteSaleIssued(ticketId = ticketId, seatLabels = seatLabels)
    }

    fun manifest(operatorId: Int, dateIso: String?): List<ManifestEntry> = synchronized(lock) {
        prepare()
        val dayTrips = tripsOn(dateIso ?: PhTime.todayIso()).filter { it.operatorId == operatorId }.associateBy { it.id }
        bookings.values
            .filter { it.tripId in dayTrips }
            .sortedWith(compareBy({ dayTrips.getValue(it.tripId).departureEpochMillis }, { it.createdAt }))
            .map { b ->
                val trip = dayTrips.getValue(b.tripId)
                ManifestEntry(
                    id = b.id,
                    passengerFullName = b.passengerFullName,
                    passengerType = b.passengerType,
                    partySize = b.partySize,
                    infantCount = b.infantCount,
                    paymentMethod = b.paymentMethod,
                    paymentStatus = b.paymentStatus,
                    status = b.status,
                    tripId = b.tripId,
                    departureEpochMillis = trip.departureEpochMillis,
                    origin = trip.origin,
                    destination = trip.destination,
                    seatLabels = b.seatLabels,
                )
            }
    }

    // ── Internals: world building ───────────────────────────────────────────

    /** Idempotent: horizon trips, today's occupancy, support fixtures, rider tickets. Call under [lock]. */
    private fun prepare() {
        val today = PhTime.todayIso()
        repeat(TRIP_HORIZON_DAYS) { offset -> ensureDate(PhTime.plusDays(today, offset)) }
        if (!seeded) {
            seeded = true
            tripsOn(today).forEach { seatView(it) } // materialise today's occupancy so the console has data
            seedSupportCases()
        }
        syncPassengerTickets()
    }

    private fun ensureDate(dateIso: String) {
        if (!materializedDates.add(dateIso)) return
        services.forEach { generateTrips(it, dateIso, 1, seedOccupancy = true) }
    }

    /** Adds missing trips for [service] over [days] from [startIso]; returns how many were new. */
    private fun generateTrips(service: Service, startIso: String, days: Int, seedOccupancy: Boolean): Int {
        var added = 0
        repeat(days) { offset ->
            val date = PhTime.plusDays(startIso, offset)
            materializedDates += date
            service.departureHours.forEach { hour ->
                val departure = PhTime.at(date, hour) ?: return@forEach
                val id = "rs${service.id}_${date.replace("-", "")}_${String.format(Locale.US, "%02d00", hour)}"
                if (trips.containsKey(id)) return@forEach
                trips[id] = newTrip(service, id, departure)
                if (!seedOccupancy) seats[id] = emptySeatMap(trips.getValue(id))
                added++
            }
        }
        return added
    }

    private fun newTrip(service: Service, id: String, departure: Long) = Trip(
        id = id,
        serviceId = service.id,
        operatorId = service.operatorId,
        operatorName = operatorName(service.operatorId) ?: "",
        origin = service.origin,
        destination = service.destination,
        busClass = service.busClass,
        rideKind = service.rideKind,
        departureEpochMillis = departure,
        arrivalEpochMillis = departure + service.durationMinutes * 60_000L,
        farePhp = service.farePhp,
        capacity = service.capacity,
    )

    private fun tripsOn(dateIso: String): List<Trip> {
        val range = PhTime.dayRange(dateIso) ?: refuse(400, "date must be YYYY-MM-DD")
        ensureDate(dateIso)
        return trips.values.filter { it.departureEpochMillis in range }.sortedWith(compareBy({ it.departureEpochMillis }, { it.id }))
    }

    private fun emptySeatMap(trip: Trip): MutableList<Seat> {
        val letters = if (trip.capacity == 30) listOf("A", "B", "C") else listOf("A", "B", "C", "D")
        val rows = trip.capacity / letters.size
        return (1..rows).flatMap { row ->
            letters.mapIndexed { index, letter ->
                val label = "$row$letter"
                Seat(id = "${trip.id}:$label", label = label, row = row, column = index + 1)
            }
        }.toMutableList()
    }

    /**
     * The trip's seat list. Rider-side trips ([Trip.isExternal]) are projected
     * from [MockDatabase] on every call so both consoles see the same state.
     * Staff trips get a deterministic seeded occupancy the first time.
     */
    private fun seatView(trip: Trip): List<Seat> {
        if (trip.isExternal) {
            val byLabel = bookings.values
                .filter { it.tripId == trip.id && it.status == BookingStatus.CONFIRMED }
                .flatMap { b -> b.seatLabels.map { it to b.id } }
                .toMap()
            return db.seatMap(trip.id).map { s ->
                Seat(
                    id = "${trip.id}:${s.label}",
                    label = s.label,
                    row = s.row,
                    column = s.column,
                    bookingId = if (s.status == SeatStatus.OCCUPIED) byLabel[s.label] ?: "ext-${s.label}" else null,
                    heldByUserId = if (s.status == SeatStatus.LOCKED || s.status == SeatStatus.SELECTED) s.lockedByUserId ?: "rider" else null,
                    holdExpiresAt = if (s.status == SeatStatus.LOCKED || s.status == SeatStatus.SELECTED) System.currentTimeMillis() + 5 * 60_000L else null,
                )
            }
        }
        return seats.getOrPut(trip.id) { seededSeatMap(trip) }
    }

    private fun seededSeatMap(trip: Trip): MutableList<Seat> {
        val list = emptySeatMap(trip)
        val rnd = Random(trip.id.hashCode())
        val now = System.currentTimeMillis()
        val daysOut = ((trip.departureEpochMillis - now) / DAY_MS).toInt()
        val fraction = when {
            trip.departureEpochMillis < now -> 0.55f + rnd.nextFloat() * 0.35f
            daysOut == 0 -> 0.35f + rnd.nextFloat() * 0.35f
            daysOut < 4 -> 0.15f + rnd.nextFloat() * 0.30f
            else -> rnd.nextFloat() * 0.20f
        }
        var toSell = (trip.capacity * fraction).toInt()
        var index = 0
        while (toSell > 0 && index < list.size) {
            if (rnd.nextFloat() < 0.25f) { index++; continue } // leave gaps like real sales
            val party = minOf(1 + rnd.nextInt(3), toSell, list.size - index)
            val picked = list.subList(index, index + party)
            val name = passengerNames[rnd.nextInt(passengerNames.size)]
            val id = "tkt_" + UUID.nameUUIDFromBytes("${trip.id}:${picked.first().label}".toByteArray())
            val type = when (rnd.nextInt(10)) { 0 -> PassengerType.STUDENT; 1 -> PassengerType.SENIOR_CITIZEN; 2 -> PassengerType.PWD; else -> PassengerType.REGULAR }
            val method = PaymentMethod.values()[rnd.nextInt(PaymentMethod.values().size)]
            val coPassengers = picked.drop(1).map { CoPassenger(firstName = passengerNames[rnd.nextInt(passengerNames.size)].substringBefore(' '), lastName = name.substringAfter(' ')) }
            val createdAt = minOf(now - rnd.nextLong(3_600_000L, 5 * DAY_MS), trip.departureEpochMillis - 3_600_000L)
            bookings[id] = Booking(
                id = id,
                tripId = trip.id,
                userId = passengerUserIds[rnd.nextInt(passengerUserIds.size)],
                passengerFullName = name,
                passengerType = type,
                infantCount = if (rnd.nextInt(8) == 0) 1 else 0,
                paymentMethod = method,
                paymentStatus = method.paymentStatus,
                qrPayload = "RIDEVIBE|v0|$id",
                promoCode = if (rnd.nextInt(6) == 0) "VIBE10" else null,
                status = BookingStatus.CONFIRMED,
                refundStatus = RefundStatus.NONE,
                refundNote = null,
                cancelReason = null,
                seatLabels = picked.map { it.label },
                createdAt = createdAt,
                cancelledAt = null,
                refundedAt = null,
                coPassengers = coPassengers,
            )
            for (i in index until index + party) list[i] = list[i].copy(bookingId = id)
            index += party
            toSell -= party
        }
        if (trip.departureEpochMillis > now) {
            val free = list.withIndex().filter { it.value.bookingId == null }
            repeat(rnd.nextInt(3)) {
                val target = free.randomOrNull(rnd) ?: return@repeat
                list[target.index] = list[target.index].copy(
                    heldByUserId = passengerUserIds[rnd.nextInt(passengerUserIds.size)],
                    holdExpiresAt = now + (3 + rnd.nextInt(10)) * 60_000L,
                )
            }
        }
        return list
    }

    /** A cancelled booking and two refund requests so the support console has every state on day one. */
    private fun seedSupportCases() {
        val candidates = bookings.values.filter { !trips.getValue(it.tripId).isExternal }.sortedBy { it.id }
        candidates.getOrNull(0)?.let { b ->
            bookings[b.id] = b.copy(
                status = BookingStatus.CANCELLED,
                cancelledAt = System.currentTimeMillis() - 2 * 3_600_000L,
                cancelReason = "Passenger request — schedule change",
                refundStatus = RefundStatus.REQUESTED,
                refundNote = "GCash refund requested by passenger",
            )
            trips[b.tripId]?.let { freeSeats(it, b.seatLabels, b.id) }
        }
        candidates.getOrNull(3)?.let { b ->
            bookings[b.id] = b.copy(refundStatus = RefundStatus.REQUESTED, refundNote = "Charged twice, see support chat")
        }
        candidates.getOrNull(5)?.let { b ->
            bookings[b.id] = b.copy(
                refundStatus = RefundStatus.REFUNDED,
                refundNote = "Refunded to card",
                refundedAt = System.currentTimeMillis() - DAY_MS,
            )
        }
    }

    /**
     * Tickets booked in the rider flow appear as support bookings (user "me"),
     * and a rider ticket that lapsed (unpaid cash reservation) is reflected
     * here as a cancellation so the manifest never lists a dead booking.
     */
    private fun syncPassengerTickets() {
        db.allTickets().forEach { ticket ->
            bookings[ticket.id]?.let { existing ->
                if (existing.status == BookingStatus.CONFIRMED && ticket.status == BookingStatus.CANCELLED) {
                    bookings[ticket.id] = existing.copy(
                        status = BookingStatus.CANCELLED,
                        cancelledAt = System.currentTimeMillis(),
                        cancelReason = "Cash-on-board reservation lapsed unpaid",
                    )
                }
                return@forEach
            }
            val t = ticket.trip
            trips.getOrPut(t.id) {
                Trip(
                    id = t.id,
                    serviceId = null,
                    operatorId = operators.firstOrNull { it.name == t.operatorName }?.id ?: 0,
                    operatorName = t.operatorName,
                    origin = t.origin,
                    destination = t.destination,
                    busClass = t.busClass,
                    rideKind = t.rideKind,
                    departureEpochMillis = t.departureEpochMillis,
                    arrivalEpochMillis = t.arrivalEpochMillis,
                    farePhp = t.farePhp,
                    capacity = if (t.busClass == BusClass.LUXURY) 30 else 44, // same rule as Service.capacity
                )
            }
            bookings[ticket.id] = Booking(
                id = ticket.id,
                tripId = t.id,
                userId = MOCK_CURRENT_USER_ID,
                passengerFullName = ticket.primaryPassenger.fullName,
                passengerType = ticket.primaryPassenger.type,
                infantCount = ticket.infantCount,
                paymentMethod = PaymentMethod.values().firstOrNull { it.paymentStatus == ticket.paymentStatus } ?: PaymentMethod.CASH_ON_BOARD,
                paymentStatus = ticket.paymentStatus,
                qrPayload = ticket.qrPayload,
                promoCode = null,
                status = BookingStatus.CONFIRMED,
                refundStatus = RefundStatus.NONE,
                refundNote = null,
                cancelReason = null,
                seatLabels = ticket.seatLabels,
                createdAt = minOf(System.currentTimeMillis(), t.departureEpochMillis - 2 * DAY_MS),
                cancelledAt = null,
                refundedAt = null,
                coPassengers = ticket.coPassengers,
            ).let { booking ->
                // A rider ticket already cancelled/refunded arrives in that state.
                when (ticket.status) {
                    BookingStatus.CONFIRMED -> booking
                    BookingStatus.CANCELLED -> booking.copy(status = BookingStatus.CANCELLED, cancelledAt = System.currentTimeMillis())
                    BookingStatus.REFUNDED -> booking.copy(
                        status = BookingStatus.CANCELLED,
                        refundStatus = RefundStatus.REFUNDED,
                        cancelledAt = System.currentTimeMillis(),
                        refundedAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    private fun freeSeats(trip: Trip, labels: List<String>, bookingId: String) {
        if (trip.isExternal) {
            // Rider-side ticket: cancelling it there frees its seats (or sea spaces) and updates its status.
            if (!db.setTicketStatus(bookingId, BookingStatus.CANCELLED)) {
                labels.forEach { db.updateSeat(trip.id, it, SeatStatus.AVAILABLE, lockedBy = null) }
            }
            return
        }
        val list = seats[trip.id] ?: return
        for (i in list.indices) {
            if (list[i].bookingId == bookingId || (list[i].label in labels && list[i].bookingId == null)) {
                list[i] = list[i].copy(bookingId = null)
            }
        }
    }

    private fun occupySeats(trip: Trip, labels: List<String>, bookingId: String) {
        if (trip.isExternal) {
            labels.forEach { db.updateSeat(trip.id, it, SeatStatus.OCCUPIED, lockedBy = null) }
            return
        }
        val list = seats.getOrPut(trip.id) { seededSeatMap(trip) }
        for (i in list.indices) {
            if (list[i].label in labels) list[i] = list[i].copy(bookingId = bookingId, heldByUserId = null, holdExpiresAt = null)
        }
    }

    private fun soldCount(trip: Trip): Int {
        val now = System.currentTimeMillis()
        return (seats[trip.id] ?: return 0).count { it.status(now) == SeatStatus.OCCUPIED }
    }

    private fun heldCount(trip: Trip): Int {
        val now = System.currentTimeMillis()
        return (seats[trip.id] ?: return 0).count { it.status(now) == SeatStatus.LOCKED }
    }

    private fun toOccupancy(trip: Trip, includeOperator: Boolean): TripOccupancy {
        val now = System.currentTimeMillis()
        val view = seatView(trip)
        val sold = view.count { it.status(now) == SeatStatus.OCCUPIED }
        val held = view.count { it.status(now) == SeatStatus.LOCKED }
        return TripOccupancy(
            id = trip.id,
            origin = trip.origin,
            destination = trip.destination,
            operatorName = if (includeOperator) trip.operatorName else null,
            busClass = trip.busClass,
            rideKind = trip.rideKind,
            departureEpochMillis = trip.departureEpochMillis,
            arrivalEpochMillis = trip.arrivalEpochMillis,
            farePhp = trip.farePhp,
            seats = view.size,
            sold = sold,
            held = held,
            available = view.size - sold - held,
        )
    }

    private fun toSupportBooking(b: Booking, includeCoPassengers: Boolean = false): SupportBooking? {
        val trip = trips[b.tripId] ?: return null
        return SupportBooking(
            id = b.id,
            status = b.status,
            refundStatus = b.refundStatus,
            refundNote = b.refundNote,
            cancelReason = b.cancelReason,
            passengerFullName = b.passengerFullName,
            passengerType = b.passengerType,
            infantCount = b.infantCount,
            paymentMethod = b.paymentMethod,
            paymentStatus = b.paymentStatus,
            qrPayload = b.qrPayload,
            promoCode = b.promoCode,
            userId = b.userId,
            tripId = b.tripId,
            departureEpochMillis = trip.departureEpochMillis,
            farePhp = trip.farePhp,
            origin = trip.origin,
            destination = trip.destination,
            operatorName = trip.operatorName,
            busClass = trip.busClass,
            rideKind = trip.rideKind,
            seatLabels = b.seatLabels,
            createdAtEpochMillis = b.createdAt,
            cancelledAtEpochMillis = b.cancelledAt,
            refundedAtEpochMillis = b.refundedAt,
            coPassengers = if (includeCoPassengers) b.coPassengers else emptyList(),
        )
    }

    private fun routeKeys(): List<Pair<String, String>> =
        services.map { it.origin to it.destination }.distinct().sortedWith(compareBy({ it.first }, { it.second }))

    private fun Service.toDomain() = OperatorService(
        id = id,
        origin = origin,
        destination = destination,
        mode = mode,
        busClass = busClass,
        rideKind = rideKind,
        farePhp = farePhp,
        rating = rating,
        departureHours = departureHours,
        durationMinutes = durationMinutes,
        seatLayout = seatLayout,
    )

    private fun randomHex(length: Int): String =
        buildString { while (this.length < length) append(UUID.randomUUID().toString().replace("-", "")) }.take(length)

    /** 12 URL-safe chars, like the server's generatePassword(). */
    private fun generatePassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789-_"
        return (1..12).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    private companion object {
        fun daysAgo(days: Int): Long = System.currentTimeMillis() - days * DAY_MS
    }
}

// ── Repositories ──────────────────────────────────────────────────────────────

@Singleton
class MockStaffAuthRepository @Inject constructor(
    private val staffDb: MockStaffDatabase,
    private val sessionStore: StaffSessionStore,
) : StaffAuthRepository {

    override val session: StateFlow<StaffSession?> get() = sessionStore.session

    override suspend fun getAuthOptions(): Result<StaffAuthOptions> =
        mockCall { StaffAuthOptions(googleEnabled = false, emailKeyEnabled = true) }

    override suspend fun signInWithPassword(email: String, password: String): Result<StaffSession> =
        mockCall { staffDb.authenticate(email, password).also(sessionStore::save) }

    override suspend fun requestEmailCode(email: String): Result<EmailCodeRequested> =
        mockCall { staffDb.requestEmailCode(email) }

    override suspend fun signInWithEmailCode(email: String, code: String): Result<StaffSession> =
        mockCall { staffDb.verifyEmailCode(email, code).also(sessionStore::save) }

    override suspend fun signInWithGoogle(idToken: String): Result<StaffSession> =
        mockCall { refuse(503, "Google sign-in is not configured (GOOGLE_CLIENT_ID)") }

    /** The persisted mock session is valid until sign-out or its 7-day lifetime passes. */
    override suspend fun refreshSession(): Result<StaffSession?> {
        if (sessionStore.isExpired()) sessionStore.clear()
        return Result.success(sessionStore.session.value)
    }

    override suspend fun signOut() = withContext(Dispatchers.Default) {
        delay(STAFF_LATENCY_MS / 2)
        sessionStore.clear()
    }
}

@Singleton
class MockAdminRepository @Inject constructor(
    private val staffDb: MockStaffDatabase,
    private val sessionStore: StaffSessionStore,
) : AdminRepository {

    private fun requireAdmin() {
        val session = sessionStore.session.value
        if (session == null || !session.isAdmin) refuse(401, "Admin sign-in required")
    }

    private suspend fun <T> admin(block: () -> T): Result<T> = mockCall { requireAdmin(); block() }

    override suspend fun getOverview(): Result<AdminOverview> = admin { staffDb.adminOverview() }

    override suspend fun getOperators(): Result<List<OperatorSummary>> = admin { staffDb.operatorSummaries() }

    override suspend fun getOperatorServices(operatorId: Int): Result<List<OperatorService>> =
        admin { staffDb.operatorServices(operatorId) }

    override suspend fun issuePartnerToken(operatorId: Int): Result<PartnerTokenIssued> =
        admin { staffDb.issuePartnerToken(operatorId) }

    override suspend fun getAccounts(): Result<List<StaffAccount>> = admin { staffDb.accounts() }

    override suspend fun createAccount(
        email: String,
        role: StaffRole,
        operatorId: Int?,
        password: String?,
    ): Result<AccountCredential> = admin { staffDb.createAccount(email, role, operatorId, password?.takeIf { it.isNotBlank() }) }

    override suspend fun resetPassword(accountId: String, password: String?): Result<AccountCredential> =
        admin { staffDb.resetPassword(accountId, password?.takeIf { it.isNotBlank() }) }

    override suspend fun deleteAccount(accountId: String): Result<Unit> = admin { staffDb.deleteAccount(accountId) }

    override suspend fun searchBookings(query: String, status: BookingStatus?, limit: Int): Result<List<SupportBooking>> =
        admin { staffDb.searchBookings(query, status, limit) }

    override suspend fun getBooking(bookingId: String): Result<SupportBooking> = admin { staffDb.booking(bookingId) }

    override suspend fun cancelBooking(bookingId: String, reason: String): Result<List<String>> =
        admin { staffDb.cancelBooking(bookingId, reason) }

    override suspend fun setRefundStatus(bookingId: String, status: RefundStatus, note: String?): Result<RefundStatus> =
        admin { staffDb.setRefundStatus(bookingId, status, note) }

    override suspend fun reissueQr(bookingId: String): Result<String> = admin { staffDb.reissueQr(bookingId) }

    override suspend fun getTrips(
        dateIso: String?,
        operator: String,
        place: String,
        limit: Int,
        offset: Int,
    ): Result<List<TripOccupancy>> = admin { staffDb.adminTrips(dateIso?.takeIf { it.isNotBlank() }, operator, place, limit, offset) }

    override suspend fun getTripSeats(tripId: String): Result<List<AllocatedSeat>> = admin { staffDb.adminTripSeats(tripId) }

    override suspend fun getTerminals(): Result<List<TerminalLocation>> = admin { staffDb.terminals() }

    override suspend fun getRoutes(query: String): Result<List<RouteSummary>> = admin { staffDb.routes(query) }

    override suspend fun getJourneys(): Result<List<CuratedJourney>> = admin { staffDb.journeys() }

    override suspend fun getPassengerAccounts(): Result<List<PassengerAccount>> = admin { staffDb.passengerAccounts() }

    override suspend fun getLiveHolds(): Result<List<LiveHold>> = admin { staffDb.liveHolds() }
}

@Singleton
class MockPartnerRepository @Inject constructor(
    private val staffDb: MockStaffDatabase,
    private val sessionStore: StaffSessionStore,
) : PartnerRepository {

    private class Access(val operatorId: Int, val readOnly: Boolean)

    /** Same access model as routes/partner.ts: partners act for themselves; admins read any operator. */
    private fun resolve(operatorId: Int?, write: Boolean): Access {
        val session = sessionStore.session.value ?: refuse(401, "Partner token required (X-Partner-Token header)")
        return if (session.isAdmin) {
            if (write) refuse(403, "Admin access to the partner dashboard is read-only")
            val id = operatorId ?: refuse(400, "operatorId query parameter required for admin read-only view")
            if (staffDb.operatorName(id) == null) refuse(404, "No such operator")
            Access(id, readOnly = true)
        } else {
            val own = session.operatorId ?: refuse(401, "Partner sign-in required")
            Access(own, readOnly = false)
        }
    }

    private suspend fun <T> read(operatorId: Int?, block: (Access) -> T): Result<T> =
        mockCall { block(resolve(operatorId, write = false)) }

    private suspend fun <T> write(block: (Access) -> T): Result<T> =
        mockCall { block(resolve(operatorId = null, write = true)) }

    override suspend fun getOverview(operatorId: Int?): Result<PartnerOverview> =
        read(operatorId) { staffDb.partnerOverview(it.operatorId, it.readOnly) }

    override suspend fun getServices(operatorId: Int?): Result<List<OperatorService>> =
        read(operatorId) { staffDb.operatorServices(it.operatorId) }

    override suspend fun createService(service: NewService): Result<ServiceCreated> =
        write { staffDb.createService(it.operatorId, service) }

    override suspend fun updateService(serviceId: Int, update: ServiceUpdate): Result<ServiceChanged> =
        write { staffDb.updateService(it.operatorId, serviceId, update) }

    override suspend fun addExtraTrip(serviceId: Int, dateIso: String, timeHm: String): Result<ExtraTripCreated> =
        write { staffDb.addExtraTrip(it.operatorId, serviceId, dateIso.trim(), timeHm.trim()) }

    override suspend fun getTrips(dateIso: String?, operatorId: Int?): Result<List<TripOccupancy>> =
        read(operatorId) { staffDb.partnerTrips(it.operatorId, dateIso?.takeIf { d -> d.isNotBlank() }) }

    override suspend fun getTripSeats(tripId: String, operatorId: Int?): Result<List<AllocatedSeat>> =
        read(operatorId) { staffDb.partnerTripSeats(it.operatorId, tripId) }

    override suspend fun recordOnsiteSale(tripId: String, sale: OnsiteSaleRequest): Result<OnsiteSaleIssued> =
        write { staffDb.recordOnsiteSale(it.operatorId, tripId, sale) }

    override suspend fun getManifest(dateIso: String?, operatorId: Int?): Result<List<ManifestEntry>> =
        read(operatorId) { staffDb.manifest(it.operatorId, dateIso?.takeIf { d -> d.isNotBlank() }) }
}
