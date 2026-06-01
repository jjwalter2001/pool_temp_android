package com.jjwalter.pooltemp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jjwalter.pooltemp.ui.dashboard.DashboardScreen
import com.jjwalter.pooltemp.ui.onboarding.OnboardingScreen
import com.jjwalter.pooltemp.ui.settings.SettingsScreen
import com.jjwalter.pooltemp.ui.theme.PoolTempTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PoolTempApp
        setContent {
            PoolTempTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    val hasConfig by app.settings.hasConfig.collectAsState(initial = null)
                    // Decide start destination once settings have loaded. null
                    // means "still loading" -- show a blank Surface for one
                    // frame rather than flash the wrong screen.
                    val start = when (hasConfig) {
                        null -> null
                        true -> "dashboard"
                        false -> "onboarding"
                    } ?: return@Surface
                    NavHost(nav, startDestination = start) {
                        composable("onboarding") {
                            OnboardingScreen(
                                settings = app.settings,
                                onDone = {
                                    nav.navigate("dashboard") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(
                                onOpenSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settings = app.settings,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
