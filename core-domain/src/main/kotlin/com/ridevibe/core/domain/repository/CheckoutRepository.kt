package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Ticket

interface CheckoutRepository {
    suspend fun confirmBooking(
        tripId: String,
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
        infantCount: Int,
        paymentMethod: PaymentMethod,
        promoCode: String? = null,
    ): Result<Ticket>

    suspend fun getTicket(ticketId: String): Result<Ticket>

    /** All of the account's bookings, newest departure first. */
    suspend fun getMyBookings(): List<Ticket>
}
