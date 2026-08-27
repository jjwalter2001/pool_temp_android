package com.jjwalter.pooltemp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jjwalter.pooltemp.notification.Scheduler
import com.jjwalter.pooltemp.ui.chemistry.ChemistryHistoryScreen
import com.jjwalter.pooltemp.ui.chemistry.ChemistryScreen
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
                    // Notification permission is runtime-prompted on
                    // Android 13+. Denial is non-fatal -- the worker still
                    // runs, it just can't surface its notification.
                    val notifPerm = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* result ignored */ }
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    val nav = rememberNavController()
                    val config by app.settings.config.collectAsState(initial = null)
                    val hasConfig = config?.isComplete

                    // Idempotent: WorkManager's unique-name policy keeps a
                    // single registration. Re-keys on either flag so the
                    // worker enqueues when onboarding completes AND respects
                    // the user's notification preference.
                    LaunchedEffect(hasConfig, config?.notificationsEnabled) {
                        if (hasConfig == true && config?.notificationsEnabled == true) {
                            Scheduler.schedule(this@MainActivity)
                        } else {
                            Scheduler.cancel(this@MainActivity)
                        }
                    }

                    // Wait until settings have loaded before picking a start
                    // destination -- avoids flashing the wrong screen.
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
                                settings = app.settings,
                                onOpenSettings = { nav.navigate("settings") },
                                onOpenChemistry = { nav.navigate("chemistry") },
                            )
                        }
                        composable("chemistry") {
                            ChemistryScreen(
                                settings = app.settings,
                                onBack = { nav.popBackStack() },
                                onOpenHistory = { nav.navigate("chemistry/history") },
                            )
                        }
                        composable("chemistry/history") {
                            ChemistryHistoryScreen(
                                settings = app.settings,
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settings = app.settings,
                                onBack = { nav.popBackStack() },
                                onEdit = {
                                    nav.navigate("onboarding") {
                                        popUpTo("dashboard")
                                    }
                                },
                                onReset = {
                                    // After settings.clear(), config becomes
                                    // incomplete so MainActivity will route
                                    // to onboarding on the next recomposition.
                                    nav.navigate("onboarding") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
