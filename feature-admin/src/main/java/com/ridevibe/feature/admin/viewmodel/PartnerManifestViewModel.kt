package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.ManifestEntry
import com.ridevibe.core.domain.repository.PartnerRepository
import com.ridevibe.feature.admin.manifest.ManifestCheckInStore
import com.ridevibe.feature.admin.manifest.RiderQrPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One manifest row with its device-local boarding mark. */
data class ManifestRow(
    val entry: ManifestEntry,
    val boardedAtEpochMillis: Long?,
) {
    val isBoarded: Boolean get() = boardedAtEpochMillis != null
}

/** One departure's block on the manifest, in departure order. */
data class ManifestDeparture(
    val tripId: String,
    val departureEpochMillis: Long,
    val origin: String,
    val destination: String,
    val rows: List<ManifestRow>,
) {
    val confirmedRows: List<ManifestRow> get() = rows.filter { it.entry.status == BookingStatus.CONFIRMED }
    val passengers: Int get() = confirmedRows.sumOf { it.entry.partySize }
    val boardedCount: Int get() = confirmedRows.count { it.isBoarded }
    val label: String get() = "${PhTime.formatDateTime(departureEpochMillis, "h:mm a")} · $origin → $destination"
}

/** What a scan (or a manual mark) produced; shown once on the scanner and cleared. */
sealed interface CheckInOutcome {
    data class Boarded(val passengerName: String, val seatLabels: List<String>) : CheckInOutcome
    data class AlreadyBoarded(val passengerName: String, val boardedAtEpochMillis: Long) : CheckInOutcome
    data class Cancelled(val passengerName: String) : CheckInOutcome
    data class NotOnManifest(val reason: String) : CheckInOutcome
}

data class PartnerManifestUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val dateIso: String = PhTime.todayIso(),
    /** The day's entries as the server returned them. */
    val entries: List<ManifestEntry> = emptyList(),
    /** Ticket id → boarded-at, from the device-local check-in store. */
    val boarded: Map<String, Long> = emptyMap(),
    val query: String = "",
    /** Trip id to show alone, or null for every departure. */
    val departureFilter: String? = null,
    val hideCancelled: Boolean = false,
    val showScanner: Boolean = false,
    val lastOutcome: CheckInOutcome? = null,
    /** Row awaiting the "Mark boarded" confirmation. */
    val pendingManual: ManifestEntry? = null,
) {
    /** Departure choices for the filter, in departure order. */
    val departureOptions: List<ManifestDeparture>
        get() = group(entries)

    /** The blocks actually rendered: search, departure filter and cancelled toggle applied. */
    val departures: List<ManifestDeparture>
        get() {
            val needle = query.trim().lowercase()
            val visible = entries.filter { entry ->
                (departureFilter == null || entry.tripId == departureFilter) &&
                    (!hideCancelled || entry.status != BookingStatus.CANCELLED) &&
                    (needle.isEmpty() || entry.passengerFullName.lowercase().contains(needle) || entry.id.lowercase().contains(needle))
            }
            return group(visible)
        }

    val totalBookings: Int get() = entries.size
    val totalPassengers: Int get() = entries.filter { it.status == BookingStatus.CONFIRMED }.sumOf { it.partySize }
    val totalConfirmed: Int get() = entries.count { it.status == BookingStatus.CONFIRMED }
    val totalBoarded: Int get() = entries.count { it.status == BookingStatus.CONFIRMED && it.id in boarded }

    private fun group(rows: List<ManifestEntry>): List<ManifestDeparture> =
        rows.groupBy { it.tripId }.values
            .map { trip ->
                val first = trip.first()
                ManifestDeparture(
                    tripId = first.tripId,
                    departureEpochMillis = first.departureEpochMillis,
                    origin = first.origin,
                    destination = first.destination,
                    rows = trip.map { ManifestRow(it, boarded[it.id]) },
                )
            }
            .sortedBy { it.departureEpochMillis }
}

/**
 * Boarding manifest: the day's bookings across the operator's departures, with
 * QR check-in. Boarded marks live in [ManifestCheckInStore] on this device
 * only until the backend grows a check-in endpoint.
 */
