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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val tracker = remember { StepTracker(context) }
    val locationTracker = remember { LocationTracker(context) }
    val stepStore = remember { StepDataStore(context) }
    val workoutStore = remember { WorkoutStore(context) }
    val profile = remember { ProfileDataStore(context) }
    var screen by rememberSaveable { mutableStateOf("home") }
    var onboarding by rememberSaveable { mutableStateOf(!profile.onboardingComplete) }
    var permissionsChecked by rememberSaveable { mutableStateOf(false) }
    var todaySteps by remember { mutableIntStateOf(stepStore.todaySteps()) }

    DisposableEffect(Unit) {
        onDispose {
            tracker.release()
            locationTracker.stop()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsChecked = true
        startStepServiceIfAllowed(context)
    }

    LaunchedEffect(onboarding) {
        if (!onboarding) {
            val missing = corePermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
            else { permissionsChecked = true; startStepServiceIfAllowed(context) }
        }
    }

    LaunchedEffect(permissionsChecked) {
        if (!permissionsChecked) return@LaunchedEffect
        while (true) {
            todaySteps = stepStore.todaySteps()
            delay(1000)
        }
    }

    BackHandler {
        when {
            onboarding && !profile.onboardingComplete -> Unit
            onboarding -> onboarding = false
            screen == "live" -> { tracker.stop(); locationTracker.stop(); screen = "home" }
            screen != "home" -> screen = "home"
            else -> finishActivity()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Green, secondary = Cyan, background = Charcoal, surface = Card, error = Coral)) {
        if (onboarding) {
            Phase2OnboardingScreen(profile) { onboarding = false }
            return@MaterialTheme
        }
        if (!permissionsChecked) {
            PermissionGate(onContinue = {
                val missing = corePermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else { permissionsChecked = true; startStepServiceIfAllowed(context) }
            })
            return@MaterialTheme
        }

        Scaffold(
            containerColor = Charcoal,
            bottomBar = {
                if (screen != "live") NavigationBar(containerColor = Card) {
                    NavItem("home", Icons.Default.Home, "Home", screen) { screen = it }
                    NavItem("analytics", Icons.Default.BarChart, "Stats", screen) { screen = it }
                    NavItem("community", Icons.Default.EmojiEvents, "Community", screen) { screen = it }
                    NavItem("profile", Icons.Default.Person, "Profile", screen) { screen = it }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    "home" -> Phase2HomeScreen(todaySteps, profile) { screen = "live" }
                    "live" -> Phase2LiveScreen(tracker, locationTracker, profile) { steps, duration, distance, calories ->
                        workoutStore.saveSession(steps, duration, distance, calories)
                        tracker.stop(); locationTracker.stop(); screen = "home"
                    }
                    "analytics" -> Phase2AnalyticsScreen(workoutStore)
                    "community" -> CommunityScreen()
                    "profile" -> Phase2ProfileScreen(profile, onBack = { screen = "home" }, onEdit = { onboarding = true })
                }
            }
        }
    }
}

private fun startStepServiceIfAllowed(context: android.content.Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
    ContextCompat.startForegroundService(context, Intent(context, StepCountingService::class.java))
}

private fun finishActivity() {
    // RootActivity owns this callback through the system BackHandler; no navigation target exists at Home.
}

@Composable
private fun PermissionGate(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Permissions needed", style = MaterialTheme.typography.headlineMedium)
        Text("Race Track needs activity recognition for steps, location for route tracking, and notifications for reminders and live activity updates.", modifier = Modifier.padding(top = 12.dp, bottom = 24.dp), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onContinue) { Text("Allow permissions") }
    }
}
