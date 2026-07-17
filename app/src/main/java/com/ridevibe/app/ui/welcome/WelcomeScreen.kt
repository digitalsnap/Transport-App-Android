package com.ridevibe.app.ui.welcome

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.ridevibe.app.R

private const val FACEBOOK_UNCONFIGURED_ID = "0"
private val FacebookBlue = Color(0xFF1877F2)

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
) {
    val context = LocalContext.current

    // Google Sign-In: basic profile/email needs no cloud console setup, so this
    // works on any Play-services device (incl. the Play Store emulator image).
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        }.onSuccess { account ->
            Toast.makeText(context, "Welcome, ${account.displayName ?: account.email}!", Toast.LENGTH_SHORT).show()
            onGetStarted()
        }.onFailure {
            Toast.makeText(context, "Google sign-in was cancelled or failed.", Toast.LENGTH_SHORT).show()
        }
    }

    val facebookCallbackManager = remember { CallbackManager.Factory.create() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
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

        // Hero panel — sample photography (Lorem Picsum) standing in for final brand shots.
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
                            1f to Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
            )
            Text(
                "Verified Safety",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().height(52.dp),
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
                    .build()
                googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
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

        Button(
            onClick = { startFacebookLogin(context, facebookCallbackManager, onGetStarted) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue, contentColor = Color.White),
        ) {
            Text("f", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.size(10.dp))
            Text("Continue with Facebook", fontWeight = FontWeight.SemiBold)
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                "Already have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // TODO: route to a real email/password sign-in flow once accounts exist in the CRS backend.
            TextButton(onClick = onGetStarted) {
                Text("Sign In", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Facebook Login requires an App ID from developers.facebook.com — there is no
 * unregistered mode. Until strings.xml carries a real id, explain that instead
 * of crashing into the SDK.
 */
private fun startFacebookLogin(
    context: android.content.Context,
    callbackManager: CallbackManager,
    onSignedIn: () -> Unit,
) {
    val appId = context.getString(R.string.facebook_app_id)
    if (appId == FACEBOOK_UNCONFIGURED_ID) {
        Toast.makeText(
            context,
            "Facebook login needs a Facebook App ID. Register the app at developers.facebook.com " +
                "and set facebook_app_id in strings.xml.",
            Toast.LENGTH_LONG,
        ).show()
        return
    }

    val activity = context as? Activity ?: return
    if (!FacebookSdk.isInitialized()) FacebookSdk.fullyInitialize()

    val loginManager = LoginManager.getInstance()
    loginManager.registerCallback(
        callbackManager,
        object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                Toast.makeText(context, "Signed in with Facebook!", Toast.LENGTH_SHORT).show()
                onSignedIn()
            }

            override fun onCancel() {
                Toast.makeText(context, "Facebook sign-in cancelled.", Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: FacebookException) {
                Toast.makeText(context, "Facebook sign-in failed: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        },
    )
    loginManager.logInWithReadPermissions(
        activity as ActivityResultRegistryOwner,
        callbackManager,
        listOf("public_profile", "email"),
    )
}
