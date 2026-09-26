package com.ridevibe.feature.search.ui

import com.ridevibe.core.domain.format.PhTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Every date the rider sees is Asia/Manila, whatever the device zone (PhTime).

internal fun formatDate(epochMillis: Long): String = PhTime.formatDateTime(epochMillis, "EEE, d MMM")

internal fun formatTime(epochMillis: Long): String = PhTime.formatDateTime(epochMillis, "hh:mm a")

internal fun durationLabel(departMillis: Long, arriveMillis: Long): String {
    val totalMinutes = ((arriveMillis - departMillis) / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h %02dm".format(minutes)
}

// The Material 3 date picker works in UTC-midnight millis. The form stores PH
// start-of-day millis (16:00 UTC the previous day), so feeding those straight in
// would show the wrong calendar date. Bridge through the ISO day string instead.

private val utcDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

/** UTC midnight of the PH calendar day containing [phDayMillis]. */
internal fun phDayToPickerMillis(phDayMillis: Long): Long =
    utcDay.parse(PhTime.isoDate(phDayMillis))?.time ?: phDayMillis

/** PH start-of-day for the calendar date the picker reports as UTC midnight. */
internal fun pickerMillisToPhDay(utcMidnightMillis: Long): Long =
    PhTime.at(utcDay.format(Date(utcMidnightMillis)), hour = 0) ?: PhTime.startOfDay(utcMidnightMillis)
