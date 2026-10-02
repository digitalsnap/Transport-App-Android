package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.AccountCredential
import com.ridevibe.core.domain.model.AdminOverview
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.CuratedJourney
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
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.core.domain.model.SupportBooking
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.TripOccupancy
import kotlinx.coroutines.flow.StateFlow

/**
 * Staff sign-in for the consolidated admin/partner console (`/auth/…`).
 *
 * The session is persisted by the implementation (encrypted at rest) and
 * observed through [session]; every other staff repository reads the token
 * from the same store, so callers never pass credentials around.
 */
interface StaffAuthRepository {
    /** Null when signed out. Emits on sign-in, sign-out, and process restart. */
    val session: StateFlow<StaffSession?>

    suspend fun getAuthOptions(): Result<StaffAuthOptions>

    suspend fun signInWithPassword(email: String, password: String): Result<StaffSession>

    /** Sends a 6-digit key to [email]. Succeeds identically whether or not the account exists. */
    suspend fun requestEmailCode(email: String): Result<EmailCodeRequested>

    suspend fun signInWithEmailCode(email: String, code: String): Result<StaffSession>

    /** Exchanges a Google ID token (web-client audience) for a session. */
    suspend fun signInWithGoogle(idToken: String): Result<StaffSession>

    /** Validates the persisted session against the server; clears it when the server rejects it. */
    suspend fun refreshSession(): Result<StaffSession?>

    suspend fun signOut()
}

/** `/admin/api/…` — requires an ADMIN session (or the legacy env token). */
interface AdminRepository {
    suspend fun getOverview(): Result<AdminOverview>

    suspend fun getOperators(): Result<List<OperatorSummary>>

    suspend fun getOperatorServices(operatorId: Int): Result<List<OperatorService>>

    /** Rotates the operator's legacy `pt_…` API token; the plaintext is returned once. */
    suspend fun issuePartnerToken(operatorId: Int): Result<PartnerTokenIssued>

    suspend fun getAccounts(): Result<List<StaffAccount>>

    /** Omit [password] to have one generated and returned once. PARTNER needs [operatorId]. */
    suspend fun createAccount(
        email: String,
        role: StaffRole,
        operatorId: Int? = null,
        password: String? = null,
    ): Result<AccountCredential>

    suspend fun resetPassword(accountId: String, password: String? = null): Result<AccountCredential>

    suspend fun deleteAccount(accountId: String): Result<Unit>

    /** Free-text over ticket id, passenger name, user id, or QR payload. Newest first. */
    suspend fun searchBookings(
        query: String = "",
        status: BookingStatus? = null,
        limit: Int = 50,
    ): Result<List<SupportBooking>>

    suspend fun getBooking(bookingId: String): Result<SupportBooking>

    /** Frees the seats back into inventory and broadcasts the change. Returns the freed labels. */
    suspend fun cancelBooking(bookingId: String, reason: String): Result<List<String>>

    /** Bookkeeping only: no payment provider is integrated. */
    suspend fun setRefundStatus(bookingId: String, status: RefundStatus, note: String? = null): Result<RefundStatus>

    suspend fun reissueQr(bookingId: String): Result<String>

    /** Departures on a PH calendar date (ISO `yyyy-MM-dd`; null = today). */
    suspend fun getTrips(
        dateIso: String? = null,
        operator: String = "",
        place: String = "",
        limit: Int = 100,
        offset: Int = 0,
    ): Result<List<TripOccupancy>>

    suspend fun getTripSeats(tripId: String): Result<List<AllocatedSeat>>

    suspend fun getTerminals(): Result<List<TerminalLocation>>

    suspend fun getRoutes(query: String = ""): Result<List<RouteSummary>>

    suspend fun getJourneys(): Result<List<CuratedJourney>>

    suspend fun getPassengerAccounts(): Result<List<PassengerAccount>>

    suspend fun getLiveHolds(): Result<List<LiveHold>>
}

/**
 * `/partner/api/…` — the operator's own portal.
 *
 * A PARTNER session acts for its own operator and [operatorId] is ignored. An
 * ADMIN session must pass [operatorId] and is strictly read-only: every write
 * fails with a 403 the implementation surfaces as a failed [Result].
 */
interface PartnerRepository {
    suspend fun getOverview(operatorId: Int? = null): Result<PartnerOverview>

    suspend fun getServices(operatorId: Int? = null): Result<List<OperatorService>>

    /** Creates the service and generates its trips for the 14-day horizon. */
    suspend fun createService(service: NewService): Result<ServiceCreated>

    /** Applies to future departures with no sold seats only. */
    suspend fun updateService(serviceId: Int, update: ServiceUpdate): Result<ServiceChanged>

    /** One-off departure outside the regular hours. [dateIso] `yyyy-MM-dd`, [timeHm] `HH:mm` PH time. */
    suspend fun addExtraTrip(serviceId: Int, dateIso: String, timeHm: String): Result<ExtraTripCreated>

    suspend fun getTrips(dateIso: String? = null, operatorId: Int? = null): Result<List<TripOccupancy>>

    /** Seat map with passenger names on sold seats (the boarding manifest). */
    suspend fun getTripSeats(tripId: String, operatorId: Int? = null): Result<List<AllocatedSeat>>

    /** Records a walk-up counter sale and broadcasts OCCUPIED to rider apps. */
    suspend fun recordOnsiteSale(tripId: String, sale: OnsiteSaleRequest): Result<OnsiteSaleIssued>

    suspend fun getManifest(dateIso: String? = null, operatorId: Int? = null): Result<List<ManifestEntry>>
}
