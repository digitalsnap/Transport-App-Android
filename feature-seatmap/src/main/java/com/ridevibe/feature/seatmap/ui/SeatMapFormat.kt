package com.ridevibe.feature.seatmap.ui

import com.ridevibe.core.domain.format.PhTime

/** "2h 05m" between two epoch instants; negative spans clamp to zero rather than showing nonsense. */
internal fun durationLabel(departMillis: Long, arriveMillis: Long): String {
    val totalMinutes = ((arriveMillis - departMillis) / 60_000).coerceAtLeast(0)
    return "${totalMinutes / 60}h %02dm".format(totalMinutes % 60)
}

/** "6:30 PM, Sep 26" in Philippine time, whatever zone the phone is set to. */
internal fun formatDeparture(epochMillis: Long): String = PhTime.formatDateTime(epochMillis, "h:mm a, MMM d")

/** "9:05" style countdown for the hold clock. */
internal fun formatCountdown(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

/** "2 seats · 1 infant on lap" — what the party needs on this trip. */
internal fun partySummary(seatCount: Int, infants: Int): String {
    val seats = "$seatCount seat${if (seatCount == 1) "" else "s"}"
    if (infants == 0) return seats
    return "$seats · $infants infant${if (infants == 1) "" else "s"} on lap"
}
