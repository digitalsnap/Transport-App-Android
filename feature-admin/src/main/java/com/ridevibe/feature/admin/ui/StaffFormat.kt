package com.ridevibe.feature.admin.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formatting for the staff console. Everything is reported in Philippine time,
 * exactly like the web dashboards (`toLocaleString("en-PH", { timeZone: "Asia/Manila" })`).
 * minSdk 24 without desugaring: `java.util.Calendar` / `SimpleDateFormat` only.
 */
val PhTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Manila")

private const val ISO_DAY_PATTERN = "yyyy-MM-dd"

private fun phFormat(pattern: String): SimpleDateFormat =
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = PhTimeZone }

/** `₱1,234.00`; null renders as an em dash like the dashboards do. */
fun formatPhp(amount: Double?): String =
    if (amount == null) "—" else String.format(Locale.US, "₱%,.2f", amount)

/** `Sep 3, 4:30 PM` in Asia/Manila; null renders as an em dash. */
fun formatPhDateTime(epochMillis: Long?): String =
    if (epochMillis == null) "—" else phFormat("MMM d, h:mm a").format(Date(epochMillis))

/** `yyyy-MM-dd` for the PH calendar day containing [epochMillis]. */
fun formatPhDate(epochMillis: Long): String = phFormat(ISO_DAY_PATTERN).format(Date(epochMillis))

/** Today's PH calendar date as `yyyy-MM-dd` — the default for every date selector. */
fun todayPhIso(): String = formatPhDate(System.currentTimeMillis())

/** Adds [days] (negative allowed) to an ISO day using PH calendar arithmetic. */
fun shiftIsoDay(isoDay: String, days: Int): String {
    val calendar = isoDayToCalendar(isoDay)
    calendar.add(Calendar.DAY_OF_MONTH, days)
    return phFormat(ISO_DAY_PATTERN).format(calendar.time)
}

/** `Mon`, `Tue`, … for the chart axis. */
fun weekdayLabel(isoDay: String): String = phFormat("EEE").format(isoDayToCalendar(isoDay).time)

/** `Wed, Sep 3 2026` for date-selector captions. */
fun formatIsoDayLong(isoDay: String): String = phFormat("EEE, MMM d yyyy").format(isoDayToCalendar(isoDay).time)

fun isTodayPh(isoDay: String): Boolean = isoDay == todayPhIso()

private fun isoDayToCalendar(isoDay: String): Calendar {
    val calendar = Calendar.getInstance(PhTimeZone, Locale.US)
    val parts = isoDay.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size == 3 && parts[1] in 1..12 && parts[2] in 1..31) {
        calendar.clear()
        calendar.set(parts[0], parts[1] - 1, parts[2])
    }
    return calendar
}

/** `5h30m` — matches the dashboards' `Math.floor(m/60)h + mm`. */
fun formatDuration(minutes: Int): String =
    String.format(Locale.US, "%dh%02dm", minutes / 60, minutes % 60)

fun formatDuration(minutes: Long): String = formatDuration(minutes.toInt())

/** `07:00` for a departure hour. */
fun formatHour(hour: Int): String = String.format(Locale.US, "%02d:00", hour)

fun formatHours(hours: List<Int>): String = hours.sorted().joinToString(" ") { formatHour(it) }

/** Thousands-grouped count for stat tiles. */
fun formatCount(count: Int): String = String.format(Locale.US, "%,d", count)

/** `a1b2c3d4e5f6…` — the dashboards truncate ids to keep rows narrow. */
fun shortId(id: String, keep: Int = 12): String = if (id.length <= keep) id else id.take(keep) + "…"

/** `SENIOR_CITIZEN` → `Senior citizen`. */
fun humanize(enumName: String): String =
    enumName.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }

/** `HH:mm`, 24-hour, as `addExtraTrip` requires. */
fun isValidTimeHm(value: String): Boolean = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(value)

fun formatRating(rating: Double?): String =
    if (rating == null) "—" else String.format(Locale.US, "★ %.1f", rating)

fun formatFareRange(min: Double?, max: Double?): String =
    if (min == null || max == null) "—" else "${formatPhp(min)} – ${formatPhp(max)}"
