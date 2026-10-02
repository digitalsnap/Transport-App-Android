package com.ridevibe.feature.admin.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.CuratedJourney
import com.ridevibe.core.domain.model.LiveHold
import com.ridevibe.core.domain.model.OperatorSummary
import com.ridevibe.core.domain.model.PassengerAccount
import com.ridevibe.core.domain.model.RouteSummary
import com.ridevibe.core.domain.model.StaffAccount
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.feature.admin.ui.components.CodeText
import com.ridevibe.feature.admin.ui.components.ConfirmDialog
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.ErrorWithRetry
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LabeledText
import com.ridevibe.feature.admin.ui.components.LabeledValue
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.Pill
import com.ridevibe.feature.admin.ui.components.PillTone
import com.ridevibe.feature.admin.ui.components.RowCard
import com.ridevibe.feature.admin.ui.components.SeatChips
import com.ridevibe.feature.admin.ui.components.SecretRevealDialog
import com.ridevibe.feature.admin.ui.formatDuration
import com.ridevibe.feature.admin.ui.formatPhDateTime
import com.ridevibe.feature.admin.ui.formatPhp
import com.ridevibe.feature.admin.ui.humanize
import com.ridevibe.feature.admin.ui.shortId
import com.ridevibe.feature.admin.viewmodel.AdminDataViewModel
import com.ridevibe.feature.admin.viewmodel.DataDetail
import com.ridevibe.feature.admin.viewmodel.DataSection
import com.ridevibe.feature.admin.viewmodel.MIN_STAFF_PASSWORD_LENGTH

