package com.ridevibe.feature.admin.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.ridevibe.core.domain.model.StaffSession
import com.ridevibe.feature.admin.ui.components.EmptyText
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.RowCard
import com.ridevibe.feature.admin.ui.components.staffTopBarColors
import com.ridevibe.feature.admin.viewmodel.AdminPartnersViewModel
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
 */
@Composable
fun AdminConsole(
    session: StaffSession,
    onExit: () -> Unit,
    onSignOut: () -> Unit,
    onOpenPartnerPortal: (operatorId: Int) -> Unit,
    partnersViewModel: AdminPartnersViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.OVERVIEW) }
    var showPartnerPicker by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    BackHandler(enabled = tab != AdminTab.OVERVIEW) { tab = AdminTab.OVERVIEW }

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
                    IconButton(onClick = onSignOut) {
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
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AdminTab.OVERVIEW -> AdminOverviewTab(onMessage = onMessage)
                AdminTab.SUPPORT -> AdminSupportTab(onMessage = onMessage)
                AdminTab.TRIPS -> AdminTripsTab(onMessage = onMessage)
                AdminTab.PARTNERS -> AdminPartnersTab(
                    onMessage = onMessage,
                    onViewAsPartner = onOpenPartnerPortal,
                    viewModel = partnersViewModel,
                )
                AdminTab.DATA -> AdminDataTab(onMessage = onMessage)
            }
        }
    }

    if (showPartnerPicker) {
        PartnerPickerDialog(
            viewModel = partnersViewModel,
            onPick = { operatorId ->
                showPartnerPicker = false
                onOpenPartnerPortal(operatorId)
            },
            onDismiss = { showPartnerPicker = false },
        )
    }
}

/**
 * "Open partner portal": pick which partner to view — the admin enters their
 * portal read-only, with a company switcher to move between partners.
 */
@Composable
fun PartnerPickerDialog(
    viewModel: AdminPartnersViewModel,
    onPick: (operatorId: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var filter by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.ensureOperatorsLoaded() }

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
                InlineError(state.error)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        state.isLoading -> LoadingRow()
                        shown.isEmpty() -> EmptyText("No partner matches.")
                        else -> shown.forEach { operator ->
                            RowCard(onClick = { onPick(operator.id) }, modifier = Modifier.padding(bottom = 6.dp)) {
                                Row {
                                    Text(operator.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
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
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
