package com.ridevibe.feature.admin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ridevibe.feature.admin.ui.components.ButtonSpinner
import com.ridevibe.feature.admin.ui.components.HintText
import com.ridevibe.feature.admin.ui.components.InlineError
import com.ridevibe.feature.admin.ui.components.LoadingRow
import com.ridevibe.feature.admin.ui.components.NoteCard
import com.ridevibe.feature.admin.ui.components.PageHeader
import com.ridevibe.feature.admin.ui.components.SectionCard
import com.ridevibe.feature.admin.ui.components.staffTopBarColors
import com.ridevibe.feature.admin.viewmodel.StaffLoginViewModel

/**
 * The dashboards' login page: email + password, or an emailed one-time
 * sign-in key. Success is not handled here — the session flow flips the
 * console root to the signed-in surface.
 */
@Composable
fun StaffLoginScreen(
    onExit: () -> Unit,
    sessionError: String? = null,
    onSessionErrorShown: () -> Unit = {},
    viewModel: StaffLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // A failed session refresh (network) is worth one line under the form, then forgotten.
    LaunchedEffect(sessionError) { if (sessionError != null) onSessionErrorShown() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Staff sign-in", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Filled.Close, contentDescription = "Close staff console")
                    }
                },
                colors = staffTopBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PageHeader(
                "RideVibe staff",
                "For RideVibe admins and partner operators. Accounts are created by an admin under Data → Accounts.",
            )

            if (state.isLoading) LoadingRow()

            SectionCard("Email and password") {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChanged,
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = viewModel::signIn,
                    enabled = state.canSignIn,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.isSigningIn) ButtonSpinner() else Text("Sign in", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (state.options?.emailKeyEnabled != false) {
                SectionCard("Email me a sign-in key") {
                    HintText(
                        "A 6-digit key valid for 10 minutes is sent to the email above. " +
                            "Works for accounts without a password too.",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = viewModel::requestEmailKey,
                        enabled = state.canRequestKey,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isRequestingKey) {
                            ButtonSpinner()
                        } else {
                            Text(if (state.keyRequested) "Send a new key" else "Email me a sign-in key")
                        }
                    }
                    if (state.keyRequested) {
                        Spacer(modifier = Modifier.height(10.dp))
                        state.keyMessage?.let { HintText(it) }
                        state.devCode?.let { code ->
                            Spacer(modifier = Modifier.height(8.dp))
                            NoteCard(
                                bold = "Development server — your key is $code",
                                text = "Shown here only because the server is not in production and has no mail " +
                                    "configured. Real deployments email the key.",
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.code,
                            onValueChange = viewModel::onCodeChanged,
                            label = { Text("6-digit key") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = viewModel::verifyEmailKey,
                            enabled = state.canVerifyKey,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            if (state.isVerifyingKey) ButtonSpinner() else Text("Verify and sign in", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (state.options?.googleEnabled == true) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
                HintText(
                    "Google sign-in for staff is configured on the web dashboard; " +
                        "in the app, use your email and password or a sign-in key.",
                )
            }

            InlineError(state.error ?: sessionError, modifier = Modifier.padding(top = 12.dp))
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
