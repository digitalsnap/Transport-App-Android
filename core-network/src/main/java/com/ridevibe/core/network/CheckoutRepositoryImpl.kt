package com.ridevibe.core.network

import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import com.ridevibe.core.network.api.CrsApiService
import com.ridevibe.core.network.dto.BookingRequestDto
import com.ridevibe.core.network.dto.CoPassengerDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckoutRepositoryImpl @Inject constructor(
    private val apiService: CrsApiService,
) : CheckoutRepository {

    override suspend fun confirmBooking(
        tripId: String,
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
        infantCount: Int,
        paymentMethod: PaymentMethod,
        promoCode: String?,
    ): Result<Ticket> = runCatching {
        val request = BookingRequestDto(
            seatIds = seatIds,
            passengerFullName = primaryPassenger.fullName,
            passengerType = primaryPassenger.type.name,
            discountIdImagePath = primaryPassenger.discountIdImagePath,
            coPassengers = coPassengers.map {
                CoPassengerDto(it.firstName, it.lastName, it.mobileNumber, it.type.name, it.discountIdImagePath)
            },
            infantCount = infantCount,
            paymentMethod = paymentMethod.name,
            promoCode = promoCode?.takeIf { it.isNotBlank() },
        )
        apiService.confirmBooking(tripId, request).toDomain()
    }

    override suspend fun getTicket(ticketId: String): Result<Ticket> =
        runCatching { apiService.getTicket(ticketId).toDomain() }

    override suspend fun getMyBookings(): List<Ticket> =
        runCatching { apiService.getMyBookings().map { it.toDomain() } }.getOrDefault(emptyList())
}
