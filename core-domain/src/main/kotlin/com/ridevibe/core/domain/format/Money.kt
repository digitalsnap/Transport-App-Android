package com.ridevibe.core.domain.format

import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Philippine English number formatting: `en-PH` groups thousands with commas and uses a dot decimal. */
private val PH_LOCALE = Locale("en", "PH")

/** Peso sign, U+20B1 — never the plain "P" some fonts fall back to. */
const val PESO_SIGN = "₱"

/**
 * `₱1,234.00` (or `₱1,234` with [showCentavos] false). Negative amounts keep the
 * sign in front of the peso sign (`-₱50.00`) so refunds read naturally.
 */
fun formatPhp(amount: Double, showCentavos: Boolean = true): String {
    val format = NumberFormat.getNumberInstance(PH_LOCALE).apply {
        minimumFractionDigits = if (showCentavos) 2 else 0
        maximumFractionDigits = if (showCentavos) 2 else 0
        isGroupingUsed = true
        roundingMode = RoundingMode.HALF_UP // same rule as FareCalculator.roundCentavos
    }
    val magnitude = format.format(Math.abs(amount))
    return if (amount < 0) "-$PESO_SIGN$magnitude" else "$PESO_SIGN$magnitude"
}

/**
 * Philippine calendar arithmetic. Everything the app schedules, files or
 * reports is in Asia/Manila (UTC+8, no DST) regardless of the device zone —
 * a departure "on the 3rd" means the PH 3rd, and the staff console must agree
 * with the web dashboards' `toLocaleString("en-PH", { timeZone: "Asia/Manila" })`.
 *
 * Built on `java.util.Calendar` / `SimpleDateFormat`, not `java.time`: the app
 * runs on minSdk 24 without core-library desugaring, where `java.time` is
 * missing at runtime.
 */
object PhTime {
    val zone: TimeZone = TimeZone.getTimeZone("Asia/Manila")

    const val DAY_MILLIS: Long = 86_400_000L

    private const val ISO_DAY_PATTERN = "yyyy-MM-dd"
    private val isoDayRegex = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val hourMinuteRegex = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

    /** `yyyy-MM-dd` of the PH calendar day containing [epochMillis]. */
    fun isoDate(epochMillis: Long): String = formatDateTime(epochMillis, ISO_DAY_PATTERN)

    /** Midnight (PH) that starts the calendar day containing [epochMillis]. */
    fun startOfDay(epochMillis: Long): Long {
        val calendar = calendarAt(epochMillis)
        calendar[Calendar.HOUR_OF_DAY] = 0
        calendar[Calendar.MINUTE] = 0
        calendar[Calendar.SECOND] = 0
        calendar[Calendar.MILLISECOND] = 0
        return calendar.timeInMillis
    }

    fun todayStartMillis(nowEpochMillis: Long = System.currentTimeMillis()): Long = startOfDay(nowEpochMillis)

    /** Today's PH calendar day as `yyyy-MM-dd`. */
    fun todayIso(nowEpochMillis: Long = System.currentTimeMillis()): String = isoDate(nowEpochMillis)

    /** [epochMillis] rendered with a `SimpleDateFormat` [pattern] in PH time, US-English names. */
    fun formatDateTime(epochMillis: Long, pattern: String): String =
        phFormat(pattern).format(Date(epochMillis))

    /** An ISO day rendered with [pattern] (`EEE` → `Mon`, `EEE, MMM d yyyy` → `Wed, Sep 3 2026`). */
    fun formatIsoDay(dateIso: String, pattern: String): String =
        phFormat(pattern).format(calendarFor(dateIso).time)

    /** Hour of day (0–23) in PH time. */
    fun hourOf(epochMillis: Long): Int = calendarAt(epochMillis)[Calendar.HOUR_OF_DAY]

    fun isValidIso(dateIso: String): Boolean = isoDayRegex.matches(dateIso)

    /** `HH:mm`, 24-hour. */
    fun isValidHm(time: String): Boolean = hourMinuteRegex.matches(time)

    /** Epoch millis of [dateIso] at [hour]:[minute] PH time, or null when [dateIso] is malformed. */
    fun at(dateIso: String, hour: Int, minute: Int = 0): Long? {
        val match = isoDayRegex.matchEntire(dateIso) ?: return null
        val (year, month, day) = match.destructured
        val calendar = Calendar.getInstance(zone, Locale.US)
        calendar.clear()
        calendar.set(year.toInt(), month.toInt() - 1, day.toInt(), hour, minute, 0)
        return calendar.timeInMillis
    }

    /** The PH calendar day [dateIso] as a half-open epoch range, or null when malformed. */
    fun dayRange(dateIso: String): LongRange? {
        val start = at(dateIso, hour = 0) ?: return null
        return start until start + DAY_MILLIS
    }

    /** Adds [days] (negative allowed) to an ISO day using PH calendar arithmetic. */
    fun plusDays(dateIso: String, days: Int): String {
        val calendar = calendarFor(dateIso)
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return phFormat(ISO_DAY_PATTERN).format(calendar.time)
    }

    private fun phFormat(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }

    private fun calendarAt(epochMillis: Long): Calendar =
        Calendar.getInstance(zone, Locale.US).apply { timeInMillis = epochMillis }

    /** Noon on [dateIso] (safe from any day-boundary drift); "now" when malformed. */
    private fun calendarFor(dateIso: String): Calendar {
        val calendar = Calendar.getInstance(zone, Locale.US)
        at(dateIso, hour = 12)?.let { calendar.timeInMillis = it }
        return calendar
    }
}
