package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.AccountCredential
import com.ridevibe.core.domain.model.AdminOverview
import com.ridevibe.core.domain.model.AllocatedSeat
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
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.core.domain.model.RouteSummary
import com.ridevibe.core.domain.model.ServiceChanged
import com.ridevibe.core.domain.model.ServiceCreated
import com.ridevibe.core.domain.model.ServiceUpdate
import com.ridevibe.core.domain.model.StaffAccount
import com.ridevibe.core.domain.model.StaffAuthOptions
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.core.domain.model.SupportBooking
import com.ridevibe.core.domain.model.TripOccupancy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/*
 * DTOs for the staff surfaces: /auth/…, /admin/api/…, /partner/api/…
 * (backend: src/routes/auth.ts, admin.ts, partner.ts).
 *
 * Enum parsing is LENIENT throughout (EnumParsing.kt) — a staff view must
 * never crash because the server grew a new value. Unknown strings fall back
 * to the safest default and are logged.
 * Request DTOs rely on kotlinx.serialization's `encodeDefaults = false`: a
 * nullable field left at its `null` default is OMITTED, which is what the
 * server's zod `.optional()` schemas expect (an explicit `null` would be a 400).
 */

// Lenient enum parsing (shared with the passenger DTOs) lives in EnumParsing.kt.

private const val DAY_MILLIS = 86_400_000L

// ── /auth ─────────────────────────────────────────────────────────────────────

@Serializable
data class StaffAuthConfigDto(
    val googleEnabled: Boolean = false,
    val googleClientId: String? = null,
    val emailKeyEnabled: Boolean = false,
) {
    fun toDomain() = StaffAuthOptions(
        googleEnabled = googleEnabled,
        emailKeyEnabled = emailKeyEnabled,
        googleClientId = googleClientId?.takeIf { it.isNotBlank() },
    )
}

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class EmailCodeRequestDto(val email: String)

@Serializable
data class EmailCodeVerifyRequestDto(val email: String, val code: String)

/** `credential` is the Google ID token (web-client audience). */
@Serializable
data class GoogleSignInRequestDto(val credential: String)

/** Returned by /auth/login, /auth/email-code/verify and /auth/google. */
@Serializable
data class StaffSessionDto(
    val token: String,
    val email: String,
    val role: String,
    val operatorId: Int? = null,
    val operatorName: String? = null,
    val expiresInDays: Int? = null,
) {
    /** The server states a lifetime, not an instant: the expiry is anchored to sign-in time here. */
    fun toDomain(nowEpochMillis: Long = System.currentTimeMillis()) = StaffSession(
        token = token,
        email = email,
        role = role.toStaffRole(),
        operatorId = operatorId,
        operatorName = operatorName,
        expiresAtEpochMillis = expiresInDays?.let { nowEpochMillis + it * DAY_MILLIS },
    )
}

@Serializable
data class EmailCodeRequestedDto(
    val sent: Boolean = true,
    val message: String = "If that email has an account, a sign-in key is on its way.",
    val devCode: String? = null,
) {
    fun toDomain() = EmailCodeRequested(message = message, devCode = devCode)
}

/** `GET /auth/me` — the session minus its token, which only the client holds. */
@Serializable
data class StaffMeDto(
    val email: String,
    val role: String,
    val operatorId: Int? = null,
    val operatorName: String? = null,
) {
    /** `/auth/me` never restates the expiry; the caller passes the one it persisted at sign-in. */
    fun toDomain(token: String, expiresAtEpochMillis: Long? = null) = StaffSession(
        token = token,
        email = email,
        role = role.toStaffRole(),
        operatorId = operatorId,
        operatorName = operatorName,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )
}

@Serializable
data class SignedOutDto(val signedOut: Boolean = true)

// ── /admin/api ────────────────────────────────────────────────────────────────

@Serializable
data class DayCountDto(val day: String, val count: Int = 0) {
    fun toDomain() = DayCount(day = day, count = count)
}

