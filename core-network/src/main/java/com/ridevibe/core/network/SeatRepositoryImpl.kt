package com.ridevibe.core.network

import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.repository.SeatRepository
import com.ridevibe.core.network.api.CrsApiService
import com.ridevibe.core.network.socket.SeatInventorySocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SeatRepositoryImpl @Inject constructor(
    private val apiService: CrsApiService,
    private val socket: SeatInventorySocket,
    @Named("wsBaseUrl") private val wsBaseUrl: String,
) : SeatRepository {

    override suspend fun getSeatMap(tripId: String): List<Seat> =
        apiService.getSeatMap(tripId).map { it.toDomain() }

    override fun observeSeatEvents(tripId: String): Flow<SeatStatusEvent> =
        socket.observe(tripId, wsBaseUrl).map { it.toDomain() }

    override suspend fun lockSeat(tripId: String, seatId: String): Result<Unit> =
        runCatching { apiService.lockSeat(tripId, seatId) }

    override suspend fun releaseSeat(tripId: String, seatId: String): Result<Unit> =
        runCatching { apiService.releaseSeat(tripId, seatId) }

    override suspend fun disconnect(tripId: String) {
        socket.disconnect(tripId)
    }
}
