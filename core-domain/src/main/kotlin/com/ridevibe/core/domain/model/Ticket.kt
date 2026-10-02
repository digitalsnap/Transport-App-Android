package com.ridevibe.core.domain.model

/**
 * Additional passenger on a multi-seat booking: first/last name for the
 * manifest, mobile number optional, and their own fare type — Student/
 * Senior/PWD discounts require an ID photo just like the primary passenger.
 */
data class CoPassenger(
    val firstName: String,
    val lastName: String,
    val mobileNumber: String? = null,
    val type: PassengerType = PassengerType.REGULAR,
    val discountIdImagePath: String? = null,
)

/**
 * Lifecycle of a booking, shared by the rider's [Ticket] and the staff
 * console's support views. REFUNDED is a cancelled booking whose fare was
 * returned; the staff surface tracks the refund bookkeeping separately in
 * [RefundStatus].
 */
enum class BookingStatus { CONFIRMED, CANCELLED, REFUNDED }

data class Ticket(
    val id: String,
    val trip: Trip,
    /** One entry per booked seat; first seat belongs to the primary passenger. */
    val seatLabels: List<String>,
    val primaryPassenger: Passenger,
    val coPassengers: List<CoPassenger> = emptyList(),
    /** Lap-held infants — free of charge, no seat assigned. */
    val infantCount: Int = 0,
    val paymentStatus: PaymentStatus,
    /** Single QR encoding the whole ticket: all passengers, seats, status. */
    val qrPayload: String,
    /** For CASH_ON_BOARD reservations: when the unpaid hold lapses. Null once paid. */
    val reservationExpiresAtEpochMillis: Long? = null,
    /** CONFIRMED unless support cancelled or refunded it (or a cash reservation lapsed). */
    val status: BookingStatus = BookingStatus.CONFIRMED,
    /** The idempotency key the client sent when booking; lets a retried checkout find its ticket. */
    val clientReference: String? = null,
)

enum class PaymentStatus {
    PAID,
    CASH_ON_BOARD,
}

enum class PaymentMethod(val paymentStatus: PaymentStatus) {
    GCASH(PaymentStatus.PAID),
    QR_PH(PaymentStatus.PAID),
    CARD(PaymentStatus.PAID),
    CASH_ON_BOARD(PaymentStatus.CASH_ON_BOARD),
}
