package com.ridevibe.core.network.api

import com.ridevibe.core.network.dto.AccountCredentialDto
import com.ridevibe.core.network.dto.AccountDeletedDto
import com.ridevibe.core.network.dto.AdminOverviewDto
import com.ridevibe.core.network.dto.AdminSeatDto
import com.ridevibe.core.network.dto.BookingCancelledDto
import com.ridevibe.core.network.dto.CancelBookingRequestDto
import com.ridevibe.core.network.dto.CreateAccountRequestDto
import com.ridevibe.core.network.dto.CreateServiceRequestDto
import com.ridevibe.core.network.dto.CuratedJourneyDto
import com.ridevibe.core.network.dto.EmailCodeRequestDto
import com.ridevibe.core.network.dto.EmailCodeRequestedDto
import com.ridevibe.core.network.dto.EmailCodeVerifyRequestDto
import com.ridevibe.core.network.dto.ExtraTripCreatedDto
import com.ridevibe.core.network.dto.ExtraTripRequestDto
import com.ridevibe.core.network.dto.GoogleSignInRequestDto
import com.ridevibe.core.network.dto.LiveHoldDto
import com.ridevibe.core.network.dto.LocationDto
import com.ridevibe.core.network.dto.LoginRequestDto
import com.ridevibe.core.network.dto.ManifestEntryDto
import com.ridevibe.core.network.dto.OnsiteSaleIssuedDto
import com.ridevibe.core.network.dto.OnsiteSaleRequestDto
import com.ridevibe.core.network.dto.OperatorServiceDto
import com.ridevibe.core.network.dto.OperatorSummaryDto
import com.ridevibe.core.network.dto.PartnerOverviewDto
import com.ridevibe.core.network.dto.PartnerSeatDto
import com.ridevibe.core.network.dto.PartnerTokenIssuedDto
import com.ridevibe.core.network.dto.PassengerAccountDto
import com.ridevibe.core.network.dto.QrReissuedDto
import com.ridevibe.core.network.dto.RefundRequestDto
import com.ridevibe.core.network.dto.RefundStatusDto
import com.ridevibe.core.network.dto.ResetPasswordRequestDto
import com.ridevibe.core.network.dto.RouteSummaryDto
import com.ridevibe.core.network.dto.ServiceChangedDto
import com.ridevibe.core.network.dto.ServiceCreatedDto
import com.ridevibe.core.network.dto.SignedOutDto
import com.ridevibe.core.network.dto.StaffAccountDto
import com.ridevibe.core.network.dto.StaffAuthConfigDto
import com.ridevibe.core.network.dto.StaffMeDto
import com.ridevibe.core.network.dto.StaffSessionDto
import com.ridevibe.core.network.dto.SupportBookingDto
import com.ridevibe.core.network.dto.TripOccupancyDto
import com.ridevibe.core.network.dto.UpdateServiceRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Staff surfaces of the CRS: `/auth/…`, `/admin/api/…`, `/partner/api/…`
 * (outside the `/v1` client contract). The session token is attached by
 * [com.ridevibe.core.network.auth.StaffTokenInterceptor] — `x-admin-token` for
 * ADMIN sessions, `x-partner-token` for PARTNER — so no call here takes it.
 *
 * An ADMIN session may READ `/partner/api/…` for any operator by passing
 * `operatorId`; every partner write returns 403 for admins.
 */
interface StaffApiService {

    // ── /auth ─────────────────────────────────────────────────────────────────

