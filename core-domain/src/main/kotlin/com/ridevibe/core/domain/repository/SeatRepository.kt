package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatusEvent
import kotlinx.coroutines.flow.Flow

/**
 * Real-time seat inventory for a trip. Backed by a WebSocket connection in the
 * data layer so seat availability reflects other passengers' actions live.
 */
interface SeatRepository {

    /** Initial seat layout snapshot fetched over REST. */
    suspend fun getSeatMap(tripId: String): List<Seat>

    /**
     * Opens (or reuses) a WebSocket subscription for [tripId] and emits every
     * `seat_status_changed` event pushed by the server until the flow is cancelled.
     */
    fun observeSeatEvents(tripId: String): Flow<SeatStatusEvent>

    /** Requests a temporary lock on [seatId] (server enforces the hold duration). */
    suspend fun lockSeat(tripId: String, seatId: String): Result<Unit>

    /** Releases a lock the current user is holding, e.g. on timeout or deselect. */
    suspend fun releaseSeat(tripId: String, seatId: String): Result<Unit>

    /** Closes the underlying WebSocket connection for [tripId]. */
    suspend fun disconnect(tripId: String)
}
