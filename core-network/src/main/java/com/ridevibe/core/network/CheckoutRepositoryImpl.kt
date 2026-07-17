package com.ridevibe.core.network

import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import com.ridevibe.core.network.api.CrsApiService
import com.ridevibe.core.network.dto.BookingRequestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckoutRepositoryImpl @Inject constructor(
    private val apiService: CrsApiService,
) : CheckoutRepository {

    override suspend fun confirmBooking(
        tripId: String,
        seatId: String,
        passenger: Passenger,
        paymentStatus: PaymentStatus,
    ): Result<Ticket> = runCatching {
        val request = BookingRequestDto(
            passengerFullName = passenger.fullName,
            passengerType = passenger.type.name,
            discountIdImagePath = passenger.discountIdImagePath,
            paymentStatus = paymentStatus.name,
        )
        apiService.confirmBooking(tripId, seatId, request).toDomain()
    }
}
