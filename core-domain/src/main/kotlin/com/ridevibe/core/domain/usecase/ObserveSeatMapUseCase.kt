package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.repository.SeatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Supplies the initial seat map snapshot and the live event stream separately —
 * the ViewModel folds events onto the snapshot with `scan` so it owns the
 * merge point where the 10-minute hold timer also reacts to lock events.
 */
class ObserveSeatMapUseCase @Inject constructor(
    private val seatRepository: SeatRepository,
) {
    suspend fun getInitialSeatMap(tripId: String): List<Seat> =
        seatRepository.getSeatMap(tripId)

    fun observeEvents(tripId: String): Flow<SeatStatusEvent> =
        seatRepository.observeSeatEvents(tripId)
}

fun applySeatEvent(seats: List<Seat>, event: SeatStatusEvent): List<Seat> =
    seats.map { seat ->
        if (seat.id == event.seatId) {
            seat.copy(status = event.status, lockedByUserId = event.lockedByUserId)
        } else {
            seat
        }
    }
