package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.SeatStatusEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApplySeatEventTest {

    private val seats = listOf(
        Seat(id = "1A", label = "1A", row = 1, column = 1, status = SeatStatus.AVAILABLE),
        Seat(id = "1B", label = "1B", row = 1, column = 2, status = SeatStatus.AVAILABLE),
    )

    @Test
    fun `lock event copies holder and expiry onto the matching seat only`() {
        val event = SeatStatusEvent(
            tripId = "T1",
            seatId = "1A",
            status = SeatStatus.LOCKED,
            lockedByUserId = "user-9",
            lockExpiresAtEpochMillis = 1_700_000_000_000L,
        )

        val result = applySeatEvent(seats, event)

        assertEquals(SeatStatus.LOCKED, result[0].status)
        assertEquals("user-9", result[0].lockedByUserId)
        assertEquals(1_700_000_000_000L, result[0].lockExpiresAtEpochMillis)
        assertEquals(seats[1], result[1])
    }

    @Test
    fun `release event clears the expiry even when the payload still carries one`() {
        val held = seats.map { it.copy(status = SeatStatus.LOCKED, lockedByUserId = "u", lockExpiresAtEpochMillis = 5L) }
        val event = SeatStatusEvent(
            tripId = "T1",
            seatId = "1A",
            status = SeatStatus.AVAILABLE,
            lockedByUserId = null,
            lockExpiresAtEpochMillis = 5L,
        )

        val result = applySeatEvent(held, event)

        assertEquals(SeatStatus.AVAILABLE, result[0].status)
        assertNull(result[0].lockedByUserId)
        assertNull(result[0].lockExpiresAtEpochMillis)
    }

    @Test
    fun `sold event clears the expiry`() {
        val held = seats.map { it.copy(status = SeatStatus.LOCKED, lockExpiresAtEpochMillis = 5L) }
        val event = SeatStatusEvent(tripId = "T1", seatId = "1B", status = SeatStatus.OCCUPIED, lockExpiresAtEpochMillis = 9L)

        val result = applySeatEvent(held, event)

        assertEquals(SeatStatus.OCCUPIED, result[1].status)
        assertNull(result[1].lockExpiresAtEpochMillis)
        assertEquals(5L, result[0].lockExpiresAtEpochMillis) // untouched seat keeps its hold
    }

    @Test
    fun `unknown seat id leaves the list unchanged`() {
        val event = SeatStatusEvent(tripId = "T1", seatId = "9Z", status = SeatStatus.LOCKED)
        assertEquals(seats, applySeatEvent(seats, event))
    }
}
