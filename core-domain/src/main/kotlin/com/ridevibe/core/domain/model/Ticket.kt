package com.ridevibe.core.domain.model

data class Ticket(
    val id: String,
    val trip: Trip,
    val seatLabel: String,
    val passenger: Passenger,
    val paymentStatus: PaymentStatus,
    val qrPayload: String, // signed token encoded into the boarding QR
)

enum class PaymentStatus {
    PAID,
    CASH_ON_BOARD,
}