@Serializable
data class AdminOverviewDto(
    val operators: Int = 0,
    val terminals: Int = 0,
    val routes: Int = 0,
    val services: Int = 0,
    val journeys: Int = 0,
    // The backend spreads the SQL row here, so these four keys are snake_case.
    @SerialName("upcoming_trips") val upcomingTrips: Int = 0,
    val seats: Int = 0,
    @SerialName("active_holds") val activeHolds: Int = 0,
    val users: Int = 0,
    val bookings: Int = 0,
    @SerialName("cancelled_bookings") val cancelledBookings: Int = 0,
    @SerialName("refunds_pending") val refundsPending: Int = 0,
    val bookingsByDay: List<DayCountDto> = emptyList(),
) {
    fun toDomain() = AdminOverview(
        operators = operators,
        terminals = terminals,
        routes = routes,
        services = services,
        journeys = journeys,
        upcomingTrips = upcomingTrips,
        seats = seats,
        activeHolds = activeHolds,
        users = users,
        bookings = bookings,
        cancelledBookings = cancelledBookings,
        refundsPending = refundsPending,
        bookingsByDay = bookingsByDay.map { it.toDomain() },
    )
}

@Serializable
data class OperatorSummaryDto(
    val id: Int,
    val name: String,
    val services: Int = 0,
    val routes: Int = 0,
    val busClasses: List<String> = emptyList(),
    val rideKinds: List<String> = emptyList(),
    val avgRating: Double? = null,
    val minFarePhp: Double? = null,
    val maxFarePhp: Double? = null,
) {
    fun toDomain() = OperatorSummary(
        id = id,
        name = name,
        services = services,
        routes = routes,
        busClasses = busClasses,
        rideKinds = rideKinds,
        avgRating = avgRating,
        minFarePhp = minFarePhp,
        maxFarePhp = maxFarePhp,
    )
}

/** Shared by /admin/api/operators/{id}/services and every /partner/api/services shape. */
@Serializable
data class OperatorServiceDto(
    val id: Int,
    val origin: String,
    val destination: String,
    val mode: String = "LAND",
    val busClass: String = "ORDINARY",
    val rideKind: String = "BUS",
    val farePhp: Double = 0.0,
    val rating: Double? = null,
    val departureHours: List<Int> = emptyList(),
    val durationMinutes: Int = 0,
    val seatLayout: String = "",
) {
    fun toDomain() = OperatorService(
        id = id,
        origin = origin,
        destination = destination,
        mode = mode,
        busClass = busClass.toBusClass(),
        rideKind = rideKind.toRideKind(),
        farePhp = farePhp,
        rating = rating,
        departureHours = departureHours,
        durationMinutes = durationMinutes,
        seatLayout = seatLayout,
    )
}

@Serializable
data class StaffAccountDto(
    val id: String,
    val email: String,
    val role: String,
    val operatorId: Int? = null,
    val operatorName: String? = null,
    val hasPassword: Boolean = false,
    val createdAtEpochMillis: Long = 0L,
    val lastLoginAtEpochMillis: Long? = null,
) {
    fun toDomain() = StaffAccount(
        id = id,
        email = email,
        role = role.toStaffRole(),
        operatorId = operatorId,
        operatorName = operatorName,
        hasPassword = hasPassword,
        createdAtEpochMillis = createdAtEpochMillis,
        lastLoginAtEpochMillis = lastLoginAtEpochMillis,
    )
}

/** Nulls are omitted on the wire (see file header) — zod rejects explicit nulls here. */
@Serializable
data class CreateAccountRequestDto(
    val email: String,
    val role: String,
    val operatorId: Int? = null,
    val password: String? = null,
)

@Serializable
data class ResetPasswordRequestDto(val password: String? = null)

/** POST /admin/api/accounts (201) and POST .../reset-password share this shape. */
@Serializable
data class AccountCredentialDto(
    val email: String,
    val role: String? = null,
    val operatorId: Int? = null,
    val generatedPassword: String? = null,
    // Reset only: how many of the account's other sessions the server signed out.
    val revokedSessions: Int? = null,
) {
    fun toDomain() = AccountCredential(
        email = email,
        role = role?.let { it.toStaffRole() },
        operatorId = operatorId,
        generatedPassword = generatedPassword,
        revokedSessions = revokedSessions,
    )
}

@Serializable
data class AccountDeletedDto(val deleted: String = "")

