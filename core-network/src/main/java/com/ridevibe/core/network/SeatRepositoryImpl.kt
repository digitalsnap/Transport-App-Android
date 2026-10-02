package com.ridevibe.core.network

import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.repository.SeatRepository
import com.ridevibe.core.network.api.CrsApiService
import com.ridevibe.core.network.api.passengerApiResult
import com.ridevibe.core.network.auth.CurrentUserId
import com.ridevibe.core.network.dto.SeatLockResponseDto
import com.ridevibe.core.network.log.RideVibeLog
import com.ridevibe.core.network.socket.SeatInventorySocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SeatRepositoryImpl @Inject constructor(
    private val apiService: CrsApiService,
    private val socket: SeatInventorySocket,
    private val currentUserId: CurrentUserId,
    private val json: Json,
    @Named("wsBaseUrl") private val wsBaseUrl: String,
) : SeatRepository {

    // The server only ever sends LOCKED; SELECTED is the app's own display state.
    // Deciding "locked by me" here, in the data layer, means the seat map screen
    // shows its own holds correctly after a reload or a socket resync, and the
    // mock repository can make the same decision with its own user id.
    private fun Seat.asSeenByMe(): Seat =
        if (status == SeatStatus.LOCKED && lockedByUserId != null && lockedByUserId == currentUserId.value) {
            copy(status = SeatStatus.SELECTED)
        } else {
            this
        }

    private fun SeatStatusEvent.asSeenByMe(): SeatStatusEvent =
        if (status == SeatStatus.LOCKED && lockedByUserId != null && lockedByUserId == currentUserId.value) {
            copy(status = SeatStatus.SELECTED)
        } else {
            this
        }

    override suspend fun getSeatMap(tripId: String): Result<List<Seat>> =
        passengerApiResult { apiService.getSeatMap(tripId).map { it.toDomain().asSeenByMe() } }

    override fun observeSeatEvents(tripId: String): Flow<SeatStatusEvent> =
        socket.observe(tripId, wsBaseUrl).map { it.toDomain().asSeenByMe() }

    // passengerApiResult turns the CRS's `{message}` error bodies into readable
    // failures: 409 "Seat already held by another user" / "Too many active
    // holds (max 12)", 429 rate-limited, and so on — instead of Retrofit's
    // bare "HTTP 409". The 204/200 split is the server's choice: a 200 body
    // carries the hold expiry, a 204 means "assume the 10-minute TTL".
    override suspend fun lockSeat(tripId: String, seatId: String): Result<Long?> = passengerApiResult {
        val response = apiService.lockSeat(tripId, seatId)
        if (!response.isSuccessful) throw HttpException(response)
        val body = response.body()?.string()?.takeIf { it.isNotBlank() } ?: return@passengerApiResult null
        runCatching { json.decodeFromString(SeatLockResponseDto.serializer(), body).lockExpiresAtEpochMillis }
            .onFailure { RideVibeLog.d("Ignoring unreadable lock response for $seatId: ${it.message}") }
            .getOrNull()
    }

    override suspend fun releaseSeat(tripId: String, seatId: String): Result<Unit> =
        passengerApiResult { apiService.releaseSeat(tripId, seatId) }

    override suspend fun disconnect(tripId: String) {
        socket.disconnect(tripId)
    }
}
