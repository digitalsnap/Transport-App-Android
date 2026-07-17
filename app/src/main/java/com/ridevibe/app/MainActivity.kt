package com.ridevibe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ridevibe.app.navigation.RideVibeNavGraph
import com.ridevibe.app.ui.theme.RideVibeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RideVibeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RideVibeNavGraph(navController = rememberNavController())
                }
            }
        }
    }
}
