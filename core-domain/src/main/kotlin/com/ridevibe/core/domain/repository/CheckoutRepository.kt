package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Ticket

interface CheckoutRepository {
    /**
     * [clientReference] is the idempotency key for this checkout attempt (see
     * `newClientReference()`): the server dedupes on it, so a retry after a
     * lost response returns the ticket already issued instead of a second one.
     */
    suspend fun confirmBooking(
        tripId: String,
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
        infantCount: Int,
        paymentMethod: PaymentMethod,
        promoCode: String? = null,
        clientReference: String,
    ): Result<Ticket>

    suspend fun getTicket(ticketId: String): Result<Ticket>

    /** All of the account's bookings, newest departure first. Fails when offline — use [cachedBookings] then. */
    suspend fun getMyBookings(): Result<List<Ticket>>

    /** The last list [getMyBookings] fetched successfully (or tickets seen since); empty on a fresh install. */
    fun cachedBookings(): List<Ticket>
}
