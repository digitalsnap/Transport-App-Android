package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import javax.inject.Inject

class ConfirmBookingUseCase @Inject constructor(
    private val checkoutRepository: CheckoutRepository,
) {
    suspend operator fun invoke(
        tripId: String,
        seatId: String,
        passenger: Passenger,
        paymentStatus: PaymentStatus,
    ): Result<Ticket> = checkoutRepository.confirmBooking(tripId, seatId, passenger, paymentStatus)
}
