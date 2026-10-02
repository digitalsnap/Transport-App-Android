package com.ridevibe.feature.admin.ui.admin

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.OperatorSummary
import com.ridevibe.feature.admin.ui.components.ButtonSpinner
import com.ridevibe.feature.admin.ui.components.ConfirmDialog
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.ErrorWithRetry
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
import com.ridevibe.feature.admin.viewmodel.MIN_STAFF_PASSWORD_LENGTH
import com.ridevibe.feature.admin.viewmodel.PartnerSort

/** Partners: bus lines, ferries and fastcraft (RoRo) operators — their services and seating. */
@Composable
fun AdminPartnersTab(
    sessionKey: String,
    onMessage: (String) -> Unit,
    onViewAsPartner: (operatorId: Int) -> Unit,
    viewModel: AdminPartnersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateSignIn by rememberSaveable { mutableStateOf(false) }
    var showIssueToken by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(sessionKey) { viewModel.start(sessionKey) }
    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    val shown = state.shownOperators

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                PageHeader(
                    "Partners",
                    "Bus lines, ferries and fastcraft (RoRo) operators — their services and seating",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh partners")
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Search partners") },
                placeholder = { Text("Company name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                PartnerSort.values().forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { viewModel.onSortChanged(sort) },
                        label = { Text(sort.label) },
                    )
                }
            }
        }
        when {
            state.isLoading -> item { LoadingRow() }
            state.error != null && state.operators.isEmpty() -> item { ErrorWithRetry(state.error!!, onRetry = viewModel::load) }
            state.operators.isEmpty() -> item { EmptyText("No partners yet.") }
            shown.isEmpty() -> item { EmptyText("No partner matches \"${state.query.trim()}\".") }
            else -> {
                state.error?.let { item { InlineError(it) } }
                items(shown, key = { it.id }) { operator ->
                    OperatorRow(
                        operator = operator,
                        onClick = { viewModel.openOperator(operator) },
                        selected = state.selected?.id == operator.id,
                    )
                }
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
                    state.servicesError != null && state.services.isEmpty() ->
                        ErrorWithRetry(state.servicesError!!, onRetry = viewModel::reloadServices)
                    state.services.isEmpty() -> EmptyText("No services yet.")
                    else -> state.services.forEach { service ->
                        ServiceRow(service)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                if (state.services.isNotEmpty()) InlineError(state.servicesError)
                Button(
                    onClick = { showCreateSignIn = true },
                    enabled = !state.isActing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isCreatingSignIn) ButtonSpinner() else Text("Create partner sign-in", fontWeight = FontWeight.Bold)
                }
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
                ) { if (state.isIssuingToken) ButtonSpinner() else Text("Issue legacy token") }
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

/**
 * Email + optional password; leaving the password blank generates one shown
 * once. Creating an account is a real credential, so the second step asks
 * the admin to read the email back before it is sent.
 */
@Composable
private fun CreatePartnerSignInDialog(
    operatorName: String,
    onCreate: (email: String, password: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val passwordTooShort = password.isNotEmpty() && password.length < MIN_STAFF_PASSWORD_LENGTH

    if (confirming) {
        ConfirmDialog(
            title = "Create this sign-in?",
            text = "Create a partner account for ${email.trim()} at $operatorName" +
                if (password.isBlank()) " with a generated password?" else " with the password you typed?",
            confirmLabel = "Create",
            onConfirm = { onCreate(email, password.ifBlank { null }) },
            onDismiss = { confirming = false },
        )
        return
    }

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
                    isError = passwordTooShort,
                    supportingText = if (passwordTooShort) ({ Text("At least $MIN_STAFF_PASSWORD_LENGTH characters") }) else null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                HintText("A generated password is shown once — copy it for the operator.")
            }
        },
        confirmButton = {
            Button(onClick = { confirming = true }, enabled = email.isNotBlank() && !passwordTooShort) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
