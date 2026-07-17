package com.ridevibe.feature.search.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.TripType
import com.ridevibe.feature.search.R
import com.ridevibe.feature.search.viewmodel.SearchFormState
import com.ridevibe.feature.search.viewmodel.SearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home / "Where to next?" screen per the Visily design (page 2). */
@Composable
fun HomeScreen(
    onSearch: (
        origin: String,
        destination: String,
        dateMillis: Long,
        busClass: BusClass?,
        adults: Int,
        children: Int,
        infants: Int,
        bookingForSelf: Boolean,
    ) -> Unit,
    onProfileClick: () -> Unit,
    onBookingsClick: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val form by viewModel.formState.collectAsState()
    var showDepartPicker by remember { mutableStateOf(false) }
    var showReturnPicker by remember { mutableStateOf(false) }
    var showPassengerDialog by remember { mutableStateOf(false) }
    var locationPickerTarget by remember { mutableStateOf<LocationTarget?>(null) }

    if (showDepartPicker) {
        DateDialog(
            initial = form.departureDateMillis,
            onPicked = viewModel::onDateSelected,
            onDismiss = { showDepartPicker = false },
        )
    }
    if (showReturnPicker) {
        DateDialog(
            initial = form.returnDateMillis ?: form.departureDateMillis,
            onPicked = viewModel::onReturnDateSelected,
            onDismiss = { showReturnPicker = false },
        )
    }
    if (showPassengerDialog) {
        PassengerCountDialog(
            adults = form.adults,
            children = form.children,
            infants = form.infants,
            onConfirm = { a, c, i ->
                viewModel.onPassengersChanged(a, c, i)
                showPassengerDialog = false
            },
            onDismiss = { showPassengerDialog = false },
        )
    }
    locationPickerTarget?.let { target ->
        LocationPickerDialog(
            title = if (target == LocationTarget.FROM) "Select origin" else "Select destination",
            locations = form.locations,
            onSelected = { name ->
                when (target) {
                    LocationTarget.FROM -> viewModel.onOriginSelected(name)
                    LocationTarget.TO -> viewModel.onDestinationSelected(name)
                }
                locationPickerTarget = null
            },
            onDismiss = { locationPickerTarget = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CenterAlignedTopAppBar(title = { Text("RideVibe", fontWeight = FontWeight.Bold) }) },
        bottomBar = { DemoBottomNav(onProfileClick = onProfileClick, onBookingsClick = onBookingsClick) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text("Where to next?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Book your seat in seconds",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            SearchCard(
                form = form,
                onPickFrom = { locationPickerTarget = LocationTarget.FROM },
                onPickTo = { locationPickerTarget = LocationTarget.TO },
                onSwap = viewModel::onSwapLocations,
                onTripTypeChanged = viewModel::onTripTypeChanged,
                onPickDepartDate = { showDepartPicker = true },
                onPickReturnDate = { showReturnPicker = true },
                onPickPassengers = { showPassengerDialog = true },
                onBookingForSelfChanged = viewModel::onBookingForSelfChanged,
                onSearch = {
                    onSearch(
                        form.origin,
                        form.destination,
                        form.departureDateMillis ?: return@SearchCard,
                        form.busClassFilter,
                        form.adults,
                        form.children,
                        form.infants,
                        form.bookingForSelf,
                    )
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClassChip("Any class", form.busClassFilter == null) { viewModel.onBusClassFilterChanged(null) }
                // Chips reflect the classes that actually exist in inventory.
                form.busClasses.forEach { busClass ->
                    ClassChip(busClass.displayLabel(), form.busClassFilter == busClass) {
                        viewModel.onBusClassFilterChanged(busClass)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HotDealsSection()

            Spacer(modifier = Modifier.height(20.dp))
            InviteFriendsCard()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private enum class LocationTarget { FROM, TO }

@Composable
private fun SearchCard(
    form: SearchFormState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    onTripTypeChanged: (TripType) -> Unit,
    onPickDepartDate: () -> Unit,
    onPickReturnDate: () -> Unit,
    onPickPassengers: () -> Unit,
    onBookingForSelfChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Trip type tick boxes
            Row(verticalAlignment = Alignment.CenterVertically) {
                TripTypeTick("One-way", form.tripType == TripType.ONE_WAY) { onTripTypeChanged(TripType.ONE_WAY) }
                Spacer(modifier = Modifier.width(16.dp))
                TripTypeTick("Round-trip", form.tripType == TripType.ROUND_TRIP) { onTripTypeChanged(TripType.ROUND_TRIP) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocationField(
                        label = "From",
                        value = form.origin,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onPickFrom,
                    )
                    LocationField(
                        label = "To",
                        value = form.destination,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = onPickTo,
                    )
                }
                IconButton(
                    onClick = onSwap,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = "Swap origin and destination",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(
                    icon = { Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) },
                    caption = if (form.tripType == TripType.ROUND_TRIP) "DEPART" else "DATE",
                    value = form.departureDateMillis?.let(::formatDate) ?: "Pick a date",
                    modifier = Modifier.weight(1f),
                    onClick = onPickDepartDate,
                )
                if (form.tripType == TripType.ROUND_TRIP) {
                    InfoTile(
                        icon = { Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.secondary) },
                        caption = "RETURN",
                        value = form.returnDateMillis?.let(::formatDate) ?: "Pick a date",
                        modifier = Modifier.weight(1f),
                        onClick = onPickReturnDate,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoTile(
                icon = { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary) },
                caption = "PASSENGERS",
                value = form.passengersLabel,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickPassengers,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Who's traveling — decides how checkout treats the primary passenger.
            Text(
                "WHO'S TRAVELING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.bookingForSelf,
                    onClick = { onBookingForSelfChanged(true) },
                    label = { Text("I'm traveling") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                FilterChip(
                    selected = !form.bookingForSelf,
                    onClick = { onBookingForSelfChanged(false) },
                    label = { Text("Booking for someone else") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSearch,
                enabled = form.canSearch,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Search Trips", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TripTypeTick(label: String, checked: Boolean, onCheck: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onCheck),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onCheck() })
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LocationField(
    label: String,
    value: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = iconTint)
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.ifBlank { "Select location" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun InfoTile(
    icon: @Composable () -> Unit,
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Location picker with predictive search ───────────────────────────────────

@Composable
private fun LocationPickerDialog(
    title: String,
    locations: List<TerminalLocation>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, locations) {
        if (query.isBlank()) locations else locations.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val terminals = filtered.filter { it.isCentralTerminal }
    val destinations = filtered.filterNot { it.isCentralTerminal }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search terminals & destinations") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    if (terminals.isNotEmpty()) {
                        item { SectionLabel("Central terminals") }
                        items(terminals, key = { "t-${it.name}" }) { location ->
                            LocationRow(location, highlight = true) { onSelected(location.name) }
                        }
                    }
                    if (destinations.isNotEmpty()) {
                        item { SectionLabel("Destinations") }
                        items(destinations, key = { "d-${it.name}" }) { location ->
                            LocationRow(location, highlight = false) { onSelected(location.name) }
                        }
                    }
                    if (terminals.isEmpty() && destinations.isEmpty()) {
                        item {
                            Text(
                                "No matching locations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun LocationRow(location: TerminalLocation, highlight: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (highlight) Icons.Filled.Hub else Icons.Filled.Place,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                location.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

// ── Passenger count dialog ───────────────────────────────────────────────────

@Composable
private fun PassengerCountDialog(
    adults: Int,
    children: Int,
    infants: Int,
    onConfirm: (adults: Int, children: Int, infants: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var adultCount by remember { mutableStateOf(adults) }
    var childCount by remember { mutableStateOf(children) }
    var infantCount by remember { mutableStateOf(infants) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passengers", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CounterRow("Adults", "12 years and above", adultCount, min = 1) { adultCount = it }
                CounterRow("Children", "2–11 years, seat required", childCount, min = 0) { childCount = it }
                CounterRow("Infants", "Under 2, lap-held — free of charge", infantCount, min = 0, max = 5) { infantCount = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(adultCount, childCount, infantCount) }) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CounterRow(
    label: String,
    caption: String,
    count: Int,
    min: Int,
    max: Int = 10,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { if (count > min) onChange(count - 1) }, enabled = count > min) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer $label")
        }
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = { if (count < max) onChange(count + 1) }, enabled = count < max) {
            Icon(Icons.Filled.Add, contentDescription = "More $label")
        }
    }
}

@Composable
private fun DateDialog(initial: Long?, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onPicked)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun ClassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

internal fun BusClass.displayLabel(): String = when (this) {
    BusClass.ORDINARY -> "Ordinary"
    BusClass.DELUXE -> "Deluxe"
    BusClass.LUXURY -> "Luxury"
}

internal fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(epochMillis))

internal fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(epochMillis))

// ─────────────────────────────────────────────────────────────────────────────
// PRESENTATION-ONLY SECTIONS (design page 2): static demo content, not wired
// to any backend. Deals, referral credits, and the Bookings/Wallet tabs
// require CRS features that don't exist yet — remove or wire up later.
// ─────────────────────────────────────────────────────────────────────────────

private data class DemoDeal(
    val badge: String,
    val tagline: String,
    val title: String,
    val priceLine: String,
    val imageRes: Int, // sample photography (Lorem Picsum) standing in for campaign shots
)

private val demoDeals = listOf(
    DemoDeal("20% OFF", "SUMMER GETAWAY", "Boracay via Batangas", "Starting at ₱850", R.drawable.deal_boracay),
    DemoDeal("15% OFF", "CITY ESCAPE", "Tagaytay Weekender", "Starting at ₱320", R.drawable.deal_tagaytay),
    DemoDeal("10% OFF", "NORTH EXPRESS", "Baguio Night Trip", "Starting at ₱690", R.drawable.deal_baguio),
)

@Composable
private fun HotDealsSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Hot Deals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "See All",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        demoDeals.forEach { deal -> DealSlide(deal) }
    }
}

@Composable
private fun DealSlide(deal: DemoDeal) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Image(
            painter = painterResource(deal.imageRes),
            contentDescription = deal.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.1f),
                        1f to Color.Black.copy(alpha = 0.65f),
                    ),
                ),
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) {
            Text(
                deal.badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(deal.tagline, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text(deal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                deal.priceLine,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFD8A8),
            )
        }
    }
}

@Composable
private fun InviteFriendsCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Invite Friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Earn ₱50 credit for every friend who joins RideVibe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { /* presentation only — referral program not built */ }) {
                    Text("Share Now", fontWeight = FontWeight.Bold)
                }
            }
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun DemoBottomNav(onProfileClick: () -> Unit, onBookingsClick: () -> Unit) {
    val itemColors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = true,
            onClick = { /* already home */ },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = false,
            onClick = onBookingsClick,
            icon = { Icon(Icons.Filled.ConfirmationNumber, contentDescription = "Bookings") },
            label = { Text("Bookings") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* presentation only — wallet not built */ },
            icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Wallet") },
            label = { Text("Wallet") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = false,
            onClick = onProfileClick,
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = itemColors,
        )
    }
}
