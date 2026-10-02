package com.ridevibe.feature.admin.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.feature.admin.ui.components.BookingRow
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.ErrorWithRetry
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.NoteCard
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.StaffDatePickerDialog
import com.ridevibe.feature.admin.ui.humanize
import com.ridevibe.feature.admin.viewmodel.AdminSupportViewModel
import com.ridevibe.feature.admin.viewmodel.SUPPORT_PAGE_SIZE
import com.ridevibe.feature.admin.viewmodel.SupportFilterPreset

/**
 * Customer support: find a booking, cancel it, record refunds, reissue QR
 * tickets. [initialFilter] arrives from an Overview tile and is applied once,
 * then [onInitialFilterApplied] clears it so re-opening the tab keeps whatever
 * the admin changed afterwards.
 */
@Composable
fun AdminSupportTab(
    sessionKey: String,
    onMessage: (String) -> Unit,
    initialFilter: SupportFilterPreset? = null,
    onInitialFilterApplied: () -> Unit = {},
    viewModel: AdminSupportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingFrom by rememberSaveable { mutableStateOf(false) }
    var pickingTo by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(sessionKey) { viewModel.start(sessionKey) }
    LaunchedEffect(initialFilter) {
        if (initialFilter != null) {
            viewModel.applyPreset(initialFilter)
            onInitialFilterApplied()
        }
    }

    val shown = state.filteredResults

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader("Customer support", "Find a booking, cancel it, record refunds, reissue QR tickets")
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Search") },
                placeholder = { Text("Ticket id, passenger, user id, or QR payload…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                trailingIcon = {
                    IconButton(onClick = viewModel::search) {
                        Icon(Icons.Filled.Search, contentDescription = "Search bookings")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column {
                MicroLabel("Status")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = state.statusFilter == null,
                        onClick = { viewModel.onStatusFilterChanged(null) },
                        label = { Text("All") },
                    )
                    BookingStatus.values().forEach { status ->
                        FilterChip(
                            selected = state.statusFilter == status,
                            onClick = { viewModel.onStatusFilterChanged(status) },
                            label = { Text(humanize(status.name)) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                MicroLabel("Refund")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = state.refundFilter == null,
                        onClick = { viewModel.onRefundFilterChanged(null) },
                        label = { Text("Any") },
                    )
                    RefundStatus.values().forEach { status ->
                        FilterChip(
                            selected = state.refundFilter == status,
                            onClick = { viewModel.onRefundFilterChanged(status) },
                            label = { Text(humanize(status.name)) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                MicroLabel("Booked between (PH)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.weight(1f)) {
                        Text(state.dateFromIso ?: "From")
                    }
                    OutlinedButton(onClick = { pickingTo = true }, modifier = Modifier.weight(1f)) {
                        Text(state.dateToIso ?: "To")
                    }
                    if (state.dateFromIso != null || state.dateToIso != null) {
                        TextButton(onClick = viewModel::clearDateRange) { Text("Clear") }
                    }
                }
            }
        }
        item {
            Column {
                if (!state.isLoading && state.hasSearched && state.error == null) {
                    HintText(
                        "Displaying ${shown.size} booking${if (shown.size == 1) "" else "s"}" +
                            if (shown.size != state.results.size) " of ${state.results.size} fetched" else "",
                    )
                }
                if (state.isTruncated) {
                    Spacer(modifier = Modifier.height(6.dp))
                    NoteCard(
                        bold = "Showing the first $SUPPORT_PAGE_SIZE",
                        text = "The search API returns one page. Narrow the search (ticket id, passenger name, user id) to find older bookings.",
                    )
                }
            }
        }
        when {
            state.isLoading -> item { LoadingRow() }
            state.error != null && state.results.isEmpty() -> item { ErrorWithRetry(state.error!!, onRetry = viewModel::search) }
            state.error != null -> item { InlineError(state.error) }
            shown.isEmpty() && state.hasSearched -> item { EmptyText("No bookings match.") }
            else -> items(shown, key = { it.id }) { booking ->
                BookingRow(
                    booking = booking,
                    onClick = { viewModel.openBooking(booking.id) },
                    selected = state.selected?.id == booking.id,
                )
            }
        }
    }

    if (pickingFrom) {
        StaffDatePickerDialog(
            initialIsoDay = state.dateFromIso ?: PhTime.todayIso(),
            onPick = {
                pickingFrom = false
                viewModel.onDateFromChanged(it)
            },
            onDismiss = { pickingFrom = false },
        )
    }
    if (pickingTo) {
        StaffDatePickerDialog(
            initialIsoDay = state.dateToIso ?: PhTime.todayIso(),
            onPick = {
                pickingTo = false
                viewModel.onDateToChanged(it)
            },
            onDismiss = { pickingTo = false },
        )
    }

    SupportBookingDetailSheet(viewModel = viewModel, onMessage = onMessage)
}

/**
 * The booking detail bottom sheet plus its snackbar plumbing. Lives apart
 * from the tab so Overview can open the same sheet from a recent-booking row.
 */
@Composable
fun SupportBookingDetailSheet(viewModel: AdminSupportViewModel, onMessage: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    if (state.showDetail) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeDetail,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                val booking = state.selected
                when {
                    state.isDetailLoading && booking == null -> LoadingRow()
                    booking == null -> InlineError(state.detailError ?: "Unable to load booking")
                    else -> AdminBookingDetail(
                        booking = booking,
                        isActing = state.isActing || state.isDetailLoading,
                        error = state.detailError,
                        onCancel = viewModel::cancelBooking,
                        onRefund = viewModel::setRefundStatus,
                        onReissueQr = viewModel::reissueQr,
                    )
                }
            }
        }
    }
}
