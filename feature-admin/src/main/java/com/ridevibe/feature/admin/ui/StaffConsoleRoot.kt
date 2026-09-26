package com.ridevibe.feature.admin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.feature.admin.ui.admin.AdminConsole
import com.ridevibe.feature.admin.ui.components.staffTopBarColors
import com.ridevibe.feature.admin.ui.partner.PartnerPortal
import com.ridevibe.feature.admin.viewmodel.StaffSessionViewModel
import kotlinx.coroutines.delay

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
 *
 * [onGoogleSignIn] is optional: the host app, which has Play Services, runs
 * the Google flow for the given web-client id and returns the ID token.
 * Without it the sign-in screen hides the Google button.
 */
@Composable
fun StaffConsoleRoot(
    onExit: () -> Unit,
    onGoogleSignIn: ((clientId: String, onToken: (String) -> Unit) -> Unit)? = null,
    sessionViewModel: StaffSessionViewModel = hiltViewModel(),
) {
    val state by sessionViewModel.uiState.collectAsStateWithLifecycle()
    var viewingOperatorId by rememberSaveable { mutableStateOf(NO_OPERATOR) }

    // Drop a stale persisted session once per visit; the repository clears it on 401.
    LaunchedEffect(Unit) { sessionViewModel.refreshSession() }

    val session = state.session
    // A new sign-in (or sign-out) always lands on that role's home, never on a leftover partner view.
    LaunchedEffect(session?.token) { viewingOperatorId = NO_OPERATOR }

    // Sign out the moment the local expiry passes while the console is open, instead of
    // letting the next call fail with a bare 401.
    LaunchedEffect(session?.token, session?.expiresAtEpochMillis) {
        val expiresAt = session?.expiresAtEpochMillis ?: return@LaunchedEffect
        delay((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
        sessionViewModel.onSessionExpired()
    }

    when {
        session == null -> StaffLoginScreen(
            onExit = onExit,
            sessionError = state.error,
            onDismissSessionError = sessionViewModel::consumeError,
            onGoogleSignIn = onGoogleSignIn,
        )

        state.isLoading -> SessionCheckScreen(email = session.email, onExit = onExit)

        session.role == StaffRole.PARTNER -> PartnerPortal(
            session = session,
            sessionKey = state.sessionKey,
            operatorId = null,
            readOnly = false,
            onExit = onExit,
            onSignOut = sessionViewModel::signOut,
        )

        viewingOperatorId != NO_OPERATOR -> PartnerPortal(
            session = session,
            sessionKey = state.sessionKey,
            operatorId = viewingOperatorId,
            readOnly = true,
            onExit = onExit,
            onSignOut = sessionViewModel::signOut,
            onSwitchToAdmin = { viewingOperatorId = NO_OPERATOR },
            onSwitchOperator = { viewingOperatorId = it },
        )

        else -> AdminConsole(
            session = session,
            sessionKey = state.sessionKey,
            onExit = onExit,
            onSignOut = sessionViewModel::signOut,
            onOpenPartnerPortal = { viewingOperatorId = it },
        )
    }
}

/** Shown while `/auth/me` confirms the persisted session — a second at most, but never a blank screen. */
@Composable
private fun SessionCheckScreen(email: String, onExit: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Staff console", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Filled.Close, contentDescription = "Close staff console")
                    }
                },
                colors = staffTopBarColors(),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Checking your session…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
