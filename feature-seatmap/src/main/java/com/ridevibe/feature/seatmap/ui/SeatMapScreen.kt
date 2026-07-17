package com.ridevibe.feature.seatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.feature.seatmap.viewmodel.SeatMapViewModel

/** Bus layout: 2 seats, aisle, 1 seat per row (standard provincial coach). */
private const val COLUMNS_PER_ROW = 3

@Composable
fun SeatMapScreen(
    onProceedToCheckout: (seatId: String) -> Unit,
    viewModel: SeatMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Select your seat") }) },
        bottomBar = {
            SeatMapBottomBar(
                holdSecondsRemaining = uiState.holdSecondsRemaining,
                canProceed = uiState.selectedSeatId != null,
                onProceed = { uiState.selectedSeatId?.let(onProceedToCheckout) },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            SeatLegend()
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS_PER_ROW),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.seats, key = { it.id }) { seat ->
                    SeatCell(seat = seat, onClick = { viewModel.onSeatClicked(seat) })
                }
            }
        }
    }
}

@Composable
private fun SeatCell(seat: Seat, onClick: () -> Unit) {
    val isAisleGap = seat.column == 2 // leaves a visual gap for the aisle in a 2-1 layout
    if (isAisleGap) {
        Box(modifier = Modifier.aspectRatio(1f))
        return
    }

    val (background, border) = seat.colors()
    val isClickable = seat.status == SeatStatus.AVAILABLE || seat.status == SeatStatus.SELECTED

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(background, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = isClickable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = seat.label, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Seat.colors(): Pair<Color, Color> = when (status) {
    SeatStatus.AVAILABLE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.outline
    SeatStatus.SELECTED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    SeatStatus.LOCKED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
    SeatStatus.OCCUPIED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
}

@Composable
private fun SeatLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LegendItem("Available", MaterialTheme.colorScheme.surfaceVariant)
        LegendItem("Selected", MaterialTheme.colorScheme.primaryContainer)
        LegendItem("Locked", MaterialTheme.colorScheme.tertiaryContainer)
        LegendItem("Occupied", MaterialTheme.colorScheme.errorContainer)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.aspectRatio(1f).background(color, RoundedCornerShape(4.dp)).padding(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun SeatMapBottomBar(
    holdSecondsRemaining: Int?,
    canProceed: Boolean,
    onProceed: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (holdSecondsRemaining != null) {
            val minutes = holdSecondsRemaining / 60
            val seconds = holdSecondsRemaining % 60
            Text(
                text = "Seat held for %d:%02d".format(minutes, seconds),
                color = if (holdSecondsRemaining < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Button(onClick = onProceed, enabled = canProceed, modifier = Modifier.fillMaxWidth()) {
            Text("Proceed to checkout")
        }
    }
}
