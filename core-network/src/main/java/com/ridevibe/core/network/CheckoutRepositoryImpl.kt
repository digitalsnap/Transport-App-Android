package com.ridevibe.core.network

import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import com.ridevibe.core.network.api.CrsApiService
import com.ridevibe.core.network.api.passengerApiResult
import com.ridevibe.core.network.cache.TicketCache
import com.ridevibe.core.network.dto.BookingRequestDto
import com.ridevibe.core.network.dto.CoPassengerDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `/v1` booking and ticket retrieval. Every successful ticket read or issue is
 * written through to [TicketCache] so the QR is still on screen when the
 * rider is at a terminal with no signal; [cachedBookings] is that offline view.
 */
@Singleton
class CheckoutRepositoryImpl @Inject constructor(
    private val apiService: CrsApiService,
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
    ): Result<Ticket> = passengerApiResult {
        val request = BookingRequestDto(
            seatIds = seatIds,
            passengerFullName = primaryPassenger.fullName,
            passengerType = primaryPassenger.type.name,
            discountIdImagePath = primaryPassenger.discountIdImagePath,
            coPassengers = coPassengers.map { CoPassengerDto.from(it) },
            infantCount = infantCount,
            paymentMethod = paymentMethod.name,
            promoCode = promoCode?.takeIf { it.isNotBlank() },
            clientReference = clientReference,
        )
        apiService.confirmBooking(tripId, request)
            .also(ticketCache::upsert)
            .toDomain()
    }

    override suspend fun getTicket(ticketId: String): Result<Ticket> =
        passengerApiResult { apiService.getTicket(ticketId).also(ticketCache::upsert).toDomain() }

    override suspend fun getMyBookings(): Result<List<Ticket>> = passengerApiResult {
        apiService.getMyBookings()
            .also(ticketCache::replaceAll)
            .map { it.toDomain() }
    }

    override fun cachedBookings(): List<Ticket> =
        ticketCache.tickets().map { it.toDomain() }.sortedByDescending { it.trip.departureEpochMillis }
}