/** Data: reference data and live operational records, with staff account administration. */
@Composable
fun AdminDataTab(
    sessionKey: String,
    onMessage: (String) -> Unit,
    viewModel: AdminDataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateAccount by rememberSaveable { mutableStateOf(false) }
    // Account ids rather than the objects: ids survive process death, and the row is looked up again below.
    var pendingResetId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionKey) { viewModel.start(sessionKey) }
    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageHeader("Data", "Reference data and live operational records", modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh ${state.section.label.lowercase()}")
            }
        }
        ScrollableTabRow(
            selectedTabIndex = state.section.ordinal,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            DataSection.values().forEach { section ->
                Tab(
                    selected = state.section == section,
                    onClick = { viewModel.selectSection(section) },
                    text = { Text(section.label) },
                )
            }
        }

        PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::load, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val error = state.error
                if (error != null) {
                    item { ErrorWithRetry(error, onRetry = viewModel::load) }
                }

                when (state.section) {
                    DataSection.ACCOUNTS -> {
                        item {
                            Column {
                                Button(onClick = { showCreateAccount = true }, enabled = !state.isActing) {
                                    Text("Add account", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                HintText(
                                    "Partner sign-ins can also be created from a company's page (Partners → Create partner sign-in). " +
                                        "Passwords are shown once at creation.",
                                )
                            }
                        }
                        when {
                            state.isLoading -> item { LoadingRow() }
                            error != null -> Unit
                            state.accounts.isEmpty() -> item { EmptyText("No admin accounts yet — create one below.") }
                            else -> items(state.accounts, key = { it.id }) { account ->
                                AccountRow(
                                    account = account,
                                    enabled = !state.isActing,
                                    onReset = { pendingResetId = account.id },
                                    onDelete = { pendingDeleteId = account.id },
                                )
                            }
                        }
                    }

                    DataSection.HOLDS -> when {
                        state.isLoading -> item { LoadingRow() }
                        error != null -> Unit
                        state.holds.isEmpty() -> item { EmptyText("No live holds right now.") }
                        else -> items(state.holds, key = { "${it.tripId}/${it.seatId}" }) { hold ->
                            HoldRow(hold, onClick = { viewModel.openDetail(DataDetail.Hold(hold)) })
                        }
                    }

                    DataSection.USERS -> when {
                        state.isLoading -> item { LoadingRow() }
                        error != null -> Unit
                        state.users.isEmpty() -> item { EmptyText("No passenger accounts yet.") }
                        else -> items(state.users, key = { it.id }) { user ->
                            UserRow(user, onClick = { viewModel.openDetail(DataDetail.User(user)) })
                        }
                    }

                    DataSection.TERMINALS -> when {
                        state.isLoading -> item { LoadingRow() }
                        error != null -> Unit
                        state.terminals.isEmpty() -> item { EmptyText("No terminals loaded.") }
                        else -> items(state.terminals, key = { it.name }) { terminal -> TerminalRow(terminal) }
                    }

                    DataSection.ROUTES -> {
                        item {
                            OutlinedTextField(
                                value = state.routeQuery,
                                onValueChange = viewModel::onRouteQueryChanged,
                                label = { Text("Search routes") },
                                placeholder = { Text("Origin or destination") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { viewModel.load() }),
                                trailingIcon = {
                                    IconButton(onClick = viewModel::load) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search routes")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        when {
                            state.isLoading -> item { LoadingRow() }
                            error != null -> Unit
                            state.routes.isEmpty() -> item { EmptyText("No routes match.") }
                            else -> items(state.routes, key = { it.id }) { route ->
                                RouteRow(route, onClick = { viewModel.openDetail(DataDetail.Route(route)) })
                            }
                        }
                    }

                    DataSection.JOURNEYS -> when {
                        state.isLoading -> item { LoadingRow() }
                        error != null -> Unit
                        state.journeys.isEmpty() -> item { EmptyText("No curated journeys.") }
                        else -> items(state.journeys, key = { it.id }) { journey -> JourneyCard(journey) }
                    }
                }
            }
        }
    }

    if (showCreateAccount) {
        LaunchedEffect(Unit) { viewModel.ensureOperatorsLoaded() }
        CreateAccountDialog(
            operators = state.operators,
            onCreate = { email, role, operatorId, password ->
                showCreateAccount = false
                viewModel.createAccount(email, role, operatorId, password)
            },
            onDismiss = { showCreateAccount = false },
        )
    }
    pendingResetId?.let { id -> state.accounts.firstOrNull { it.id == id } }?.let { account ->
        ResetPasswordDialog(
            account = account,
            onConfirm = { password ->
                pendingResetId = null
                viewModel.resetPassword(account, password)
            },
            onDismiss = { pendingResetId = null },
        )
    }
    pendingDeleteId?.let { id -> state.accounts.firstOrNull { it.id == id } }?.let { account ->
        ConfirmDialog(
            title = "Delete account?",
            text = "Delete the account ${account.email}? Their sessions end immediately.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingDeleteId = null
                viewModel.deleteAccount(account)
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
    state.issuedCredential?.let { credential ->
        val generated = credential.generatedPassword
        // A reset signs the account out everywhere else; say so, or the admin
        // will be surprised when the operator's other phone asks to log in again.
        val revokedNote = credential.revokedSessions
            ?.takeIf { it > 0 }
            ?.let { " Their $it other sign-in${if (it == 1) "" else "s"} ended." }
            .orEmpty()
        if (generated != null) {
            SecretRevealDialog(
                title = "Temporary password",
                message = "${credential.email} — copy this password now; it is shown once.$revokedNote",
                secret = generated,
                secretLabel = "Temporary password",
                onDismiss = viewModel::dismissCredential,
            )
        } else {
            AlertDialog(
                onDismissRequest = viewModel::dismissCredential,
                title = { Text("Account ready", fontWeight = FontWeight.Bold) },
                text = { Text("${credential.email} can sign in with the password you entered.$revokedNote") },
                confirmButton = { TextButton(onClick = viewModel::dismissCredential) { Text("Done") } },
            )
        }
    }
    state.detail?.let { detail ->
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
                when (detail) {
                    is DataDetail.User -> UserDetail(detail.user)
                    is DataDetail.Hold -> HoldDetail(detail.hold)
                    is DataDetail.Route -> RouteDetail(detail.route)
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: StaffAccount, enabled: Boolean, onReset: () -> Unit, onDelete: () -> Unit) {
    RowCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                account.email,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Pill(account.role.name, tone = if (account.role == StaffRole.ADMIN) PillTone.MINT else PillTone.PLAIN)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LabeledText("Company", account.operatorName ?: "—", modifier = Modifier.weight(1f))
            LabeledText("Password", if (account.hasPassword) "set" else "none (Google / key only)", modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LabeledText("Created", formatPhDateTime(account.createdAtEpochMillis), modifier = Modifier.weight(1f))
            LabeledText("Last sign-in", formatPhDateTime(account.lastLoginAtEpochMillis), modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onReset, enabled = enabled) { Text("Reset password") }
            TextButton(onClick = onDelete, enabled = enabled) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun HoldRow(hold: LiveHold, onClick: () -> Unit) {
    RowCard(onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SeatChips(listOf(hold.label))
            Spacer(modifier = Modifier.width(10.dp))
            Text("${hold.origin} → ${hold.destination}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LabeledText("Departs", formatPhDateTime(hold.departureEpochMillis), modifier = Modifier.weight(1f))
            LabeledText("Expires", formatPhDateTime(hold.expiresAtEpochMillis), modifier = Modifier.weight(1f))
        }
        LabeledText("Held by", "user ${shortId(hold.userId, 8)}")
    }
}

@Composable
private fun HoldDetail(hold: LiveHold) {
    Text("Seat ${hold.label} — live hold", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    LabeledValue("Trip") {
        Text("${hold.origin} → ${hold.destination}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(formatPhDateTime(hold.departureEpochMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LabeledValue("Trip id") { CodeText(hold.tripId) }
    LabeledValue("Seat id") { CodeText(hold.seatId) }
    LabeledValue("Held by user") { CodeText(hold.userId) }
    LabeledText("Hold expires (PH)", formatPhDateTime(hold.expiresAtEpochMillis))
    HintText("Holds lapse on their own; the seat returns to inventory when the timer runs out.")
}

@Composable
private fun UserRow(user: PassengerAccount, onClick: () -> Unit) {
    RowCard(onClick = onClick) {
        CodeText(user.id)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LabeledText("First seen", formatPhDateTime(user.createdAtEpochMillis), modifier = Modifier.weight(1.4f))
            LabeledText("Bookings", user.bookings.toString(), modifier = Modifier.weight(0.8f))
            LabeledText("Cancelled", user.cancelled.toString(), modifier = Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun UserDetail(user: PassengerAccount) {
    Text("Passenger account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    LabeledValue("User id") { CodeText(user.id) }
    LabeledText("First seen (PH)", formatPhDateTime(user.createdAtEpochMillis))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LabeledText("Bookings", user.bookings.toString())
        LabeledText("Cancelled", user.cancelled.toString())
    }
    HintText("Riders are identified by device (X-Device-Id); there is no name or email on the account. Find their tickets in Support by user id.")
}

@Composable
private fun TerminalRow(terminal: TerminalLocation) {
    RowCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                terminal.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(humanize(terminal.kind.name), PillTone.PLAIN)
                if (terminal.isCentralTerminal) Pill("CENTRAL", PillTone.MINT)
            }
        }
        if (terminal.region.isNotBlank()) {
            Text(terminal.region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (terminal.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(terminal.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RouteRow(route: RouteSummary, onClick: () -> Unit) {
    RowCard(onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${route.origin} → ${route.destination}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Pill(route.mode, PillTone.PLAIN)
        }
        Text(
            "${route.services} service${if (route.services == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouteDetail(route: RouteSummary) {
    Text("${route.origin} → ${route.destination}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    LabeledText("Route id", route.id.toString())
    LabeledValue("Mode") { Pill(route.mode, PillTone.PLAIN) }
    LabeledText("Services", "${route.services} service${if (route.services == 1) "" else "s"} run this direction")
    HintText("Directed: the return direction is a separate route. Operators and fares are on the Partners tab.")
}

@Composable
private fun JourneyCard(journey: CuratedJourney) {
    RowCard {
        Text(journey.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "${journey.from} → ${journey.to}" +
                if (journey.matchKeywords.isNotEmpty()) " · keywords: ${journey.matchKeywords.joinToString(", ")}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        journey.legs.forEachIndexed { index, leg ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}. ${leg.from} → ${leg.to}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Pill(leg.kind.name, PillTone.MINT)
                }
                Text(
                    "${formatDuration(leg.durationMinutes)} · ${formatPhp(leg.indicativeFarePhp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                leg.note?.let { HintText(it) }
            }
        }
    }
}

/** Generate a password (shown once) or set one by hand — the same `password` field the API already takes. */
@Composable
private fun ResetPasswordDialog(
    account: StaffAccount,
    onConfirm: (password: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var setByHand by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val tooShort = setByHand && password.length < MIN_STAFF_PASSWORD_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("New password for ${account.email}:")
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !setByHand, onClick = { setByHand = false }, label = { Text("Generate") })
                    FilterChip(selected = setByHand, onClick = { setByHand = true }, label = { Text("Set a password") })
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (setByHand) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = password.isNotEmpty() && tooShort,
                        supportingText = { Text("At least $MIN_STAFF_PASSWORD_LENGTH characters") },
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
                } else {
                    HintText("A random password is generated and shown once so you can pass it on.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(if (setByHand) password else null) },
                enabled = !tooShort,
            ) { Text(if (setByHand) "Set password" else "Generate", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** ADMIN or PARTNER account; PARTNER needs a company. Blank password → generated, shown once. */
@Composable
private fun CreateAccountDialog(
    operators: List<OperatorSummary>,
    onCreate: (email: String, role: StaffRole, operatorId: Int?, password: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(StaffRole.ADMIN) }
    var operatorId by rememberSaveable { mutableStateOf<Int?>(null) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var operatorMenuOpen by rememberSaveable { mutableStateOf(false) }
    val passwordTooShort = password.isNotEmpty() && password.length < MIN_STAFF_PASSWORD_LENGTH

    val canCreate = email.isNotBlank() && !passwordTooShort && (role == StaffRole.ADMIN || operatorId != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                MicroLabel("Role")
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StaffRole.values().forEach { option ->
                        FilterChip(
                            selected = role == option,
                            onClick = { role = option },
                            label = { Text(option.name) },
                        )
                    }
                }
                if (role == StaffRole.PARTNER) {
                    Spacer(modifier = Modifier.height(10.dp))
                    MicroLabel("Company")
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(onClick = { operatorMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                operators.firstOrNull { it.id == operatorId }?.name
                                    ?: if (operators.isEmpty()) "Loading partners…" else "Pick a company",
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = operatorMenuOpen, onDismissRequest = { operatorMenuOpen = false }) {
                            operators.forEach { operator ->
                                DropdownMenuItem(
                                    text = { Text(operator.name) },
                                    onClick = {
                                        operatorId = operator.id
                                        operatorMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
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
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(email, role, operatorId, password.ifBlank { null }) }, enabled = canCreate) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
