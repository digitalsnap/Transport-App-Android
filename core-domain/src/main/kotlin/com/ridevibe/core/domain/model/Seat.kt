package com.ridevibe.core.domain.model

/**
 * A single seat within a trip's inventory.
 */
data class Seat(
    val id: String,
    val label: String, // e.g. "12A"
    val row: Int,
    val column: Int,
    val status: SeatStatus,
    val lockedByUserId: String? = null,
)

enum class SeatStatus {
    AVAILABLE,
    LOCKED, // held by another passenger (or this one, pending payment)
    OCCUPIED, // paid / confirmed
    SELECTED, // held by the current user, not yet confirmed
}

/**
 * Server-pushed event describing a change in a seat's status.
 */
data class SeatStatusEvent(
    val tripId: String,
    val seatId: String,
    val status: SeatStatus,
    val lockedByUserId: String? = null,
    val lockExpiresAtEpochMillis: Long? = null,
)
