package com.ridevibe.feature.admin.ui.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.OperatorSummary
import com.ridevibe.feature.admin.ui.components.ButtonSpinner
import com.ridevibe.feature.admin.ui.components.ConfirmDialog
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LabeledText
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.Pill
import com.ridevibe.feature.admin.ui.components.PillTone
import com.ridevibe.feature.admin.ui.components.RowCard
import com.ridevibe.feature.admin.ui.components.SecretRevealDialog
import com.ridevibe.feature.admin.ui.components.ServiceRow
import com.ridevibe.feature.admin.ui.formatFareRange
import com.ridevibe.feature.admin.ui.formatRating
import com.ridevibe.feature.admin.viewmodel.AdminPartnersViewModel

/** Partners: bus lines, ferries and fastcraft (RoRo) operators — their services and seating. */
@Composable
fun AdminPartnersTab(
    onMessage: (String) -> Unit,
    onViewAsPartner: (operatorId: Int) -> Unit,
    viewModel: AdminPartnersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateSignIn by rememberSaveable { mutableStateOf(false) }
    var showIssueToken by rememberSaveable { mutableStateOf(false) }

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
            PageHeader("Partners", "Bus lines, ferries and fastcraft (RoRo) operators — their services and seating")
        }
        state.error?.let { item { InlineError(it) } }
        when {
            state.isLoading -> item { LoadingRow() }
            state.operators.isEmpty() -> item { EmptyText("No partners yet.") }
            else -> items(state.operators, key = { it.id }) { operator ->
                OperatorRow(
                    operator = operator,
                    onClick = { viewModel.openOperator(operator) },
                    selected = state.selected?.id == operator.id,
                )
            }
        }
    }

    state.selected?.let { operator ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeOperator,
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
                Text(operator.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Services & seating",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    state.isServicesLoading -> LoadingRow()
                    state.services.isEmpty() && state.servicesError == null -> EmptyText("No services yet.")
                    else -> state.services.forEach { service ->
                        ServiceRow(service)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                InlineError(state.servicesError)
                Button(
                    onClick = { showCreateSignIn = true },
                    enabled = !state.isActing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create partner sign-in", fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onViewAsPartner(operator.id) },
                    enabled = !state.isActing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("View as partner (read-only)") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showIssueToken = true },
                    enabled = !state.isActing,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (state.isActing) ButtonSpinner() else Text("Issue legacy token") }
                Spacer(modifier = Modifier.height(10.dp))
                HintText(
                    "\"Create partner sign-in\" makes an email + password account for this company's staff. " +
                        "\"View as partner\" opens their portal read-only with your session. " +
                        "The legacy token is an API credential (pt_…).",
                )
            }
        }

        if (showCreateSignIn) {
            CreatePartnerSignInDialog(
                operatorName = operator.name,
                onCreate = { email, password ->
                    showCreateSignIn = false
                    viewModel.createPartnerSignIn(email, password)
                },
                onDismiss = { showCreateSignIn = false },
            )
        }
        if (showIssueToken) {
            ConfirmDialog(
                title = "Issue a new partner token?",
                text = "Issue a new partner token for ${operator.name}? Any previous token stops working.",
                confirmLabel = "Issue token",
                onConfirm = {
                    showIssueToken = false
                    viewModel.issueLegacyToken()
                },
                onDismiss = { showIssueToken = false },
            )
        }
    }

    state.issuedCredential?.let { credential ->
        val generated = credential.generatedPassword
        if (generated != null) {
            SecretRevealDialog(
                title = "Partner sign-in created",
                message = "${credential.email} can now sign in to the partner portal. " +
                    "Copy the temporary password now — it is shown once.",
                secret = generated,
                secretLabel = "Temporary password",
                onDismiss = viewModel::dismissCredential,
            )
        } else {
            AlertDialog(
                onDismissRequest = viewModel::dismissCredential,
                title = { Text("Partner sign-in created", fontWeight = FontWeight.Bold) },
                text = { Text("${credential.email} can sign in with the password you entered.") },
                confirmButton = { TextButton(onClick = viewModel::dismissCredential) { Text("Done") } },
            )
        }
    }
    state.issuedToken?.let { issued ->
        SecretRevealDialog(
            title = "Legacy token issued",
            message = "${issued.operatorName}: ${issued.note}",
            secret = issued.token,
            secretLabel = "Partner API token (shown once)",
            onDismiss = viewModel::dismissToken,
        )
    }
}

@Composable
private fun OperatorRow(operator: OperatorSummary, onClick: () -> Unit, selected: Boolean) {
    RowCard(onClick = onClick, selected = selected) {
        Text(operator.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "${operator.routes} routes · ${operator.services} services",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            operator.busClasses.forEach { Pill(it, tone = if (it == "LUXURY") PillTone.GOLD else PillTone.PLAIN) }
            operator.rideKinds.forEach { Pill(it, PillTone.MINT) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
            LabeledText("Fare range", formatFareRange(operator.minFarePhp, operator.maxFarePhp))
            LabeledText("Rating", formatRating(operator.avgRating))
        }
    }
}

/** Email + optional password; leaving the password blank generates one shown once. */
@Composable
private fun CreatePartnerSignInDialog(
    operatorName: String,
    onCreate: (email: String, password: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create partner sign-in", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Email address for $operatorName's partner sign-in:")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    placeholder = { Text("Leave blank to generate one") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                HintText("A generated password is shown once — copy it for the operator.")
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(email, password.ifBlank { null }) }, enabled = email.isNotBlank()) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
