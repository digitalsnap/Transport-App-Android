package com.ridevibe.app.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.app.auth.PassengerSession
import com.ridevibe.app.auth.PassengerSessionViewModel
import com.ridevibe.app.ui.theme.charcoalTopBarColors
import com.ridevibe.core.domain.model.Vehicle
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    /** Opens the staff console (admin / partner sign-in). */
    onOpenStaffConsole: () -> Unit,
    /** Guest tapped "Sign in": back to Welcome. */
    onSignIn: () -> Unit,
    /** Session cleared: leave for Welcome with the back stack emptied. */
    onSignedOut: () -> Unit,
    sessionViewModel: PassengerSessionViewModel,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savedMessage) {
        val text = uiState.savedMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onSavedMessageShown()
    }
    LaunchedEffect(sessionState.error) {
        val text = sessionState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        sessionViewModel.onErrorShown()
    }
    LaunchedEffect(sessionState.signedOut) {
        if (sessionState.signedOut) {
            sessionViewModel.onSignedOutHandled()
            onSignedOut()
        }
    }

    // OpenDocument (not GetContent) so the grant can be made persistable: the
    // certificate must still open after the provider's temporary grant lapses.
    val certificatePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val info = readDocumentInfo(context, uri)
        viewModel.onCertificatePicked(uri.toString(), info.displayName, info.mimeType, info.sizeBytes)
    }

    // Permission-free: the system contacts picker returns one phone row with a
    // read grant on just that row, so READ_CONTACTS is never needed.
    val phonePicker = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        uri?.let { readPickedPhoneNumber(context, it) }?.let(viewModel::onPhonePickedFromContacts)
    }

    var showBirthdateSheet by rememberSaveable { mutableStateOf(false) }
    if (showBirthdateSheet) {
        BirthdatePickerSheet(
            initialIsoDate = uiState.profile.birthDate,
            onDismiss = { showBirthdateSheet = false },
            onSet = { iso ->
                viewModel.onProfileFieldChanged { it.copy(birthDate = iso) }
                showBirthdateSheet = false
            },
        )
    }

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete your account?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This signs you out and removes your profile, booking in progress and saved tickets " +
                        "from this phone. Tickets already issued stay valid for travel.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    sessionViewModel.deleteAccount()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Keep account") }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                    }
                },
                colors = charcoalTopBarColors(),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(uiState.error.orEmpty(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = viewModel::load) { Text("Retry", fontWeight = FontWeight.Bold) }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                IdentityHeader(
                    session = sessionState.session,
                    profileName = uiState.profile.fullName,
                    onSignIn = onSignIn,
                )

                SectionTitle("Customer data")
                ProfileField("First name", uiState.profile.firstName) { value ->
                    viewModel.onProfileFieldChanged { it.copy(firstName = value) }
                }
                ProfileField("Last name", uiState.profile.lastName) { value ->
                    viewModel.onProfileFieldChanged { it.copy(lastName = value) }
                }
                ProfileField("Email", uiState.profile.email, error = uiState.fieldErrors.email) { value ->
                    viewModel.onProfileFieldChanged { it.copy(email = value) }
                }
                BirthdateField(
                    isoDate = uiState.profile.birthDate,
                    error = uiState.fieldErrors.birthDate,
                    onClick = { showBirthdateSheet = true },
                )
                PhoneField(
                    value = uiState.profile.mobileNumber,
                    fromContacts = uiState.phoneFromContacts,
                    error = uiState.fieldErrors.mobileNumber,
                    onValueChange = viewModel::onPhoneEdited,
                    onPickFromContacts = { phonePicker.launch(Unit) },
                )
                ProfileField("Address", uiState.profile.address) { value ->
                    viewModel.onProfileFieldChanged { it.copy(address = value) }
                }

                SectionTitle("Emergency contact")
                ProfileField("Contact name", uiState.profile.emergencyContactName) { value ->
                    viewModel.onProfileFieldChanged { it.copy(emergencyContactName = value) }
                }
                ProfileField(
                    "Contact number",
                    uiState.profile.emergencyContactNumber,
                    error = uiState.fieldErrors.emergencyContactNumber,
                ) { value ->
                    viewModel.onProfileFieldChanged { it.copy(emergencyContactNumber = value) }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = viewModel::saveProfile,
                    enabled = uiState.canSaveProfile,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("profile_save"),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Text(if (uiState.isSaving) "Saving…" else "Save Profile", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                SectionTitle("My vehicles")
                Text(
                    "Register a vehicle now to be ready for Roll-on/Roll-off (RORO) ferry bookings when they launch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                uiState.vehicles.forEach { vehicle ->
                    VehicleCard(vehicle = vehicle, onRemove = { viewModel.removeVehicle(vehicle.id) })
                    Spacer(modifier = Modifier.height(10.dp))
                }

                AddVehicleCard(
                    plateNumber = uiState.newPlateNumber,
                    certificateName = uiState.newCertificateName,
                    canAdd = uiState.canAddVehicle,
                    error = uiState.vehicleError,
                    onPlateChanged = viewModel::onNewPlateNumberChanged,
                    onPickCertificate = { certificatePicker.launch(arrayOf("image/*", "application/pdf")) },
                    onAdd = viewModel::addVehicle,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                SectionTitle("Staff access")
                Text(
                    "For RideVibe admins and partner operators: bookings support, trips, services and manifests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenStaffConsole,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text("Staff sign-in", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                SectionTitle("Account")
                OutlinedButton(
                    onClick = sessionViewModel::signOut,
                    enabled = !sessionState.isBusy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text("Sign out", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !sessionState.isBusy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(
                        if (sessionState.isBusy) "Deleting…" else "Delete account",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/** Initials avatar, name and email; a Guest chip with a sign-in call to action for the guest path. */
@Composable
private fun IdentityHeader(session: PassengerSession?, profileName: String, onSignIn: () -> Unit) {
    val isGuest = session == null || session.isGuest
    val name = session?.displayName?.takeIf { it.isNotBlank() } ?: profileName
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isGuest) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                // No image loader in the app yet, so the photo URL is not shown; initials stand in.
                Text(
                    session?.initials ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                name.ifBlank { "Set up your profile" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            val subtitle = session?.email?.takeIf { !isGuest && it.isNotBlank() }
                ?: "Accurate details speed up boarding verification"
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isGuest) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = onSignIn, label = { Text("Guest") })
                    TextButton(onClick = onSignIn) { Text("Sign in", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(top = 12.dp, bottom = 10.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    error: String? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

/** Read-only field that opens the birthdate bottom sheet. */
@Composable
private fun BirthdateField(isoDate: String, error: String?, onClick: () -> Unit) {
    val formatted = formatBirthdate(isoDate)
    Box(modifier = Modifier.padding(bottom = 10.dp)) {
        OutlinedTextField(
            value = formatted,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Birthdate") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            // Disabled state keeps the click on the wrapper; restore enabled colors.
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // The overlay is what TalkBack lands on, so it announces the field and its value.
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics { contentDescription = "Birthdate, ${formatted.ifBlank { "not set" }}" }
                .clickable(onClick = onClick, role = Role.Button, onClickLabel = "Change birthdate"),
        )
    }
}

@Composable
private fun PhoneField(
    value: String,
    fromContacts: Boolean,
    error: String?,
    onValueChange: (String) -> Unit,
    onPickFromContacts: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Phone") },
        placeholder = { Text("09XXXXXXXXX") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fromContacts) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "FROM CONTACTS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                IconButton(onClick = onPickFromContacts) {
                    Icon(
                        Icons.Filled.Contacts,
                        contentDescription = "Pick phone number from contacts",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

// ── Birthdate bottom sheet ───────────────────────────────────────────────────

/**
 * Modal date picker per the redesign (page 5): year/month/day selectors on top
 * (day highlighted), a day grid below, CANCEL/SET actions. Rendered in its own
 * window, so it always sits above the bottom navigation bar.
 */
@Composable
private fun BirthdatePickerSheet(
    initialIsoDate: String,
    onDismiss: () -> Unit,
    onSet: (isoDate: String) -> Unit,
) {
    val initial = remember(initialIsoDate) { parseIsoDate(initialIsoDate) }
    var year by rememberSaveable { mutableIntStateOf(initial.first) }
    var month by rememberSaveable { mutableIntStateOf(initial.second) } // 0-based
    var day by rememberSaveable { mutableIntStateOf(initial.third) }

    // Localised names from the platform, not a hard-coded English list.
    val symbols = remember { DateFormatSymbols.getInstance(Locale.getDefault()) }
    val monthNames = remember(symbols) { symbols.months.take(12) }
    val weekdayInitials = remember(symbols) {
        // shortWeekdays is 1-based (Calendar.SUNDAY..SATURDAY); index 0 is unused.
        (Calendar.SUNDAY..Calendar.SATURDAY).map { symbols.shortWeekdays[it].take(1).uppercase(Locale.getDefault()) }
    }

    val daysInMonth = remember(year, month) {
        Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    // Switching from the 31st of one month to a shorter month: clamp for display
    // and for SET, without writing state during composition.
    val selectedDay = day.coerceAtMost(daysInMonth)
    // Day-of-week offset of the 1st (0 = Sunday) so the grid aligns to the weekday header.
    val firstDayOffset = remember(year, month) {
        Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("Select birthdate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectorTile(
                    caption = "YEAR",
                    value = "$year",
                    options = (Calendar.getInstance().get(Calendar.YEAR) downTo 1920).map { "$it" },
                    onSelected = { year = it.toInt() },
                    modifier = Modifier.weight(1f),
                )
                SelectorTile(
                    caption = "MONTH",
                    value = monthNames[month],
                    options = monthNames,
                    onSelected = { month = monthNames.indexOf(it) },
                    modifier = Modifier.weight(1.2f),
                )
                // Day tile is the highlighted selector; the grid below picks it.
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            "DAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        )
                        Text(
                            "$selectedDay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayInitials.forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            val totalCells = firstDayOffset + daysInMonth
            val rows = (totalCells + 6) / 7
            repeat(rows) { rowIndex ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { columnIndex ->
                        val dayNumber = rowIndex * 7 + columnIndex - firstDayOffset + 1
                        Box(modifier = Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (dayNumber in 1..daysInMonth) {
                                val selected = dayNumber == selectedDay
                                // Clickable Surface pads its touch target to 48dp around the 38dp disc.
                                Surface(
                                    onClick = { day = dayNumber },
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .semantics { contentDescription = "${monthNames[month]} $dayNumber" },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "$dayNumber",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.size(4.dp))
                TextButton(onClick = { onSet("%04d-%02d-%02d".format(year, month + 1, selectedDay)) }) {
                    Text("SET", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SelectorTile(
    caption: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$caption $value" },
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Parse ISO yyyy-MM-dd into (year, month 0-based, day); default 2000-01-01. */
private fun parseIsoDate(iso: String): Triple<Int, Int, Int> {
    val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
    return if (parts.size == 3 && parts[1] in 1..12 && parts[2] in 1..31) {
        Triple(parts[0], parts[1] - 1, parts[2])
    } else {
        Triple(2000, 0, 1)
    }
}

private fun formatBirthdate(iso: String): String {
    val (year, month, day) = parseIsoDate(iso).takeIf { iso.isNotBlank() } ?: return ""
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month, day)
    }
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(calendar.time)
}

// ── Contacts picker (no READ_CONTACTS) ───────────────────────────────────────

/**
 * ACTION_PICK on the phone-number table: the picker returns one data row with
 * a temporary read grant, which is why this needs no contacts permission.
 * (PickContact would return a contact URI whose grant does not cover the
 * phone table.)
 */
private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

private fun readPickedPhoneNumber(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()?.takeIf { it.isNotBlank() }

// ── Picked document metadata ─────────────────────────────────────────────────

private data class DocumentInfo(val displayName: String?, val mimeType: String?, val sizeBytes: Long?)

/** Name, type and size of a picked document, from the provider; nulls where the provider withholds them. */
private fun readDocumentInfo(context: Context, uri: Uri): DocumentInfo {
    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    var displayName: String? = null
    var sizeBytes: Long? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0)
                sizeBytes = if (cursor.isNull(1)) null else cursor.getLong(1)
            }
        }
    }
    return DocumentInfo(displayName, mimeType, sizeBytes)
}

@Composable
private fun VehicleCard(vehicle: Vehicle, onRemove: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(vehicle.plateNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        " LTO Certificate of Registration on file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove vehicle ${vehicle.plateNumber}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddVehicleCard(
    plateNumber: String,
    certificateName: String?,
    canAdd: Boolean,
    error: String?,
    onPlateChanged: (String) -> Unit,
    onPickCertificate: () -> Unit,
    onAdd: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Add a Vehicle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = plateNumber,
                onValueChange = onPlateChanged,
                label = { Text("Plate number") },
                placeholder = { Text("e.g. NBC 1234") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            val certificateAttached = certificateName != null
            OutlinedButton(
                onClick = onPickCertificate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    if (certificateAttached) Icons.Filled.CheckCircle else Icons.Filled.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    if (certificateAttached) "Attached: $certificateName" else "Upload LTO Certificate of Registration",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Text(
                "Photo, scan or PDF, up to 10 MB.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onAdd,
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("Add Vehicle", fontWeight = FontWeight.Bold)
            }
        }
    }
}
