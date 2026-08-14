package com.ridevibe.app.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.app.ui.theme.charcoalTopBarColors
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A support topic row: title + one-line description (design page 8). */
data class SupportTopic(val title: String, val description: String, val icon: ImageVector)

val supportTopics = listOf(
    SupportTopic("Rebook a trip", "Move to another date or departure time", Icons.Filled.EventRepeat),
    SupportTopic("Request a refund", "Cancelled or missed trip", Icons.Filled.CurrencyExchange),
    SupportTopic("Payment or wallet issue", "Failed charge, missing credits, promo code", Icons.Filled.AccountBalanceWallet),
    SupportTopic("Seat or ticket problem", "Wrong seat, QR not scanning, name change", Icons.Filled.AirlineSeatReclineNormal),
    SupportTopic("Lost item", "Left something on board", Icons.Filled.Luggage),
    SupportTopic("Report a driver or bus", "Safety, conduct, or vehicle condition", Icons.Filled.ReportProblem),
    SupportTopic("Something else", "Describe it in your own words", Icons.AutoMirrored.Filled.Chat),
)

data class SupportUiState(
    val bookings: List<Ticket> = emptyList(),
    val selectedBooking: Ticket? = null,
)

// CheckoutRepository is injected directly (no use-case layer): a plain list
// fetch to pick which booking a support request is about.
@HiltViewModel
class SupportViewModel @Inject constructor(
    checkoutRepository: CheckoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // Soonest upcoming trip first — the booking a request is most likely about.
            val bookings = checkoutRepository.getMyBookings().sortedWith(
                compareBy({ it.trip.departureEpochMillis < now }, { it.trip.departureEpochMillis }),
            )
            _uiState.update { it.copy(bookings = bookings, selectedBooking = bookings.firstOrNull()) }
        }
    }

    fun onBookingSelected(booking: Ticket) = _uiState.update { it.copy(selectedBooking = booking) }
}

/** Booking reference shown on the context card and attached to the chat. */
fun Ticket.supportLabel(): String = "$id · ${trip.origin} → ${trip.destination}"

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
    val uiState by viewModel.uiState.collectAsState()
    var showBookingPicker by remember { mutableStateOf(false) }

    if (showBookingPicker) {
        AlertDialog(
            onDismissRequest = { showBookingPicker = false },
            title = { Text("Which booking is this about?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    uiState.bookings.forEach { booking ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (booking.id == uiState.selectedBooking?.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    viewModel.onBookingSelected(booking)
                                    showBookingPicker = false
                                },
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
            },
            confirmButton = { TextButton(onClick = { showBookingPicker = false }) { Text("Cancel") } },
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
                        "Still stuck? An agent replies in about 3 min.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Button(onClick = onChatNow, shape = RoundedCornerShape(24.dp)) {
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
            Text("How can we help?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Pick a topic so we can pull up the right booking before you chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            uiState.selectedBooking?.let { booking ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "ABOUT THIS BOOKING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                booking.supportLabel(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        TextButton(onClick = { showBookingPicker = true }) {
                            Text(
                                "CHANGE",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "CHOOSE A TOPIC",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                supportTopics.forEach { topic ->
                    TopicRow(
                        topic = topic,
                        onClick = {
                            onTopicSelected(topic.title, uiState.selectedBooking?.supportLabel())
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TopicRow(topic: SupportTopic, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                        topic.icon,
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
