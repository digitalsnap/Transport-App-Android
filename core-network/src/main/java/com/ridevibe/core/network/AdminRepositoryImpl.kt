package com.ridevibe.core.network

import com.ridevibe.core.domain.model.AccountCredential
import com.ridevibe.core.domain.model.AdminOverview
import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.CuratedJourney
import com.ridevibe.core.domain.model.LiveHold
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.OperatorSummary
import com.ridevibe.core.domain.model.PartnerTokenIssued
import com.ridevibe.core.domain.model.PassengerAccount
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.core.domain.model.RouteSummary
import com.ridevibe.core.domain.model.StaffAccount
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.model.SupportBooking
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.core.domain.repository.AdminRepository
import com.ridevibe.core.network.api.StaffApiService
import com.ridevibe.core.network.api.apiResult
import com.ridevibe.core.network.dto.CancelBookingRequestDto
import com.ridevibe.core.network.dto.CreateAccountRequestDto
import com.ridevibe.core.network.dto.RefundRequestDto
import com.ridevibe.core.network.dto.ResetPasswordRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/** `/admin/api/…`. The ADMIN session header is attached by the token interceptor. */
@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val api: StaffApiService,
) : AdminRepository {

    override suspend fun getOverview(): Result<AdminOverview> =
        apiResult { api.adminOverview().toDomain() }

    override suspend fun getOperators(): Result<List<OperatorSummary>> =
        apiResult { api.adminOperators().map { it.toDomain() } }

    override suspend fun getOperatorServices(operatorId: Int): Result<List<OperatorService>> =
        apiResult { api.adminOperatorServices(operatorId).map { it.toDomain() } }

    override suspend fun issuePartnerToken(operatorId: Int): Result<PartnerTokenIssued> =
        apiResult { api.adminIssuePartnerToken(operatorId).toDomain() }

    override suspend fun getAccounts(): Result<List<StaffAccount>> =
        apiResult { api.adminAccounts().map { it.toDomain() } }

    override suspend fun createAccount(
        email: String,
        role: StaffRole,
        operatorId: Int?,
        password: String?,
    ): Result<AccountCredential> = apiResult {
        api.adminCreateAccount(
            CreateAccountRequestDto(
                email = email.trim().lowercase(),
                role = role.name,
                operatorId = operatorId,
                password = password?.takeIf { it.isNotBlank() },
            ),
        ).toDomain()
    }

    override suspend fun resetPassword(accountId: String, password: String?): Result<AccountCredential> =
        apiResult {
            api.adminResetPassword(accountId, ResetPasswordRequestDto(password = password?.takeIf { it.isNotBlank() }))
                .toDomain()
        }

    override suspend fun deleteAccount(accountId: String): Result<Unit> =
        apiResult { api.adminDeleteAccount(accountId); Unit }

    override suspend fun searchBookings(
        query: String,
        status: BookingStatus?,
        limit: Int,
    ): Result<List<SupportBooking>> = apiResult {
        api.adminBookings(
            query = query.trim(),
            status = status?.name,
            limit = limit.coerceIn(1, 200),
        ).map { it.toDomain() }
    }

    override suspend fun getBooking(bookingId: String): Result<SupportBooking> =
        apiResult { api.adminBooking(bookingId).toDomain() }

    override suspend fun cancelBooking(bookingId: String, reason: String): Result<List<String>> =
        apiResult { api.adminCancelBooking(bookingId, CancelBookingRequestDto(reason = reason.trim())).freedSeats }

    override suspend fun setRefundStatus(
        bookingId: String,
        status: RefundStatus,
        note: String?,
    ): Result<RefundStatus> = apiResult {
        api.adminRefundBooking(
            bookingId,
            RefundRequestDto(refundStatus = status.name, note = note?.takeIf { it.isNotBlank() }),
        ).toDomain()
    }

    override suspend fun reissueQr(bookingId: String): Result<String> =
        apiResult { api.adminReissueQr(bookingId).qrPayload }

    override suspend fun getTrips(
        dateIso: String?,
        operator: String,
        place: String,
        limit: Int,
        offset: Int,
    ): Result<List<TripOccupancy>> = apiResult {
        api.adminTrips(
            date = dateIso?.takeIf { it.isNotBlank() },
            operator = operator.trim(),
            place = place.trim(),
            limit = limit.coerceIn(1, 500),
            offset = offset.coerceAtLeast(0),
        ).map { it.toDomain() }
    }

    override suspend fun getTripSeats(tripId: String): Result<List<AllocatedSeat>> =
        apiResult { api.adminTripSeats(tripId).map { it.toDomain() } }

    override suspend fun getTerminals(): Result<List<TerminalLocation>> =
        apiResult { api.adminTerminals().map { it.toDomain() } }

    override suspend fun getRoutes(query: String): Result<List<RouteSummary>> =
        apiResult { api.adminRoutes(query.trim()).map { it.toDomain() } }

    override suspend fun getJourneys(): Result<List<CuratedJourney>> =
        apiResult { api.adminJourneys().map { it.toDomain() } }

    override suspend fun getPassengerAccounts(): Result<List<PassengerAccount>> =
        apiResult { api.adminUsers().map { it.toDomain() } }

    override suspend fun getLiveHolds(): Result<List<LiveHold>> =
        apiResult { api.adminHolds().map { it.toDomain() } }
}
