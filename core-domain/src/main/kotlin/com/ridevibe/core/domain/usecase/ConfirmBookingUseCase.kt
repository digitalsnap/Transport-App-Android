package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import javax.inject.Inject

class ConfirmBookingUseCase @Inject constructor(
    private val checkoutRepository: CheckoutRepository,
) {
    suspend operator fun invoke(
        tripId: String,
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
        infantCount: Int,
        paymentMethod: PaymentMethod,
        promoCode: String? = null,
    ): Result<Ticket> = checkoutRepository.confirmBooking(
        tripId = tripId,
        seatIds = seatIds,
        primaryPassenger = primaryPassenger,
        coPassengers = coPassengers,
        infantCount = infantCount,
        paymentMethod = paymentMethod,
        promoCode = promoCode,
    )
}
