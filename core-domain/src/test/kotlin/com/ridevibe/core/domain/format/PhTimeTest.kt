package com.ridevibe.core.domain.format

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhTimeTest {

    // 2026-09-24T17:30:00Z — still the 24th in UTC, already 01:30 on the 25th in Manila (UTC+8).
    private val lateUtc24th = 1_790_271_000_000L

    // 2026-09-24T15:59:59Z — 23:59:59 on the 24th in Manila.
    private val justBeforePhMidnight = lateUtc24th - 90 * 60_000L - 1_000L

    @Test
    fun `isoDate follows the Manila calendar not UTC`() {
        assertEquals("2026-09-25", PhTime.isoDate(lateUtc24th))
        assertEquals("2026-09-24", PhTime.isoDate(justBeforePhMidnight))
    }

    @Test
    fun `startOfDay is Manila midnight`() {
        val phMidnight25th = PhTime.startOfDay(lateUtc24th)
        assertEquals(PhTime.at("2026-09-25", hour = 0), phMidnight25th)
        assertEquals(lateUtc24th - 90 * 60_000L, phMidnight25th)
        assertEquals(phMidnight25th, PhTime.startOfDay(phMidnight25th))
    }

    @Test
    fun `at and dayRange agree`() {
        val start = PhTime.at("2026-09-25", hour = 0)!!
        val range = PhTime.dayRange("2026-09-25")!!
        assertEquals(start, range.first)
        assertEquals(start + PhTime.DAY_MILLIS - 1, range.last)
        assertTrue(lateUtc24th in range)
        assertTrue(justBeforePhMidnight !in range)
    }

    @Test
    fun `malformed iso days are rejected not guessed`() {
        assertNull(PhTime.at("2026/09/25", hour = 8))
        assertNull(PhTime.dayRange("tomorrow"))
        assertTrue(!PhTime.isValidIso("26-9-5"))
        assertTrue(PhTime.isValidIso("2026-09-05"))
    }

    @Test
    fun `plusDays crosses month and year boundaries`() {
        assertEquals("2026-10-01", PhTime.plusDays("2026-09-30", 1))
        assertEquals("2025-12-31", PhTime.plusDays("2026-01-01", -1))
        assertEquals("2026-09-24", PhTime.plusDays("2026-09-24", 0))
    }

    @Test
    fun `hourOf and formatDateTime render Manila time`() {
        assertEquals(1, PhTime.hourOf(lateUtc24th))
        assertEquals("Sep 25, 1:30 AM", PhTime.formatDateTime(lateUtc24th, "MMM d, h:mm a"))
        assertEquals("Fri", PhTime.formatIsoDay("2026-09-25", "EEE"))
    }

    @Test
    fun `time of day validation is strict 24 hour`() {
        assertTrue(PhTime.isValidHm("00:00"))
        assertTrue(PhTime.isValidHm("23:59"))
        assertTrue(!PhTime.isValidHm("24:00"))
        assertTrue(!PhTime.isValidHm("7:05"))
    }
}
