package com.racetrack.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class RootActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ProductionRoot() }
    }
}

private val corePermissions = buildList {
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
private fun ProductionRoot() {
    val context = LocalContext.current
    val tracker = remember { StepTracker(context) }
    val store = remember { StepDataStore(context) }
    var screen by rememberSaveable { mutableStateOf("home") }
    var onboarding by rememberSaveable { mutableStateOf(false) }
    var permissionsChecked by rememberSaveable { mutableStateOf(false) }
    var todaySteps by remember { mutableIntStateOf(store.todaySteps()) }

    DisposableEffect(tracker) {
        onDispose { tracker.release() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsChecked = true
        startStepServiceIfAllowed(context)
    }

    LaunchedEffect(Unit) {
        val missing = corePermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            permissionsChecked = true
            startStepServiceIfAllowed(context)
        }
    }

    LaunchedEffect(permissionsChecked) {
        if (!permissionsChecked) return@LaunchedEffect
        while (true) {
            todaySteps = store.todaySteps()
            delay(1000)
        }
    }

    BackHandler {
        when {
            onboarding -> onboarding = false
            screen == "settings" -> screen = "profile"
            screen == "live" -> {
                tracker.stop()
                screen = "home"
            }
            screen != "home" -> screen = "home"
            else -> Unit
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green,
            secondary = Cyan,
            background = Charcoal,
            surface = Card,
            error = Coral
        )
    ) {
        if (!permissionsChecked) {
            PermissionGate(
                onContinue = {
                    val missing = corePermissions.filter { permission ->
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
                    else {
                        permissionsChecked = true
                        startStepServiceIfAllowed(context)
                    }
                }
            )
            return@MaterialTheme
        }

        if (onboarding) {
            OnboardingScreen(onFinish = { onboarding = false })
            return@MaterialTheme
        }

        Scaffold(
            containerColor = Charcoal,
            bottomBar = {
                if (screen != "live" && screen != "settings") {
                    NavigationBar(containerColor = Card) {
                        NavItem("home", Icons.Default.Home, "Home", screen) { screen = it }
                        NavItem("analytics", Icons.Default.BarChart, "Stats", screen) { screen = it }
                        NavItem("community", Icons.Default.EmojiEvents, "Community", screen) { screen = it }
                        NavItem("profile", Icons.Default.Person, "Profile", screen) { screen = it }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    "home" -> HomeScreen(
                        steps = todaySteps,
                        onStart = { screen = "live" }
                    )
                    "live" -> LiveTrackingScreen(
                        tracker = tracker,
                        onFinish = {
                            tracker.stop()
                            screen = "home"
                        }
                    )
                    "analytics" -> AnalyticsScreen()
                    "community" -> CommunityScreen()
                    "profile" -> ProfileScreen(onSettings = { screen = "settings" })
                    "settings" -> SettingsScreen(
                        onBack = { screen = "profile" },
                        onOnboarding = { onboarding = true }
                    )
                }
            }
        }
    }
}

private fun startStepServiceIfAllowed(context: android.content.Context) {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) != PackageManager.PERMISSION_GRANTED
    ) return

    val intent = Intent(context, StepCountingService::class.java)
    ContextCompat.startForegroundService(context, intent)
}

@Composable
private fun PermissionGate(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = Green)
        Text("Permissions needed", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Race Track needs activity recognition for steps, location for route tracking, and notifications for reminders and live activity updates.",
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onContinue) {
            Text("Allow permissions")
        }
    }
}
