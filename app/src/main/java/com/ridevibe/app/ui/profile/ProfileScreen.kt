package com.ridevibe.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.ridevibe.core.domain.model.Vehicle

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
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

            SectionTitle("Personal information")
            ProfileField("Full name", uiState.profile.fullName) { value ->
                viewModel.onProfileFieldChanged { it.copy(fullName = value) }
            }
            ProfileField("Email", uiState.profile.email) { value ->
                viewModel.onProfileFieldChanged { it.copy(email = value) }
            }
            ProfileField("Mobile number", uiState.profile.mobileNumber) { value ->
                viewModel.onProfileFieldChanged { it.copy(mobileNumber = value) }
            }
            ProfileField("Birth date (YYYY-MM-DD)", uiState.profile.birthDate) { value ->
                viewModel.onProfileFieldChanged { it.copy(birthDate = value) }
            }
            ProfileField("Home address", uiState.profile.address) { value ->
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
