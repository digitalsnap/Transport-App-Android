package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Ticket

interface CheckoutRepository {
    suspend fun confirmBooking(
        tripId: String,
        seatId: String,
        passenger: Passenger,
        paymentStatus: PaymentStatus,
    ): Result<Ticket>
}
