package com.ridevibe.core.domain.model

/**
 * Staff-side domain: what the CRS admin and partner surfaces (`/auth`,
 * `/admin/api`, `/partner/api`) expose. Consolidates the two web dashboards
 * into the app as role-gated screens. Passenger identity (X-Device-Id) is a
 * separate system; a staff session never changes who the passenger is.
 */

enum class StaffRole { ADMIN, PARTNER }

/** A signed-in dashboard account. [token] is the `s_…` session credential. */
data class StaffSession(
    val token: String,
    val email: String,
    val role: StaffRole,
    val operatorId: Int? = null,
    val operatorName: String? = null,
    /**
     * When the server will stop honouring [token] (sign-in time + `expiresInDays`).
     * Null when the server did not say; the store then defers to `/auth/me`.
     */
    val expiresAtEpochMillis: Long? = null,
) {
    val isAdmin: Boolean get() = role == StaffRole.ADMIN

    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMillis?.let { it <= nowEpochMillis } ?: false
}

/** What the sign-in screen may offer (`GET /auth/config`). */
data class StaffAuthOptions(
    val googleEnabled: Boolean,
    val emailKeyEnabled: Boolean,
    /** Web-client audience for the Google ID token; null when Google sign-in is off. */
    val googleClientId: String? = null,
)

/** `POST /auth/email-code` result. [devCode] is only ever present outside production. */
data class EmailCodeRequested(
    val message: String,
    val devCode: String? = null,
)

// ── Admin ────────────────────────────────────────────────────────────────────

data class DayCount(val day: String, val count: Int)

data class AdminOverview(
    val operators: Int,
    val terminals: Int,
    val routes: Int,
    val services: Int,
    val journeys: Int,
    val upcomingTrips: Int,
    val seats: Int,
    val activeHolds: Int,
    val users: Int,
    val bookings: Int,
    val cancelledBookings: Int,
    val refundsPending: Int,
    val bookingsByDay: List<DayCount>,
)

data class OperatorSummary(
    val id: Int,
    val name: String,
    val services: Int,
    val routes: Int,
    val busClasses: List<String>,
    val rideKinds: List<String>,
    val avgRating: Double?,
    val minFarePhp: Double?,
    val maxFarePhp: Double?,
)

/** One route service an operator runs, with the seat layout its class allocates. */
data class OperatorService(
    val id: Int,
    val origin: String,
    val destination: String,
    val mode: String,
    val busClass: BusClass,
    val rideKind: RideKind,
    val farePhp: Double,
    val rating: Double?,
    val departureHours: List<Int>,
    val durationMinutes: Int,
    val seatLayout: String,
)

data class StaffAccount(
    val id: String,
    val email: String,
    val role: StaffRole,
    val operatorId: Int?,
    val operatorName: String?,
    val hasPassword: Boolean,
    val createdAtEpochMillis: Long,
    val lastLoginAtEpochMillis: Long?,
)

/** Result of creating an account or resetting a password; [generatedPassword] is shown once. */
data class AccountCredential(
    val email: String,
    val role: StaffRole? = null,
    val operatorId: Int? = null,
    val generatedPassword: String? = null,
    /** After a password reset: other sessions of the account the server revoked (null when not a reset). */
    val revokedSessions: Int? = null,
)

data class PartnerTokenIssued(
    val operatorId: Int,
    val operatorName: String,
    val token: String,
    val note: String,
)

enum class RefundStatus { NONE, REQUESTED, REFUNDED }

/** A booking as the support console sees it (`/admin/api/bookings`). */
data class SupportBooking(
    val id: String,
    val status: BookingStatus,
    val refundStatus: RefundStatus,
    val refundNote: String?,
    val cancelReason: String?,
    val passengerFullName: String,
    val passengerType: PassengerType,
    val infantCount: Int,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus,
    val qrPayload: String,
    val promoCode: String?,
    val userId: String,
    val tripId: String,
    val departureEpochMillis: Long,
    val farePhp: Double,
    val origin: String,
    val destination: String,
    val operatorName: String,
    val busClass: BusClass,
    val rideKind: RideKind,
    val seatLabels: List<String>,
    val createdAtEpochMillis: Long,
    val cancelledAtEpochMillis: Long?,
    val refundedAtEpochMillis: Long?,
    /** Only populated by the single-booking detail call. */
    val coPassengers: List<CoPassenger> = emptyList(),
)

