package com.ridevibe.feature.admin.ui.partner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.ridevibe.feature.admin.ui.admin.PartnerPickerDialog
import com.ridevibe.feature.admin.ui.components.NoteCard
import com.ridevibe.feature.admin.ui.components.staffTopBarColors
import com.ridevibe.feature.admin.viewmodel.AdminPartnersViewModel
import com.ridevibe.feature.admin.viewmodel.PartnerOverviewViewModel
import kotlinx.coroutines.launch

/** The four portal views, in sidebar order. */
enum class PartnerTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Dashboard),
    SERVICES("My services", Icons.Filled.AltRoute),
    TRIPS("Trips", Icons.Filled.DirectionsBus),
    MANIFEST("Manifest", Icons.Filled.Groups),
}

/**
 * The operator's self-service portal: `admin/partner.html` as a tabbed screen.
 *
 * A PARTNER session passes [operatorId] null and is writable. An ADMIN session
 * passes the operator it wants to view and [readOnly] true: every write is
 * hidden here and refused by the server anyway. [onSwitchToAdmin] returns to
 * the admin console; [onSwitchOperator] is the top-bar company switcher.
 */
@Composable
fun PartnerPortal(
    session: StaffSession,
    operatorId: Int?,
    readOnly: Boolean,
    onExit: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchToAdmin: (() -> Unit)? = null,
    onSwitchOperator: ((operatorId: Int) -> Unit)? = null,
    overviewViewModel: PartnerOverviewViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(PartnerTab.OVERVIEW) }
    var showSwitcher by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    val overviewState by overviewViewModel.uiState.collectAsState()

    // Back: leave a non-Overview tab first; an admin view then returns to the console.
    BackHandler(enabled = tab != PartnerTab.OVERVIEW || onSwitchToAdmin != null) {
        if (tab != PartnerTab.OVERVIEW) tab = PartnerTab.OVERVIEW else onSwitchToAdmin?.invoke()
    }

    val operatorName = overviewState.overview?.operatorName ?: session.operatorName ?: "Partner portal"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(operatorName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (readOnly) "Partner portal · viewed by ${session.email}" else session.email,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (onSwitchToAdmin != null) {
                        TextButton(onClick = onSwitchToAdmin) {
                            Text("Admin view", color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (onSwitchOperator != null) {
                        IconButton(onClick = { showSwitcher = true }) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch company")
                        }
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
                PartnerTab.values().forEach { item ->
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (readOnly) {
                NoteCard(
                    bold = "Admin view — read-only.",
                    text = "Service changes and on-site sales can only be made by the operator.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    PartnerTab.OVERVIEW -> PartnerOverviewTab(operatorId = operatorId, viewModel = overviewViewModel)
                    PartnerTab.SERVICES -> PartnerServicesTab(operatorId = operatorId, readOnly = readOnly, onMessage = onMessage)
                    PartnerTab.TRIPS -> PartnerTripsTab(operatorId = operatorId, readOnly = readOnly, onMessage = onMessage)
                    PartnerTab.MANIFEST -> PartnerManifestTab(operatorId = operatorId)
                }
            }
        }
    }

    if (showSwitcher && onSwitchOperator != null) {
        val partnersViewModel: AdminPartnersViewModel = hiltViewModel()
        PartnerPickerDialog(
            viewModel = partnersViewModel,
            onPick = { picked ->
                showSwitcher = false
                if (picked != operatorId) {
                    tab = PartnerTab.OVERVIEW
                    onSwitchOperator(picked)
                }
            },
            onDismiss = { showSwitcher = false },
        )
    }
}
