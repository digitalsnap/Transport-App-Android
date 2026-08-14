package com.ridevibe.app.ui.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.app.ui.theme.charcoalTopBarColors
import com.ridevibe.core.domain.model.Vehicle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val certificatePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { viewModel.onCertificatePicked(it.toString()) } }

    // ── Phone auto-fill from the device contact card ─────────────────────────
    var showContactsRationale by remember { mutableStateOf(false) }
    var contactsPromptDone by rememberSaveable { mutableStateOf(false) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Denied → silent fall back to manual entry.
        if (granted) readProfilePhoneNumber(context)?.let(viewModel::onPhoneAutoFilled)
    }

    LaunchedEffect(uiState.isLoaded) {
        if (uiState.isLoaded && !contactsPromptDone && uiState.profile.mobileNumber.isBlank()) {
            contactsPromptDone = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                readProfilePhoneNumber(context)?.let(viewModel::onPhoneAutoFilled)
            } else {
                showContactsRationale = true
            }
        }
    }

    if (showContactsRationale) {
        AlertDialog(
            onDismissRequest = { showContactsRationale = false },
            title = { Text("Fill phone from your contact card?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "RideVibe can read the phone number saved on your device's own contact card " +
                        "to fill it in for you. We only read your card — never your other contacts. " +
                        "You can always type the number yourself instead.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showContactsRationale = false
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }) { Text("Continue", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showContactsRationale = false }) { Text("Enter manually") }
            },
        )
    }

    // ── Birthdate bottom-sheet picker ────────────────────────────────────────
    var showBirthdateSheet by remember { mutableStateOf(false) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Avatar + intro
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        uiState.profile.fullName.ifBlank { "Set up your profile" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Accurate details speed up boarding verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("Customer data")
            ProfileField("First name", uiState.profile.firstName) { value ->
                viewModel.onProfileFieldChanged { it.copy(firstName = value) }
            }
            ProfileField("Last name", uiState.profile.lastName) { value ->
                viewModel.onProfileFieldChanged { it.copy(lastName = value) }
            }
            ProfileField("Email", uiState.profile.email) { value ->
                viewModel.onProfileFieldChanged { it.copy(email = value) }
            }
            BirthdateField(
                isoDate = uiState.profile.birthDate,
                onClick = { showBirthdateSheet = true },
            )
            PhoneField(
                value = uiState.profile.mobileNumber,
                fromContacts = uiState.phoneFromContacts,
                onValueChange = viewModel::onPhoneEdited,
            )
            ProfileField("Address", uiState.profile.address) { value ->
                viewModel.onProfileFieldChanged { it.copy(address = value) }
            }

            SectionTitle("Emergency contact")
            ProfileField("Contact name", uiState.profile.emergencyContactName) { value ->
                viewModel.onProfileFieldChanged { it.copy(emergencyContactName = value) }
            }
            ProfileField("Contact number", uiState.profile.emergencyContactNumber) { value ->
                viewModel.onProfileFieldChanged { it.copy(emergencyContactNumber = value) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = viewModel::saveProfile,
                enabled = uiState.canSaveProfile,
                modifier = Modifier.fillMaxWidth().height(52.dp),
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
                certificateAttached = uiState.newCertificateUri != null,
                canAdd = uiState.canAddVehicle,
                error = uiState.vehicleError,
                onPlateChanged = viewModel::onNewPlateNumberChanged,
                onPickCertificate = { certificatePicker.launch("image/*") },
                onAdd = viewModel::addVehicle,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
    )
}

@Composable
private fun ProfileField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

/** Read-only field that opens the birthdate bottom sheet. */
@Composable
private fun BirthdateField(isoDate: String, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(bottom = 10.dp)) {
        OutlinedTextField(
            value = formatBirthdate(isoDate),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Birthdate") },
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
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun PhoneField(
    value: String,
    fromContacts: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Phone") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        trailingIcon = if (fromContacts) {
            {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(end = 10.dp),
                ) {
                    Text(
                        "FROM CONTACTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

// ── Birthdate bottom sheet ───────────────────────────────────────────────────

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

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
    var year by remember { mutableStateOf(initial.first) }
    var month by remember { mutableStateOf(initial.second) } // 0-based
    var day by remember { mutableStateOf(initial.third) }

    val daysInMonth = remember(year, month) {
        Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    if (day > daysInMonth) day = daysInMonth
    // Day-of-week offset of the 1st (0 = Sunday) so the grid aligns to S M T W T F S.
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
                    value = MONTH_NAMES[month],
                    options = MONTH_NAMES,
                    onSelected = { month = MONTH_NAMES.indexOf(it) },
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
                            "$day",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
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
                                val selected = dayNumber == day
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable { day = dayNumber },
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
                TextButton(onClick = { onSet("%04d-%02d-%02d".format(year, month + 1, day)) }) {
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
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
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
                    contentDescription = "Change $caption",
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

/**
 * Phone number from the device owner's contact card ("Me" profile), or null if
 * the card is empty. Requires READ_CONTACTS, which covers the profile directory.
 */
private fun readProfilePhoneNumber(context: Context): String? {
    val uri = ContactsContract.Profile.CONTENT_URI.buildUpon()
        .appendPath(ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
        .build()
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.Contacts.Data.MIMETYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                    val number = cursor.getString(0)
                    if (!number.isNullOrBlank()) return@use number
                }
            }
            null
        }
    }.getOrNull()
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
                    contentDescription = "Remove vehicle",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddVehicleCard(
    plateNumber: String,
    certificateAttached: Boolean,
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
                    if (certificateAttached) "LTO Certificate attached ✓" else "Upload LTO Certificate of Registration",
                    fontWeight = FontWeight.SemiBold,
                )
            }

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
