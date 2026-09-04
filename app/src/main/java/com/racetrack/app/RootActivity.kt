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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val stepStore = remember { StepDataStore(context) }
    val workoutStore = remember { WorkoutStore(context) }
    val profile = remember { ProfileStore(context) }
    val auth = remember { FirebaseAuthManager(context) }
    var screen by rememberSaveable { mutableStateOf("home") }
    var setupProfile by rememberSaveable { mutableStateOf(!profile.isComplete) }
    var authChecked by rememberSaveable { mutableStateOf(auth.currentUser != null) }
    var permissionsChecked by rememberSaveable { mutableStateOf(false) }
    var todaySteps by remember { mutableIntStateOf(stepStore.todaySteps()) }
    var selectedWorkout by remember { mutableStateOf<WorkoutStore.Session?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsChecked = true
        startStepServiceIfAllowed(context)
        setupProfile = !profile.isComplete
    }

    LaunchedEffect(authChecked) {
        if (!authChecked) return@LaunchedEffect
        val missing = corePermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else {
            permissionsChecked = true
            startStepServiceIfAllowed(context)
            setupProfile = !profile.isComplete
        }
    }

    LaunchedEffect(permissionsChecked) {
        if (!permissionsChecked) return@LaunchedEffect
        while (true) { todaySteps = stepStore.todaySteps(); delay(1000) }
    }

    BackHandler {
        when {
            !authChecked -> Unit
            setupProfile && profile.isComplete -> { setupProfile = false; screen = "profile" }
            setupProfile -> Unit
            screen == "settings" || screen == "account" -> screen = "profile"
            screen == "live" -> Unit
            screen == "replay" || screen == "details" -> screen = "history"
            screen == "history" -> screen = "analytics"
            screen != "home" -> screen = "home"
            else -> Unit
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Green, secondary = Cyan, background = Charcoal, surface = Card, error = Coral)) {
        if (!authChecked) {
            GoogleAuthScreen(
                profile = profile,
                workoutStore = workoutStore,
                onBack = { },
                onFinished = {
                    setupProfile = !profile.isComplete
                    authChecked = true
                }
            )
            return@MaterialTheme
        }
        if (!permissionsChecked) {
            PermissionGate(onContinue = {
                val missing = corePermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else permissionsChecked = true
            })
            return@MaterialTheme
        }
        if (setupProfile) {
            RaceTrackProfileEditScreen(
                profile,
                onSaved = { setupProfile = false; screen = "home" },
                onBack = { if (profile.isComplete) { setupProfile = false; screen = "profile" } }
            )
            return@MaterialTheme
        }
        Scaffold(containerColor = Charcoal, bottomBar = {
            if (screen != "live" && screen != "settings" && screen != "account" && screen != "replay" && screen != "history" && screen != "details") NavigationBar(containerColor = Card) {
                NavItem("home", Icons.Default.Home, "Home", screen) { screen = it }
                NavItem("analytics", Icons.Default.BarChart, "Stats", screen) { screen = it }
                NavItem("community", Icons.Default.EmojiEvents, "Community", screen) { screen = it }
                NavItem("profile", Icons.Default.Person, "Profile", screen) { screen = it }
            }
        }) { padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    "home" -> {
                        Box(Modifier.fillMaxSize()) {
                            Phase2HomeScreen(todaySteps, profile, workoutStore) { screen = "live" }
                            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { screen = "history" }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Workout History") }
                                if (RouteReplaySession.route.isNotEmpty()) Button(onClick = { screen = "replay" }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("▶  Replay last route") }
                            }
                        }
                    }
                    "live" -> Phase2LiveServiceScreen(profile, tracker) { steps, duration, distance, calories, activity, laps ->
                        workoutStore.saveSession(steps, duration, distance, calories, activity, laps)
                        RouteReplaySession.setActivity(activity)
                        screen = "home"
                    }
                    "replay" -> AnimatedRoutePostScreen(route = RouteReplaySession.route, distanceMeters = RouteReplaySession.distanceMeters, durationSeconds = RouteReplaySession.durationSeconds, activity = RouteReplaySession.activity, onDone = { screen = "home" })
                    "history" -> WorkoutHistoryScreen(workoutStore) { session ->
                        selectedWorkout = session
                        screen = "details"
                    }
                    "details" -> selectedWorkout?.let { session ->
                        WorkoutDetailsScreen(
                            session = session,
                            onReplay = {
                                RouteReplaySession.update(session.route, session.distanceMeters, session.durationSeconds)
                                RouteReplaySession.setActivity(session.activity)
                                screen = "replay"
                            },
                            onBack = { screen = "history" }
                        )
                    } ?: run { screen = "history" }
                    "analytics" -> Phase2AnalyticsScreen(stepStore, workoutStore)
                    "community" -> CommunityScreen()
                    "profile" -> Phase2ProfileScreen(profile, onEdit = { setupProfile = true }, onSettings = { screen = "settings" })
                    "settings" -> Phase2SettingsScreen(context, onEditProfile = { setupProfile = true }, onAccountSync = { screen = "account" }, onBack = { screen = "profile" })
                    "account" -> GoogleAuthScreen(profile, workoutStore, onBack = { screen = "settings" }, onFinished = { screen = "home" })
                }
            }
        }
    }
}

private fun startStepServiceIfAllowed(context: android.content.Context) {
    val activityGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!activityGranted || !locationGranted) return
    ContextCompat.startForegroundService(context, Intent(context, StepCountingService::class.java))
}

@Composable
private fun PermissionGate(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Race Track", style = MaterialTheme.typography.headlineLarge)
        Text("Allow activity, location and notification permissions before tracking.", modifier = Modifier.padding(top = 12.dp, bottom = 24.dp), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onContinue) { Text("Allow permissions") }
    }
}
