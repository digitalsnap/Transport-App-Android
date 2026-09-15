package com.ridevibe.feature.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.feature.admin.ui.admin.AdminConsole
import com.ridevibe.feature.admin.ui.partner.PartnerPortal
import com.ridevibe.feature.admin.viewmodel.StaffSessionViewModel

/** Sentinel for "not viewing a partner" — rememberSaveable prefers a plain Int over a nullable. */
private const val NO_OPERATOR = -1

/**
 * Entry point of the staff console: the two web dashboards (`/admin`,
 * `/partner`) consolidated into the app behind one role-gated root.
 *
 * - no session → [StaffLoginScreen]
 * - PARTNER session → that operator's [PartnerPortal], writable
 * - ADMIN session → [AdminConsole]; "Partner portal" / "View as partner" open a
 *   read-only [PartnerPortal] for the chosen operator, with a company switcher
 *   and an "Admin view" action to come back — exactly the web hand-off.
 *
 * Back inside the console steps back through these states before [onExit].
 */
@Composable
fun StaffConsoleRoot(
    onExit: () -> Unit,
    sessionViewModel: StaffSessionViewModel = hiltViewModel(),
) {
    val state by sessionViewModel.uiState.collectAsState()
    var viewingOperatorId by rememberSaveable { mutableStateOf(NO_OPERATOR) }

    // Drop a stale persisted session once per visit; the repository clears it on 401.
    LaunchedEffect(Unit) { sessionViewModel.refreshSession() }

    val session = state.session
    // A new sign-in (or sign-out) always lands on that role's home, never on a leftover partner view.
    LaunchedEffect(session?.token) { viewingOperatorId = NO_OPERATOR }

    when {
        session == null -> StaffLoginScreen(
            onExit = onExit,
            sessionError = state.error,
            onSessionErrorShown = sessionViewModel::consumeError,
        )

        session.role == StaffRole.PARTNER -> PartnerPortal(
            session = session,
            operatorId = null,
            readOnly = false,
            onExit = onExit,
            onSignOut = sessionViewModel::signOut,
        )

        viewingOperatorId != NO_OPERATOR -> PartnerPortal(
            session = session,
            operatorId = viewingOperatorId,
            readOnly = true,
            onExit = onExit,
            onSignOut = sessionViewModel::signOut,
            onSwitchToAdmin = { viewingOperatorId = NO_OPERATOR },
            onSwitchOperator = { viewingOperatorId = it },
        )

        else -> AdminConsole(
            session = session,
            onExit = onExit,
            onSignOut = sessionViewModel::signOut,
            onOpenPartnerPortal = { viewingOperatorId = it },
        )
    }
}
