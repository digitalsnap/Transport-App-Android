package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import java.util.UUID
import javax.inject.Inject

/** A booking that must not reach the server: the rider still has something to fix. */
class BookingValidationException(message: String) : IllegalArgumentException(message)

/**
 * The idempotency key for one checkout attempt. Generate it ONCE per attempt
 * and reuse it on retries, so a booking that succeeded server-side while the
 * response was lost is found again instead of duplicated.
 */
fun newClientReference(): String = UUID.randomUUID().toString()

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
        clientReference: String = newClientReference(),
    ): Result<Ticket> {
        validate(seatIds, primaryPassenger, coPassengers)?.let { return Result.failure(it) }
        return checkoutRepository.confirmBooking(
            tripId = tripId,
            seatIds = seatIds,
            primaryPassenger = primaryPassenger,
            coPassengers = coPassengers,
            infantCount = infantCount,
            paymentMethod = paymentMethod,
            promoCode = promoCode,
            clientReference = clientReference,
        )
    }

    /**
     * The rules the server would refuse (409 without seats, 422 for a discount
     * without an ID) — checked here first so the rider gets a plain message
     * instead of a round trip.
     */
    private fun validate(
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
    ): BookingValidationException? {
        if (seatIds.none { it.isNotBlank() }) {
            return BookingValidationException("Pick at least one seat before booking.")
        }
        if (primaryPassenger.type.requiresIdCapture && primaryPassenger.discountIdImagePath.isNullOrBlank()) {
            return BookingValidationException(
                "A photo of the discount ID is needed for ${primaryPassenger.fullName.ifBlank { "the primary passenger" }}.",
            )
        }
        coPassengers.firstOrNull { it.type.requiresIdCapture && it.discountIdImagePath.isNullOrBlank() }?.let {
            val name = listOf(it.firstName, it.lastName).filter { part -> part.isNotBlank() }.joinToString(" ")
            return BookingValidationException(
                "A photo of the discount ID is needed for ${name.ifBlank { "each discounted co-passenger" }}.",
            )
        }
        return null
    }
}
