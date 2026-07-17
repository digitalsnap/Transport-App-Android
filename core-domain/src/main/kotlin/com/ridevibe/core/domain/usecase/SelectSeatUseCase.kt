package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.repository.SeatRepository
import javax.inject.Inject

/** Requests a hold on a seat; the server is the source of truth for the lock duration. */
class SelectSeatUseCase @Inject constructor(
    private val seatRepository: SeatRepository,
) {
    suspend operator fun invoke(tripId: String, seatId: String): Result<Unit> =
        seatRepository.lockSeat(tripId, seatId)
}

/** Releases a previously held seat, e.g. on deselect or hold-timer expiry. */
class ReleaseSeatUseCase @Inject constructor(
    private val seatRepository: SeatRepository,
) {
    suspend operator fun invoke(tripId: String, seatId: String): Result<Unit> =
        seatRepository.releaseSeat(tripId, seatId)
}
