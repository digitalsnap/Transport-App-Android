package com.ridevibe.feature.admin.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.ridevibe.core.domain.format.PhTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
 * Material 3 date and time pickers for the staff forms. The console works in
 * PH calendar days (`yyyy-MM-dd`) and `HH:mm` strings because that is what the
 * staff API takes; the pickers themselves speak UTC-midnight millis, so the
 * conversion is isolated here and nothing else touches it.
 */

private val utcDayFormat: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

/** UTC midnight of an ISO day — what `DatePickerState.selectedDateMillis` expects. Null when malformed. */
private fun isoDayToUtcMillis(isoDay: String): Long? =
    if (PhTime.isValidIso(isoDay)) runCatching { utcDayFormat.parse(isoDay)?.time }.getOrNull() else null

private fun utcMillisToIsoDay(utcMillis: Long): String = utcDayFormat.format(Date(utcMillis))

/** Calendar dialog seeded with [initialIsoDay]; [onPick] receives `yyyy-MM-dd`. */
@Composable
fun StaffDatePickerDialog(
    initialIsoDay: String,
    onPick: (isoDay: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = isoDayToUtcMillis(initialIsoDay))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { state.selectedDateMillis?.let { onPick(utcMillisToIsoDay(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("Use date", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

/**
 * 24-hour clock dialog; [onPick] receives `HH:mm`. Material 3 ships a
 * TimePicker but no dialog for it, hence the AlertDialog wrapper.
 */
@Composable
fun StaffTimePickerDialog(
    initialHm: String,
    onPick: (timeHm: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val parts = if (PhTime.isValidHm(initialHm)) initialHm.split(":").map { it.toInt() } else listOf(8, 0)
    val state = rememberTimePickerState(initialHour = parts[0], initialMinute = parts[1], is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Departure time (PH)", fontWeight = FontWeight.Bold) },
        text = { TimePicker(state = state) },
        confirmButton = {
            Button(onClick = { onPick(String.format(Locale.US, "%02d:%02d", state.hour, state.minute)) }) {
                Text("Use time", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
