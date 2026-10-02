package com.ridevibe.feature.admin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * The dashboards' login page: email + password, an emailed one-time sign-in
 * key, or Google. Success is not handled here — the session flow flips the
 * console root to the signed-in surface.
 *
 * [onGoogleSignIn] is supplied by the host app, which owns Play Services:
 * it receives the server's web-client id, runs the Google flow and answers
 * with the ID token or the reason there is none. Null hides the Google button
 * entirely. [googleResult] is the same answer arriving late, after an
 * Activity recreation lost the callback; it is consumed once handled.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StaffLoginScreen(
    onExit: () -> Unit,
    sessionError: String? = null,
    onDismissSessionError: () -> Unit = {},
    onGoogleSignIn: ((clientId: String, onResult: (Result<String>) -> Unit) -> Unit)? = null,
    googleResult: Result<String>? = null,
    onConsumeGoogleResult: () -> Unit = {},
    viewModel: StaffLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(googleResult) {
        if (googleResult != null) {
            viewModel.onGoogleResult(googleResult)
            onConsumeGoogleResult()
        }
    }

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

            // A dropped session is worth a card that stays until the person reads it, not a one-frame flash.
            if (sessionError != null) {
                DismissibleNotice(text = sessionError, onDismiss = onDismissSessionError)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (state.isLoading) LoadingRow()

            state.optionsError?.let { optionsError ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    InlineError(optionsError, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::loadOptions) { Text("Retry") }
                }
            }

            SectionCard("Email and password") {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChanged,
                    label = { Text("Email") },
                    singleLine = true,
                    isError = state.showEmailError,
                    supportingText = if (state.showEmailError) ({ Text("Enter a valid email address") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .staffAutofill(AutofillType.EmailAddress, onFill = viewModel::onEmailChanged)
                        .onFocusChanged { if (!it.isFocused) viewModel.onEmailFocusLost() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.signIn()
                        },
                    ),
                    trailingIcon = {
                        IconButton(onClick = viewModel::togglePasswordVisibility) {
                            Icon(
                                if (state.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (state.passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .staffAutofill(AutofillType.Password, onFill = viewModel::onPasswordChanged),
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
                        // No BuildConfig in this module (buildConfig is off), so the gate is the server's
                        // own behaviour: it only ever includes devCode outside production.
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.verifyEmailKey()
                                },
                            ),
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

            val googleClientId = state.googleClientId
            if (googleClientId != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
                if (onGoogleSignIn != null) {
                    OutlinedButton(
                        onClick = { onGoogleSignIn(googleClientId, viewModel::onGoogleResult) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (state.isSigningInWithGoogle) {
                            ButtonSpinner()
                        } else {
                            Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    HintText(
                        "Google sign-in for staff is configured on the web dashboard; " +
                            "in the app, use your email and password or a sign-in key.",
                    )
                }
            }

            InlineError(state.error, modifier = Modifier.padding(top = 12.dp))
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Error-tinted card with a Dismiss action; stays until the person closes it. */
@Composable
private fun DismissibleNotice(text: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 4.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            TextButton(onClick = onDismiss) { Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

/**
 * Registers the field with the platform autofill service so password managers
 * offer saved staff credentials. Compose 1.7 has no `contentType` semantics
 * yet (that arrived with the 1.8 autofill rewrite), hence the explicit
 * [AutofillNode] registration.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.staffAutofill(type: AutofillType, onFill: (String) -> Unit): Modifier = composed {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val node = androidx.compose.runtime.remember(type) { AutofillNode(autofillTypes = listOf(type), onFill = onFill) }
    autofillTree += node
    this
        .onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .onFocusChanged { focusState ->
            autofill?.run {
                if (focusState.isFocused) requestAutofillForNode(node) else cancelAutofillForNode(node)
            }
        }
}
