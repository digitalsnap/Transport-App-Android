package com.ridevibe.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ridevibe.app.navigation.RideVibeNavGraph
import com.ridevibe.app.ui.theme.RideVibeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** The live controller, so a deep link that arrives via onNewIntent reaches the graph. */
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede super.onCreate: it swaps the splash theme for postSplashScreenTheme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Status bar transparent over the charcoal top bars; the navigation bar
        // keeps a charcoal scrim so 3-button navigation stays legible on the
        // light canvas of screens that have no bottom nav (results, checkout).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(ContextCompat.getColor(this, R.color.brand_charcoal)),
        )
        setContent {
            RideVibeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val controller = rememberNavController()
                    SideEffect { navController = controller }
                    RideVibeNavGraph(navController = controller)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop delivers ridevibe:// links here while the app is open.
        navController?.handleDeepLink(intent)
    }
}
