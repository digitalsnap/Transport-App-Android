package com.ridevibe.feature.admin.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.ManifestEntry
import com.ridevibe.feature.admin.ui.components.DateSelector
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.PaymentPills
import com.ridevibe.feature.admin.ui.components.Pill
import com.ridevibe.feature.admin.ui.components.PillTone
import com.ridevibe.feature.admin.ui.components.RowCard
import com.ridevibe.feature.admin.ui.components.SeatChips
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.ui.humanize
import com.ridevibe.feature.admin.viewmodel.PartnerManifestViewModel

/** Manifest: the day's bookings grouped per departure — passenger, party, seats, payment. */
@Composable
fun PartnerManifestTab(
    operatorId: Int?,
    viewModel: PartnerManifestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(operatorId) { viewModel.start(operatorId) }

    // Group by departure, in departure order (entries arrive sorted by departure then booking time).
    val groups = state.entries.groupBy { it.tripId }.values.toList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader("Manifest", "The day's bookings per departure: passenger, party size, seats, payment") }
        item { DateSelector(dateIso = state.dateIso, onPrevious = viewModel::previousDay, onNext = viewModel::nextDay) }
        state.error?.let { item { InlineError(it) } }
        when {
            state.isLoading && state.entries.isEmpty() -> item { LoadingRow() }
            state.entries.isEmpty() -> item { EmptyText("No bookings on ${state.dateIso}.") }
            else -> {
                item {
                    val passengers = state.entries.filter { it.status == BookingStatus.CONFIRMED }.sumOf { it.partySize }
                    HintText("${state.entries.size} booking${if (state.entries.size == 1) "" else "s"} · $passengers passengers boarding")
                }
                groups.forEach { entries ->
                    val first = entries.first()
                    item(key = "trip-${first.tripId}") {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            MicroLabel("${formatPhDateTime(first.departureEpochMillis)} · ${first.origin} → ${first.destination}")
                            Text(
                                "${entries.size} booking${if (entries.size == 1) "" else "s"} · ${entries.sumOf { it.partySize }} passengers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(entries, key = { it.id }) { entry -> ManifestRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun ManifestRow(entry: ManifestEntry) {
    RowCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.passengerFullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(humanize(entry.passengerType.name))
                        append(" · party of ${entry.partySize}")
                        if (entry.infantCount > 0) append(" + ${entry.infantCount} infant${if (entry.infantCount == 1) "" else "s"}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.status == BookingStatus.CANCELLED) Pill("CANCELLED", PillTone.BAD) else Pill("CONFIRMED", PillTone.OK)
        }
        Spacer(modifier = Modifier.height(8.dp))
        SeatChips(entry.seatLabels)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PaymentPills(entry.paymentMethod, entry.paymentStatus)
        }
    }
}
