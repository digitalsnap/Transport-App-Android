package com.ridevibe.feature.admin.ui

import com.ridevibe.core.domain.format.PhTime
import java.util.Locale

/**
 * Formatting for the staff console. Money and calendar rules live in
 * core-domain (`formatPhp`, `PhTime`) so the console agrees with the rider
 * app and the web dashboards; what remains here is dashboard-only presentation
 * (em dashes for missing values, truncated ids, hour lists).
 */

/** `₱1,234.00`; null renders as an em dash like the dashboards do. */
fun formatPhp(amount: Double?): String =
    if (amount == null) "—" else com.ridevibe.core.domain.format.formatPhp(amount)

/** `Sep 3, 4:30 PM` in Asia/Manila; null renders as an em dash. */
fun formatPhDateTime(epochMillis: Long?): String =
    if (epochMillis == null) "—" else PhTime.formatDateTime(epochMillis, "MMM d, h:mm a")

/** `4:30 PM` in Asia/Manila — for rows that already say which day. */
fun formatPhTime(epochMillis: Long): String = PhTime.formatDateTime(epochMillis, "h:mm a")

/** `Wed, Sep 3 2026` for date-selector captions. */
fun formatIsoDayLong(isoDay: String): String = PhTime.formatIsoDay(isoDay, "EEE, MMM d yyyy")

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

fun formatRating(rating: Double?): String =
    if (rating == null) "—" else String.format(Locale.US, "★ %.1f", rating)

fun formatFareRange(min: Double?, max: Double?): String =
    if (min == null || max == null) "—" else "${formatPhp(min)} – ${formatPhp(max)}"
