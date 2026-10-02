package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatusEvent
import kotlinx.serialization.Serializable

@Serializable
data class SeatDto(
    val id: String,
    val label: String,
    val row: Int,
    val column: Int,
    val status: String,
    val lockedByUserId: String? = null,
    /** Present on a held seat when the server tracks hold expiry (see openapi.yaml `Seat`). */
    val lockExpiresAtEpochMillis: Long? = null,
) {
    // Unknown status → OCCUPIED: a seat we cannot read must never be sold twice.
    fun toDomain() = Seat(
        id = id,
        label = label,
        row = row,
        column = column,
        status = status.toSeatStatusLenient(),
        lockedByUserId = lockedByUserId,
        lockExpiresAtEpochMillis = lockExpiresAtEpochMillis,
    )
}

/**
 * Payload of the `seat_status_changed` WebSocket event pushed by the CRS
 * whenever any passenger's action changes a seat's lock/occupancy state.
 */
@Serializable
data class SeatStatusEventDto(
    val type: String = "seat_status_changed",
    val tripId: String,
    val seatId: String,
    val status: String,
    val lockedByUserId: String? = null,
    val lockExpiresAtEpochMillis: Long? = null,
) {
    fun toDomain() = SeatStatusEvent(
        tripId = tripId,
        seatId = seatId,
        status = status.toSeatStatusLenient(),
        lockedByUserId = lockedByUserId,
        lockExpiresAtEpochMillis = lockExpiresAtEpochMillis,
    )
}

/**
 * Optional body of `POST /v1/trips/{tripId}/seats/{seatId}/lock` (200). Every
 * field is optional: a 204 with no body is equally valid and means "held,
 * assume the documented 10-minute TTL".
 */
@Serializable
data class SeatLockResponseDto(
    val seatId: String? = null,
    val lockedByUserId: String? = null,
    val lockExpiresAtEpochMillis: Long? = null,
)
