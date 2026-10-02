package com.ridevibe.app.ui.welcome

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.GraphRequest
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.ridevibe.app.R
import com.ridevibe.app.auth.PassengerSessionViewModel

/** Facebook's brand blue for the "f" mark: the one hard-coded colour the design allows. */
private val FacebookBlue = Color(0xFF1877F2)

/**
 * First screen for a device with no session. [sessionViewModel] is the
 * activity-scoped session so Profile later sees the same identity; [onSignedIn]
 * and [onGetStarted] both land on Home, the latter as a guest.
 */
@SuppressLint("DiscouragedApi") // default_web_client_id is resolved by name on purpose; see below.
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSignedIn: () -> Unit,
    sessionViewModel: PassengerSessionViewModel,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        message = null
    }

    // The web client id is generated into resources by the google-services plugin
    // from google-services.json. It is looked up by name so a build whose JSON
    // has no web OAuth client still compiles: then there is no ID token to
    // request and sign-in falls back to profile + email only.
    val webClientId = remember(context) {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id != 0) context.getString(id).takeIf { it.isNotBlank() } else null
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            sessionViewModel.onGoogleSignedIn(account)
            currentOnSignedIn()
        } catch (e: ApiException) {
            message = googleSignInMessage(e.statusCode)
        }
    }

    // One CallbackManager per activity, its callback registered once; the
    // Facebook SDK is only initialised when an App ID is configured.
    val facebookEnabled = remember(context) { context.getString(R.string.facebook_app_id).isNotBlank() }
    val facebookCallbackManager = remember(context) { CallbackManager.Factory.create() }
    DisposableEffect(facebookCallbackManager, facebookEnabled) {
        if (!facebookEnabled) return@DisposableEffect onDispose {}
        if (!FacebookSdk.isInitialized()) FacebookSdk.fullyInitialize()
        val loginManager = LoginManager.getInstance()
        loginManager.registerCallback(
            facebookCallbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val token = result.accessToken
                    // Name and email come from the Graph API; a failed lookup still
                    // signs the rider in, just without a display name.
                    val request = GraphRequest.newMeRequest(token) { profile, _ ->
                        sessionViewModel.onFacebookSignedIn(
                            displayName = profile?.optString("name")?.takeIf { it.isNotBlank() },
                            email = profile?.optString("email")?.takeIf { it.isNotBlank() },
                            accessToken = token.token,
                        )
                        currentOnSignedIn()
                    }
                    request.parameters = Bundle().apply { putString("fields", "id,name,email") }
                    request.executeAsync()
                }

                override fun onCancel() {
                    message = "Facebook sign-in cancelled."
                }

                override fun onError(error: FacebookException) {
                    message = "Facebook sign-in failed: ${error.message ?: "unknown error"}"
                }
            },
        )
        onDispose { loginManager.unregisterCallback(facebookCallbackManager) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter = painterResource(R.drawable.ic_ridevibe_logo),
                contentDescription = "RideVibe logo",
                modifier = Modifier.size(84.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = buildAnnotatedString {
                    append("Your Journey,\n")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("Simplified.")
                    }
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Secure your seat in seconds. Modern bus ticketing for the everyday commuter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Hero panel: sample photography standing in for final brand shots.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.hero_travel),
                    contentDescription = "Scenic travel photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                            ),
                        ),
                )
                // Sits on the photo, not on a themed surface, so it uses the
                // "on primary" role: white in both schemes.
                Text(
                    "Verified Safety",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    sessionViewModel.continueAsGuest()
                    onGetStarted()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("welcome_get_started"),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Get Started  →", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "or continue with",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .apply { if (webClientId != null) requestIdToken(webClientId) }
                        .build()
                    googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("welcome_google"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_google_g),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text("Continue with Google", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { startFacebookLogin(context, facebookEnabled, facebookCallbackManager) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("welcome_facebook"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                // The "f" mark keeps Facebook's brand blue; the button matches the white social cards.
                Text("f", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FacebookBlue)
                Spacer(modifier = Modifier.size(10.dp))
                Text("Continue with Facebook", fontWeight = FontWeight.SemiBold)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // TODO(auth): route to an email/password sign-in once the CRS has
                // passenger accounts (docs/DEVELOPER-ACTIONS.md §B); until then this is the guest path.
                TextButton(onClick = {
                    sessionViewModel.continueAsGuest()
                    onGetStarted()
                }) {
                    Text("Sign In", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** A rider-facing sentence for each Google Sign-In failure worth distinguishing. */
private fun googleSignInMessage(statusCode: Int): String = when (statusCode) {
    // The OAuth client (SHA-1 / package) is not registered for this build's signing key.
    CommonStatusCodes.DEVELOPER_ERROR -> "Sign-in not configured for this build."
    CommonStatusCodes.NETWORK_ERROR -> "No connection. Check your network and try again."
    GoogleSignInStatusCodes.SIGN_IN_CANCELLED, CommonStatusCodes.CANCELED -> "Google sign-in cancelled."
    GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "Google sign-in is already in progress."
    else -> "Google sign-in failed (code $statusCode)."
}

/**
 * Facebook Login requires an App ID from developers.facebook.com; there is no
 * unregistered mode. If strings.xml carries no id, explain that instead of
 * crashing into the SDK.
 */
private fun startFacebookLogin(
    context: Context,
    facebookEnabled: Boolean,
    callbackManager: CallbackManager,
) {
    if (!facebookEnabled) {
        Toast.makeText(
            context,
            "Facebook login needs a Facebook App ID. Register the app at developers.facebook.com " +
                "and set facebook_app_id in strings.xml.",
            Toast.LENGTH_LONG,
        ).show()
        return
    }
    val activity = context as? Activity ?: return
    LoginManager.getInstance().logInWithReadPermissions(
        activity as ActivityResultRegistryOwner,
        callbackManager,
        listOf("public_profile", "email"),
    )
}
