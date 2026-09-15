package com.ridevibe.feature.admin.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.feature.admin.ui.components.BookingRow
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.viewmodel.AdminSupportViewModel

/** Customer support: find a booking, cancel it, record refunds, reissue QR tickets. */
@Composable
fun AdminSupportTab(
    onMessage: (String) -> Unit,
    viewModel: AdminSupportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.statusFilter == null,
                    onClick = { viewModel.onStatusFilterChanged(null) },
                    label = { Text("All") },
                )
                BookingStatus.values().forEach { status ->
                    FilterChip(
                        selected = state.statusFilter == status,
                        onClick = { viewModel.onStatusFilterChanged(status) },
                        label = { Text(status.name) },
                    )
                }
            }
        }
        item {
            Column {
                InlineError(state.error)
                if (!state.isLoading && state.hasSearched) {
                    HintText("Displaying ${state.results.size} booking${if (state.results.size == 1) "" else "s"}")
                }
            }
        }
        when {
            state.isLoading -> item { LoadingRow() }
            state.results.isEmpty() && state.hasSearched -> item { EmptyText("No bookings match.") }
            else -> items(state.results, key = { it.id }) { booking ->
                BookingRow(
                    booking = booking,
                    onClick = { viewModel.openBooking(booking.id) },
                    selected = state.selected?.id == booking.id,
                )
            }
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