/** A departure with its sold / held / available counts. [operatorName] is null on the partner surface. */
data class TripOccupancy(
    val id: String,
    val origin: String,
    val destination: String,
    val operatorName: String?,
    val busClass: BusClass,
    val rideKind: RideKind,
    val departureEpochMillis: Long,
    val arrivalEpochMillis: Long,
    val farePhp: Double,
    val seats: Int,
    val sold: Int,
    val held: Int,
    val available: Int,
)

/**
 * A seat in a staff seat-allocation view. Admin sees holder/buyer ids and hold
 * expiry; partner sees the passenger name on sold seats. Unused fields are null.
 */
data class AllocatedSeat(
    val id: String?,
    val label: String,
    val row: Int,
    val column: Int,
    val status: SeatStatus,
    val bookingId: String? = null,
    val heldByUserId: String? = null,
    val holdExpiresAtEpochMillis: Long? = null,
    val passengerName: String? = null,
)

data class RouteSummary(
    val id: Int,
    val origin: String,
    val destination: String,
    val mode: String,
    val services: Int,
)

data class CuratedJourney(
    val id: String,
    val title: String,
    val from: String,
    val to: String,
    val matchKeywords: List<String>,
    val legs: List<JourneyLeg>,
)

data class PassengerAccount(
    val id: String,
    val createdAtEpochMillis: Long,
    val bookings: Int,
    val cancelled: Int,
)

data class LiveHold(
    val seatId: String,
    val label: String,
    val tripId: String,
    val origin: String,
    val destination: String,
    val departureEpochMillis: Long,
    val userId: String,
    val expiresAtEpochMillis: Long,
)

// ── Partner ──────────────────────────────────────────────────────────────────

data class PartnerOverview(
    val operatorId: Int,
    val operatorName: String,
    /** True when an admin is viewing this operator; every write is refused. */
    val readOnly: Boolean,
    val services: Int,
    val routes: Int,
    val upcomingTrips: Int,
    val departuresNext24h: Int,
    val seatsSoldUpcoming: Int,
    val upcomingRevenuePhp: Double,
)

data class NewService(
    val origin: String,
    val destination: String,
    val busClass: BusClass,
    val rideKind: RideKind,
    val farePhp: Double,
    val departureHours: List<Int>,
    val durationMinutes: Int,
)

/** Partial update; null fields are left unchanged. At least one must be set. */
data class ServiceUpdate(
    val farePhp: Double? = null,
    val departureHours: List<Int>? = null,
    val durationMinutes: Int? = null,
) {
    val isEmpty: Boolean get() = farePhp == null && departureHours == null && durationMinutes == null
}

data class ServiceCreated(
    val service: OperatorService,
    val generatedTrips: Int,
    val generatedSeats: Int,
)

data class ServiceChanged(
    val service: OperatorService,
    val repricedTrips: Int,
    val removedTrips: Int,
    val addedTrips: Int,
)

data class ExtraTripCreated(val tripId: String, val seats: Int)

/** Walk-up counter sale: exact [seatLabels] on a bus, or a [count] on open seating. */
data class OnsiteSaleRequest(
    val seatLabels: List<String>? = null,
    val count: Int? = null,
    val passengerFullName: String = "Walk-in passenger",
)

data class OnsiteSaleIssued(val ticketId: String, val seatLabels: List<String>)

data class ManifestEntry(
    val id: String,
    val passengerFullName: String,
    val passengerType: PassengerType,
    val partySize: Int,
    val infantCount: Int,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus,
    val status: BookingStatus,
    val tripId: String,
    val departureEpochMillis: Long,
    val origin: String,
    val destination: String,
    val seatLabels: List<String>,
)
