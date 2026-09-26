package com.ridevibe.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.app.ui.theme.charcoalTopBarColors
import com.ridevibe.core.domain.model.Ticket

/**
 * Support triage (design page 8): pick the booking and topic first; the chat
 * thread opens with both attached. CHAT NOW skips the topic.
 */
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onTopicSelected: (topic: String, bookingLabel: String?) -> Unit,
    onChatNow: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showBookingPicker by rememberSaveable { mutableStateOf(false) }

    if (showBookingPicker) {
        BookingPickerDialog(
            uiState = uiState,
            onPick = { booking ->
                viewModel.onBookingSelected(booking)
                showBookingPicker = false
            },
            onContinueWithout = {
                viewModel.clearSelectedBooking()
                showBookingPicker = false
            },
            onDismiss = { showBookingPicker = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Filled.Person, contentDescription = "Profile")
                    }
                },
                colors = charcoalTopBarColors(),
            )
        },
        bottomBar = {
            // Pinned above the app's bottom navigation (the root Scaffold insets it).
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Still stuck? We'll reply here as soon as an agent is free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Button(
                        onClick = onChatNow,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("support_chat_now"),
                    ) {
                        Text("CHAT NOW", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "How can we help?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Pick a topic so we can pull up the right booking before you chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                BookingContextCard(
                    label = uiState.selectedBooking?.supportLabel(),
                    error = uiState.error,
                    onChange = { showBookingPicker = true },
                    onRetry = viewModel::load,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "CHOOSE A TOPIC",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.topics.forEach { topic ->
                    TopicRow(
                        topic = topic,
                        onClick = { onTopicSelected(topic.title, uiState.selectedBooking?.supportLabel()) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** "About this booking" card; with nothing attached it invites the rider to attach one. */
@Composable
private fun BookingContextCard(label: String?, error: String?, onChange: () -> Unit, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (label != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val contentColor = if (label != null) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ABOUT THIS BOOKING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.7f),
                )
                Text(
                    label ?: error?.let { "Couldn't load your bookings" } ?: "No booking attached",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
            if (error != null && label == null) {
                TextButton(onClick = onRetry) { Text("RETRY", fontWeight = FontWeight.Bold, color = contentColor) }
            } else {
                TextButton(onClick = onChange) {
                    Text(if (label != null) "CHANGE" else "ATTACH", fontWeight = FontWeight.Bold, color = contentColor)
                }
            }
        }
    }
}

@Composable
private fun BookingPickerDialog(
    uiState: SupportUiState,
    onPick: (Ticket) -> Unit,
    onContinueWithout: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which booking is this about?", fontWeight = FontWeight.Bold) },
        text = {
            if (uiState.bookings.isEmpty()) {
                Text(
                    "You have no bookings yet. You can still chat with us about anything else.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    uiState.bookings.forEach { booking ->
                        val selected = booking.id == uiState.selectedBooking?.id
                        Surface(
                            onClick = { onPick(booking) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .semantics { role = Role.RadioButton },
                        ) {
                            Text(
                                booking.supportLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinueWithout) { Text("Continue without a booking", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The icon for a topic id; the topic list itself is data on the view model. */
private fun topicIcon(id: String): ImageVector = when (id) {
    SupportViewModel.TOPIC_REBOOK -> Icons.Filled.EventRepeat
    SupportViewModel.TOPIC_REFUND -> Icons.Filled.CurrencyExchange
    SupportViewModel.TOPIC_PAYMENT -> Icons.Filled.AccountBalanceWallet
    SupportViewModel.TOPIC_SEAT -> Icons.Filled.AirlineSeatReclineNormal
    SupportViewModel.TOPIC_LOST_ITEM -> Icons.Filled.Luggage
    SupportViewModel.TOPIC_REPORT -> Icons.Filled.ReportProblem
    else -> Icons.AutoMirrored.Filled.Chat
}

@Composable
private fun TopicRow(topic: SupportTopic, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        topicIcon(topic.id),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(topic.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    topic.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
