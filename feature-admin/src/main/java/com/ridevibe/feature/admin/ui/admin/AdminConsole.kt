package com.ridevibe.feature.admin.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.feature.admin.ui.components.ConfirmDialog
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.ErrorWithRetry
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.RowCard
import com.ridevibe.feature.admin.ui.components.StaffWideLayoutMinWidth
import com.ridevibe.feature.admin.ui.components.staffTopBarColors
import com.ridevibe.feature.admin.viewmodel.AdminPartnersViewModel
import com.ridevibe.feature.admin.viewmodel.AdminSupportViewModel
import com.ridevibe.feature.admin.viewmodel.SupportFilterPreset
import kotlinx.coroutines.launch

/** The five dashboard views, in sidebar order. */
enum class AdminTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Dashboard),
    SUPPORT("Support", Icons.Filled.SupportAgent),
    TRIPS("Trips", Icons.Filled.DirectionsBus),
    PARTNERS("Partners", Icons.Filled.Storefront),
    DATA("Data", Icons.Filled.Storage),
}

/**
 * The admin operations console: `admin/dashboard.html` as a tabbed screen.
 * Back on a non-Overview tab returns to Overview before the console exits.
 *
 * On a tablet-width window (≥ 600 dp) the tabs move to a navigation rail on
 * the left, the way the web sidebar sits, and the stat grids widen.
 * [sessionKey] identifies the signed-in account for the tab view models.
 */
@Composable
fun AdminConsole(
    session: StaffSession,
    sessionKey: String,
    onExit: () -> Unit,
    onSignOut: () -> Unit,
    onOpenPartnerPortal: (operatorId: Int) -> Unit,
    partnersViewModel: AdminPartnersViewModel = hiltViewModel(),
    supportViewModel: AdminSupportViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.OVERVIEW) }
    var showPartnerPicker by rememberSaveable { mutableStateOf(false) }
    var showSignOutConfirm by rememberSaveable { mutableStateOf(false) }
    // A tile on Overview can send the admin to Support with a filter; consumed once applied.
    var pendingSupportPreset by rememberSaveable { mutableStateOf<SupportFilterPreset?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    BackHandler(enabled = tab != AdminTab.OVERVIEW) { tab = AdminTab.OVERVIEW }

    BoxWithConstraints {
        val wide = maxWidth >= StaffWideLayoutMinWidth
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("RideVibe Admin", fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                session.email,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showPartnerPicker = true }) {
                            Icon(Icons.Filled.Storefront, contentDescription = "Partner portal")
                        }
                        IconButton(onClick = { showSignOutConfirm = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                        }
                        IconButton(onClick = onExit) {
                            Icon(Icons.Filled.Close, contentDescription = "Close staff console")
                        }
                    },
                    colors = staffTopBarColors(),
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        AdminTab.values().forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        AdminTab.values().forEach { item ->
                            NavigationRailItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when (tab) {
                        AdminTab.OVERVIEW -> AdminOverviewTab(
                            sessionKey = sessionKey,
                            wide = wide,
                            onMessage = onMessage,
                            onOpenSupport = { preset ->
                                pendingSupportPreset = preset
                                tab = AdminTab.SUPPORT
                            },
                            supportViewModel = supportViewModel,
                        )
                        AdminTab.SUPPORT -> AdminSupportTab(
                            sessionKey = sessionKey,
                            onMessage = onMessage,
                            initialFilter = pendingSupportPreset,
                            onInitialFilterApplied = { pendingSupportPreset = null },
                            viewModel = supportViewModel,
                        )
                        AdminTab.TRIPS -> AdminTripsTab(sessionKey = sessionKey, onMessage = onMessage)
                        AdminTab.PARTNERS -> AdminPartnersTab(
                            sessionKey = sessionKey,
                            onMessage = onMessage,
                            onViewAsPartner = onOpenPartnerPortal,
                            viewModel = partnersViewModel,
                        )
                        AdminTab.DATA -> AdminDataTab(sessionKey = sessionKey, onMessage = onMessage)
                    }
                }
            }
        }
    }

    if (showPartnerPicker) {
        PartnerPickerDialog(
            sessionKey = sessionKey,
            viewModel = partnersViewModel,
            onPick = { operatorId ->
                showPartnerPicker = false
                onOpenPartnerPortal(operatorId)
            },
            onDismiss = { showPartnerPicker = false },
        )
    }
    if (showSignOutConfirm) {
        SignOutDialog(
            onConfirm = {
                showSignOutConfirm = false
                onSignOut()
            },
            onDismiss = { showSignOutConfirm = false },
        )
    }
}

/** Sign-out always asks: a half-typed note or an unsaved service form would be lost with the session. */
@Composable
fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Sign out?",
        text = "Anything you have not saved on this console is discarded.",
        confirmLabel = "Sign out",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * "Open partner portal": pick which partner to view — the admin enters their
 * portal read-only, with a company switcher to move between partners.
 */
@Composable
fun PartnerPickerDialog(
    sessionKey: String,
    viewModel: AdminPartnersViewModel,
    onPick: (operatorId: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(sessionKey) { viewModel.ensureOperatorsLoaded(sessionKey) }

    val shown = remember(state.operators, filter) {
        val query = filter.trim().lowercase()
        state.operators.filter { query.isEmpty() || it.name.lowercase().contains(query) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open partner portal", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                HintText(
                    "Pick which partner to view — you'll enter their portal read-only, " +
                        "with a company switcher to move between partners.",
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Search partners…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        state.isLoading -> LoadingRow()
                        state.error != null && state.operators.isEmpty() -> ErrorWithRetry(state.error!!, onRetry = viewModel::load)
                        shown.isEmpty() -> EmptyText("No partner matches.")
                        else -> shown.forEach { operator ->
                            RowCard(onClick = { onPick(operator.id) }, modifier = Modifier.padding(bottom = 6.dp)) {
                                Text(operator.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${operator.routes} routes · ${operator.services} services",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