@HiltViewModel
class PartnerManifestViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val checkInStore: ManifestCheckInStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerManifestUiState())
    val uiState: StateFlow<PartnerManifestUiState> = _uiState.asStateFlow()

    private var operatorId: Int? = null
    private var sessionKey: String? = null

    fun start(sessionKey: String, operatorId: Int?) {
        if (this.sessionKey == sessionKey && this.operatorId == operatorId) return
        this.sessionKey = sessionKey
        this.operatorId = operatorId
        _uiState.value = PartnerManifestUiState()
        load()
    }

    fun previousDay() = changeDay(-1)

    fun nextDay() = changeDay(+1)

    fun setDate(isoDay: String) {
        if (!PhTime.isValidIso(isoDay)) return
        _uiState.update { it.copy(dateIso = isoDay, departureFilter = null) }
        load()
    }

    private fun changeDay(delta: Int) {
        _uiState.update { it.copy(dateIso = PhTime.plusDays(it.dateIso, delta), departureFilter = null) }
        load()
    }

    fun load() {
        val dateIso = _uiState.value.dateIso
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            partnerRepository.getManifest(dateIso = dateIso, operatorId = operatorId)
                .onSuccess { entries ->
                    val boarded = checkInStore.boardedFor(entries.map { it.tripId }).values.fold(emptyMap<String, Long>()) { acc, m -> acc + m }
                    _uiState.update { it.copy(isLoading = false, entries = entries, boarded = boarded) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.staffMessage("Unable to load the manifest")) }
                }
        }
    }

    fun onQueryChanged(value: String) = _uiState.update { it.copy(query = value) }

    fun onDepartureFilterChanged(tripId: String?) = _uiState.update { it.copy(departureFilter = tripId) }

    fun onHideCancelledChanged(hide: Boolean) = _uiState.update { it.copy(hideCancelled = hide) }

    fun openScanner() = _uiState.update { it.copy(showScanner = true, lastOutcome = null) }

    fun closeScanner() = _uiState.update { it.copy(showScanner = false, lastOutcome = null) }

    fun dismissOutcome() = _uiState.update { it.copy(lastOutcome = null) }

    /** A QR was decoded by the camera: match it against the loaded manifest and record the boarding. */
    fun onScanned(rawPayload: String) {
        val payload = RiderQrPayload.parse(rawPayload)
        if (payload == null) {
            _uiState.update { it.copy(lastOutcome = CheckInOutcome.NotOnManifest("Not a RideVibe ticket")) }
            return
        }
        val entries = _uiState.value.entries
        val byTicket = entries.filter { it.id == payload.ticketId }
        val outcome = when {
            byTicket.isEmpty() -> CheckInOutcome.NotOnManifest(
                if (payload.tripId != null && entries.none { it.tripId == payload.tripId }) {
                    "Wrong trip — this ticket is for a departure not on today's manifest"
                } else {
                    "Not on this manifest"
                },
            )
            payload.tripId != null && byTicket.none { it.tripId == payload.tripId } ->
                CheckInOutcome.NotOnManifest("Wrong trip — ticket ${payload.ticketId} belongs to another departure")
            else -> checkIn(byTicket.first { payload.tripId == null || it.tripId == payload.tripId })
        }
        _uiState.update { it.copy(lastOutcome = outcome) }
    }

    /** "Mark boarded" on a row, step one: ask. */
    fun requestManualBoarding(entry: ManifestEntry) = _uiState.update { it.copy(pendingManual = entry) }

    fun cancelManualBoarding() = _uiState.update { it.copy(pendingManual = null) }

    /** "Mark boarded" on a row, step two: record it, same rules as a scan. */
    fun confirmManualBoarding() {
        val entry = _uiState.value.pendingManual ?: return
        val outcome = checkIn(entry)
        _uiState.update { it.copy(pendingManual = null, lastOutcome = outcome) }
    }

    private fun checkIn(entry: ManifestEntry): CheckInOutcome {
        if (entry.status != BookingStatus.CONFIRMED) return CheckInOutcome.Cancelled(entry.passengerFullName)
        val already = _uiState.value.boarded[entry.id]
        if (already != null) return CheckInOutcome.AlreadyBoarded(entry.passengerFullName, already)
        val now = System.currentTimeMillis()
        if (!checkInStore.markBoarded(entry.tripId, entry.id, now)) {
            // Another screen on this device marked it between our read and now; reflect the stored time.
            val stored = checkInStore.boardedFor(entry.tripId)[entry.id] ?: now
            _uiState.update { it.copy(boarded = it.boarded + (entry.id to stored)) }
            return CheckInOutcome.AlreadyBoarded(entry.passengerFullName, stored)
        }
        _uiState.update { it.copy(boarded = it.boarded + (entry.id to now)) }
        return CheckInOutcome.Boarded(entry.passengerFullName, entry.seatLabels)
    }

    /** Plain-text manifest for ACTION_SEND: one line per booking, grouped by departure. */
    fun shareText(): String {
        val state = _uiState.value
        return buildString {
            append("RideVibe manifest — ").append(PhTime.formatIsoDay(state.dateIso, "EEE, MMM d yyyy")).append('\n')
            append("Boarded ").append(state.totalBoarded).append(" / ").append(state.totalConfirmed)
            append(" bookings · ").append(state.totalPassengers).append(" passengers\n")
            state.departures.forEach { departure ->
                append('\n').append(departure.label).append('\n')
                departure.rows.forEach { row ->
                    val entry = row.entry
                    append(entry.seatLabels.joinToString("+").ifBlank { "—" }).append(" | ")
                    append(entry.passengerFullName).append(" | party of ").append(entry.partySize)
                    if (entry.infantCount > 0) append(" + ").append(entry.infantCount).append(" infant(s)")
                    append(" | ").append(entry.paymentMethod.name).append(' ').append(entry.paymentStatus.name)
                    append(" | ")
                    append(
                        when {
                            entry.status != BookingStatus.CONFIRMED -> entry.status.name
                            row.isBoarded -> "BOARDED " + PhTime.formatDateTime(row.boardedAtEpochMillis ?: 0L, "h:mm a")
                            else -> "not boarded"
                        },
                    )
                    append(" | ").append(entry.id).append('\n')
                }
            }
        }
    }
}