@Serializable
data class PartnerTokenIssuedDto(
    val operatorId: Int,
    val operatorName: String = "",
    val token: String,
    val note: String = "",
) {
    fun toDomain() = PartnerTokenIssued(
        operatorId = operatorId,
        operatorName = operatorName,
        token = token,
        note = note,
    )
}

/** Co-passenger as the support console returns it; lenient twin of [CoPassengerDto]. */
@Serializable
data class SupportCoPassengerDto(
    val firstName: String = "",
    val lastName: String = "",
    val mobileNumber: String? = null,
    val type: String? = null,
    val discountIdImagePath: String? = null,
) {
    fun toDomain() = CoPassenger(
        firstName = firstName,
        lastName = lastName,
        mobileNumber = mobileNumber,
        type = type.toPassengerType(),
        discountIdImagePath = discountIdImagePath,
    )
}

@Serializable
data class SupportBookingDto(
    val id: String,
    val status: String = "CONFIRMED",
    val refundStatus: String = "NONE",
    val refundNote: String? = null,
    val cancelReason: String? = null,
    val passengerFullName: String = "",
    val passengerType: String = "REGULAR",
    val infantCount: Int = 0,
    val paymentMethod: String = "CASH_ON_BOARD",
    val paymentStatus: String = "CASH_ON_BOARD",
    val qrPayload: String = "",
    val promoCode: String? = null,
    val userId: String = "",
    val tripId: String = "",
    val departureEpochMillis: Long = 0L,
    val farePhp: Double = 0.0,
    val origin: String = "",
    val destination: String = "",
    val operatorName: String = "",
    val busClass: String = "ORDINARY",
    val rideKind: String = "BUS",
    val seatLabels: List<String> = emptyList(),
    val createdAtEpochMillis: Long = 0L,
    val cancelledAtEpochMillis: Long? = null,
    val refundedAtEpochMillis: Long? = null,
    /** Only the single-booking detail route populates this. */
    val coPassengers: List<SupportCoPassengerDto> = emptyList(),
) {
    fun toDomain() = SupportBooking(
        id = id,
        status = status.toBookingStatus(),
        refundStatus = refundStatus.toRefundStatus(),
        refundNote = refundNote,
        cancelReason = cancelReason,
        passengerFullName = passengerFullName,
        passengerType = passengerType.toPassengerType(),
        infantCount = infantCount,
        paymentMethod = paymentMethod.toPaymentMethod(),
        paymentStatus = paymentStatus.toPaymentStatus(),
        qrPayload = qrPayload,
        promoCode = promoCode,
        userId = userId,
        tripId = tripId,
        departureEpochMillis = departureEpochMillis,
        farePhp = farePhp,
        origin = origin,
        destination = destination,
        operatorName = operatorName,
        busClass = busClass.toBusClass(),
        rideKind = rideKind.toRideKind(),
        seatLabels = seatLabels,
        createdAtEpochMillis = createdAtEpochMillis,
        cancelledAtEpochMillis = cancelledAtEpochMillis,
        refundedAtEpochMillis = refundedAtEpochMillis,
        coPassengers = coPassengers.map { it.toDomain() },
    )
}

@Serializable
data class CancelBookingRequestDto(val reason: String)

@Serializable
data class BookingCancelledDto(val cancelled: Boolean = true, val freedSeats: List<String> = emptyList())

@Serializable
data class RefundRequestDto(val refundStatus: String, val note: String? = null)

@Serializable
data class RefundStatusDto(val refundStatus: String = "NONE") {
    fun toDomain(): RefundStatus = refundStatus.toRefundStatus()
}

@Serializable
data class QrReissuedDto(val qrPayload: String)

/** /admin/api/trips rows carry operatorName; /partner/api/trips rows do not. */
@Serializable
data class TripOccupancyDto(
    val id: String,
    val origin: String = "",
    val destination: String = "",
    val operatorName: String? = null,
    val busClass: String = "ORDINARY",
    val rideKind: String = "BUS",
    val departureEpochMillis: Long = 0L,
    val arrivalEpochMillis: Long = 0L,
    val farePhp: Double = 0.0,
    val seats: Int = 0,
    val sold: Int = 0,
    val held: Int = 0,
    val available: Int = 0,
) {
    fun toDomain() = TripOccupancy(
        id = id,
        origin = origin,
        destination = destination,
        operatorName = operatorName,
        busClass = busClass.toBusClass(),
        rideKind = rideKind.toRideKind(),
        departureEpochMillis = departureEpochMillis,
        arrivalEpochMillis = arrivalEpochMillis,
        farePhp = farePhp,
        seats = seats,
        sold = sold,
        held = held,
        available = available,
    )
}

