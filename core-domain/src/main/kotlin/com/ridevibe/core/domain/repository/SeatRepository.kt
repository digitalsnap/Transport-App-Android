package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatusEvent
import kotlinx.coroutines.flow.Flow

/**
 * Real-time seat inventory for a trip. Backed by a WebSocket connection in the
 * data layer so seat availability reflects other passengers' actions live.
 */
interface SeatRepository {

    /** Initial seat layout snapshot fetched over REST. Empty for sea passage (no seat map). */
    suspend fun getSeatMap(tripId: String): Result<List<Seat>>

    /**
     * Opens (or reuses) a WebSocket subscription for [tripId] and emits every
     * `seat_status_changed` event pushed by the server until the flow is cancelled.
     */
    fun observeSeatEvents(tripId: String): Flow<SeatStatusEvent>

    /**
     * Requests a temporary lock on [seatId]. The server enforces the hold
     * duration; the value is the hold's expiry (epoch millis) when the server
     * reported one, null when it sent no body — the UI then assumes the
     * documented 10-minute TTL.
     */
    suspend fun lockSeat(tripId: String, seatId: String): Result<Long?>

    /** Releases a lock the current user is holding, e.g. on timeout or deselect. */
    suspend fun releaseSeat(tripId: String, seatId: String): Result<Unit>

    /** Closes the underlying WebSocket connection for [tripId]. */
    suspend fun disconnect(tripId: String)
}