    @GET("auth/config")
    suspend fun getAuthConfig(): StaffAuthConfigDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): StaffSessionDto

    @POST("auth/email-code")
    suspend fun requestEmailCode(@Body body: EmailCodeRequestDto): EmailCodeRequestedDto

    @POST("auth/email-code/verify")
    suspend fun verifyEmailCode(@Body body: EmailCodeVerifyRequestDto): StaffSessionDto

    @POST("auth/google")
    suspend fun signInWithGoogle(@Body body: GoogleSignInRequestDto): StaffSessionDto

    @GET("auth/me")
    suspend fun me(): StaffMeDto

    @POST("auth/logout")
    suspend fun logout(): SignedOutDto

    // ── /admin/api ────────────────────────────────────────────────────────────

    @GET("admin/api/overview")
    suspend fun adminOverview(): AdminOverviewDto

    @GET("admin/api/operators")
    suspend fun adminOperators(): List<OperatorSummaryDto>

    @GET("admin/api/operators/{id}/services")
    suspend fun adminOperatorServices(@Path("id") operatorId: Int): List<OperatorServiceDto>

    /** Rotates the operator's legacy `pt_…` token; plaintext returned once. */
    @POST("admin/api/operators/{id}/partner-token")
    suspend fun adminIssuePartnerToken(@Path("id") operatorId: Int): PartnerTokenIssuedDto

    @GET("admin/api/accounts")
    suspend fun adminAccounts(): List<StaffAccountDto>

    @POST("admin/api/accounts")
    suspend fun adminCreateAccount(@Body body: CreateAccountRequestDto): AccountCredentialDto

    @POST("admin/api/accounts/{id}/reset-password")
    suspend fun adminResetPassword(
        @Path("id") accountId: String,
        @Body body: ResetPasswordRequestDto,
    ): AccountCredentialDto

    @DELETE("admin/api/accounts/{id}")
    suspend fun adminDeleteAccount(@Path("id") accountId: String): AccountDeletedDto

    /** [status] is CONFIRMED | CANCELLED or null for both; [limit] 1..200. */
    @GET("admin/api/bookings")
    suspend fun adminBookings(
        @Query("query") query: String = "",
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
    ): List<SupportBookingDto>

    @GET("admin/api/bookings/{id}")
    suspend fun adminBooking(@Path("id") bookingId: String): SupportBookingDto

    @POST("admin/api/bookings/{id}/cancel")
    suspend fun adminCancelBooking(
        @Path("id") bookingId: String,
        @Body body: CancelBookingRequestDto,
    ): BookingCancelledDto

    @POST("admin/api/bookings/{id}/refund")
    suspend fun adminRefundBooking(
        @Path("id") bookingId: String,
        @Body body: RefundRequestDto,
    ): RefundStatusDto

    @POST("admin/api/bookings/{id}/reissue-qr")
    suspend fun adminReissueQr(@Path("id") bookingId: String): QrReissuedDto

    /** [date] `yyyy-MM-dd` PH calendar day (null = today); [limit] 1..500. */
    @GET("admin/api/trips")
    suspend fun adminTrips(
        @Query("date") date: String? = null,
        @Query("operator") operator: String = "",
        @Query("place") place: String = "",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): List<TripOccupancyDto>

    @GET("admin/api/trips/{id}/seats")
    suspend fun adminTripSeats(@Path("id") tripId: String): List<AdminSeatDto>

    @GET("admin/api/terminals")
    suspend fun adminTerminals(): List<LocationDto>

    @GET("admin/api/routes")
    suspend fun adminRoutes(@Query("query") query: String = ""): List<RouteSummaryDto>

    @GET("admin/api/journeys")
    suspend fun adminJourneys(): List<CuratedJourneyDto>

    @GET("admin/api/users")
    suspend fun adminUsers(): List<PassengerAccountDto>

    @GET("admin/api/holds")
    suspend fun adminHolds(): List<LiveHoldDto>

    // ── /partner/api ──────────────────────────────────────────────────────────
    // `operatorId` is only consulted for the admin read-only view; a PARTNER
    // session acts for its own operator and the server ignores it.

    @GET("partner/api/me")
    suspend fun partnerMe(@Query("operatorId") operatorId: Int? = null): PartnerOverviewDto

    @GET("partner/api/services")
    suspend fun partnerServices(@Query("operatorId") operatorId: Int? = null): List<OperatorServiceDto>

    @POST("partner/api/services")
    suspend fun partnerCreateService(@Body body: CreateServiceRequestDto): ServiceCreatedDto

    @PATCH("partner/api/services/{id}")
    suspend fun partnerUpdateService(
        @Path("id") serviceId: Int,
        @Body body: UpdateServiceRequestDto,
    ): ServiceChangedDto

    @POST("partner/api/trips")
    suspend fun partnerAddExtraTrip(@Body body: ExtraTripRequestDto): ExtraTripCreatedDto

    @POST("partner/api/trips/{id}/onsite")
    suspend fun partnerOnsiteSale(
        @Path("id") tripId: String,
        @Body body: OnsiteSaleRequestDto,
    ): OnsiteSaleIssuedDto

    @GET("partner/api/trips")
    suspend fun partnerTrips(
        @Query("date") date: String? = null,
        @Query("operatorId") operatorId: Int? = null,
    ): List<TripOccupancyDto>

    @GET("partner/api/trips/{id}/seats")
    suspend fun partnerTripSeats(
        @Path("id") tripId: String,
        @Query("operatorId") operatorId: Int? = null,
    ): List<PartnerSeatDto>

    /** Booking manifest for a PH calendar day of departures. */
    @GET("partner/api/bookings")
    suspend fun partnerManifest(
        @Query("date") date: String? = null,
        @Query("operatorId") operatorId: Int? = null,
    ): List<ManifestEntryDto>
}