/** `/admin/api/trips/{id}/seats` row — support context: buyer / holder ids. */
@Serializable
data class AdminSeatDto(
    val id: String,
    val label: String,
    val row: Int = 0,
    val column: Int = 0,
    val status: String = "AVAILABLE",
    val bookingId: String? = null,
    val heldByUserId: String? = null,
    val holdExpiresAtEpochMillis: Long? = null,
) {
    fun toDomain() = AllocatedSeat(
        id = id,
        label = label,
        row = row,
        column = column,
        status = status.toSeatStatusLenient(),
        bookingId = bookingId,
        heldByUserId = heldByUserId,
        holdExpiresAtEpochMillis = holdExpiresAtEpochMillis,
    )
}

/** `/partner/api/trips/{id}/seats` row — no seat id; passenger name on sold seats. */
@Serializable
data class PartnerSeatDto(
    val label: String,
    val row: Int = 0,
    val column: Int = 0,
    val status: String = "AVAILABLE",
    val passengerName: String? = null,
) {
    fun toDomain() = AllocatedSeat(
        id = null,
        label = label,
        row = row,
        column = column,
        status = status.toSeatStatusLenient(),
        passengerName = passengerName,
    )
}

@Serializable
data class RouteSummaryDto(
    val id: Int,
    val origin: String = "",
    val destination: String = "",
    val mode: String = "LAND",
    val services: Int = 0,
) {
    fun toDomain() = RouteSummary(id = id, origin = origin, destination = destination, mode = mode, services = services)
}

/**
 * Curated journey as stored server-side. `journeys.id` is a SERIAL there, so
 * it arrives as a JSON number; the domain id is a String, hence [JsonPrimitive].
 */
@Serializable
data class CuratedJourneyDto(
    val id: JsonPrimitive,
    val title: String = "",
    val from: String = "",
    val to: String = "",
    val matchKeywords: List<String> = emptyList(),
    val legs: List<JourneyLegDto> = emptyList(),
) {
    fun toDomain() = CuratedJourney(
        id = id.content,
        title = title,
        from = from,
        to = to,
        matchKeywords = matchKeywords,
        legs = legs.map { it.toDomain() },
    )
}

@Serializable
data class PassengerAccountDto(
    val id: String,
    val createdAtEpochMillis: Long = 0L,
    val bookings: Int = 0,
    val cancelled: Int = 0,
) {
    fun toDomain() = PassengerAccount(
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        bookings = bookings,
        cancelled = cancelled,
    )
}

@Serializable
data class LiveHoldDto(
    val seatId: String,
    val label: String = "",
    val tripId: String = "",
    val origin: String = "",
    val destination: String = "",
    val departureEpochMillis: Long = 0L,
    val userId: String = "",
    val expiresAtEpochMillis: Long = 0L,
) {
    fun toDomain() = LiveHold(
        seatId = seatId,
        label = label,
        tripId = tripId,
        origin = origin,
        destination = destination,
        departureEpochMillis = departureEpochMillis,
        userId = userId,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )
}

// ── /partner/api ──────────────────────────────────────────────────────────────

@Serializable
data class PartnerOverviewDto(
    val operatorId: Int,
    val operatorName: String = "",
    val readOnly: Boolean = false,
    val services: Int = 0,
    val routes: Int = 0,
    val upcomingTrips: Int = 0,
    val departuresNext24h: Int = 0,
    val seatsSoldUpcoming: Int = 0,
    val upcomingRevenuePhp: Double = 0.0,
) {
    fun toDomain() = PartnerOverview(
        operatorId = operatorId,
        operatorName = operatorName,
        readOnly = readOnly,
        services = services,
        routes = routes,
        upcomingTrips = upcomingTrips,
        departuresNext24h = departuresNext24h,
        seatsSoldUpcoming = seatsSoldUpcoming,
        upcomingRevenuePhp = upcomingRevenuePhp,
    )
}

