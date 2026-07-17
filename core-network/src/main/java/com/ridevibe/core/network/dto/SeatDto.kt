package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
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
) {
    fun toDomain() = Seat(
        id = id,
        label = label,
        row = row,
        column = column,
        status = status.toSeatStatus(),
        lockedByUserId = lockedByUserId,
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
        status = status.toSeatStatus(),
        lockedByUserId = lockedByUserId,
        lockExpiresAtEpochMillis = lockExpiresAtEpochMillis,
    )
}

private fun String.toSeatStatus(): SeatStatus = when (uppercase()) {
    "LOCKED" -> SeatStatus.LOCKED
    "OCCUPIED" -> SeatStatus.OCCUPIED
    "SELECTED" -> SeatStatus.SELECTED
    else -> SeatStatus.AVAILABLE
}