@Serializable
data class CreateServiceRequestDto(
    val origin: String,
    val destination: String,
    val busClass: String,
    val rideKind: String,
    val farePhp: Double,
    val departureHours: List<Int>,
    val durationMinutes: Int,
) {
    companion object {
        fun from(service: NewService) = CreateServiceRequestDto(
            origin = service.origin.trim(),
            destination = service.destination.trim(),
            busClass = service.busClass.name,
            rideKind = service.rideKind.name,
            farePhp = service.farePhp,
            departureHours = service.departureHours.distinct().sorted(),
            durationMinutes = service.durationMinutes,
        )
    }
}

/** PATCH body: unchanged fields stay null and are omitted on the wire. */
@Serializable
data class UpdateServiceRequestDto(
    val farePhp: Double? = null,
    val departureHours: List<Int>? = null,
    val durationMinutes: Int? = null,
) {
    companion object {
        fun from(update: ServiceUpdate) = UpdateServiceRequestDto(
            farePhp = update.farePhp,
            departureHours = update.departureHours?.distinct()?.sorted(),
            durationMinutes = update.durationMinutes,
        )
    }
}

@Serializable
data class ServiceCreatedDto(
    val service: OperatorServiceDto,
    val generatedTrips: Int = 0,
    val generatedSeats: Int = 0,
) {
    fun toDomain() = ServiceCreated(
        service = service.toDomain(),
        generatedTrips = generatedTrips,
        generatedSeats = generatedSeats,
    )
}

@Serializable
data class ServiceChangedDto(
    val service: OperatorServiceDto,
    val repricedTrips: Int = 0,
    val removedTrips: Int = 0,
    val addedTrips: Int = 0,
) {
    fun toDomain() = ServiceChanged(
        service = service.toDomain(),
        repricedTrips = repricedTrips,
        removedTrips = removedTrips,
        addedTrips = addedTrips,
    )
}

/** POST /partner/api/trips — [date] `yyyy-MM-dd`, [time] `HH:mm`, both PH time. */
@Serializable
data class ExtraTripRequestDto(val serviceId: Int, val date: String, val time: String)

@Serializable
data class ExtraTripCreatedDto(val tripId: String, val seats: Int = 0) {
    fun toDomain() = ExtraTripCreated(tripId = tripId, seats = seats)
}

/** Exactly one of [seatLabels] / [count] must be set; the other stays null and is omitted. */
@Serializable
data class OnsiteSaleRequestDto(
    val seatLabels: List<String>? = null,
    val count: Int? = null,
    val passengerFullName: String = "Walk-in passenger",
) {
    companion object {
        fun from(sale: OnsiteSaleRequest) = OnsiteSaleRequestDto(
            seatLabels = sale.seatLabels?.takeIf { it.isNotEmpty() },
            count = sale.count,
            passengerFullName = sale.passengerFullName.trim().ifEmpty { "Walk-in passenger" },
        )
    }
}

@Serializable
data class OnsiteSaleIssuedDto(val ticketId: String, val seatLabels: List<String> = emptyList()) {
    fun toDomain() = OnsiteSaleIssued(ticketId = ticketId, seatLabels = seatLabels)
}

@Serializable
data class ManifestEntryDto(
    val id: String,
    val passengerFullName: String = "",
    val passengerType: String = "REGULAR",
    val partySize: Int = 1,
    val infantCount: Int = 0,
    val paymentMethod: String = "CASH_ON_BOARD",
    val paymentStatus: String = "CASH_ON_BOARD",
    val status: String = "CONFIRMED",
    val tripId: String = "",
    val departureEpochMillis: Long = 0L,
    val origin: String = "",
    val destination: String = "",
    val seatLabels: List<String> = emptyList(),
) {
    fun toDomain() = ManifestEntry(
        id = id,
        passengerFullName = passengerFullName,
        passengerType = passengerType.toPassengerType(),
        partySize = partySize,
        infantCount = infantCount,
        paymentMethod = paymentMethod.toPaymentMethod(),
        paymentStatus = paymentStatus.toPaymentStatus(),
        status = status.toBookingStatus(),
        tripId = tripId,
        departureEpochMillis = departureEpochMillis,
        origin = origin,
        destination = destination,
        seatLabels = seatLabels,
    )
}
