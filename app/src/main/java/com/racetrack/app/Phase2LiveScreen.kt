package com.racetrack.app

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun Phase2LiveScreen(
    tracker: StepTracker,
    locationTracker: LocationTracker,
    profile: ProfileDataStore,
    onFinish: (steps: Int, durationSeconds: Long, distanceMeters: Double, calories: Double) -> Unit
) {
    var running by remember { mutableStateOf(true) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var accumulated by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        tracker.start()
        locationTracker.start()
        while (true) {
            if (running) elapsed = accumulated + (SystemClock.elapsedRealtime() - startedAt) / 1000L
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tracker.stop()
            locationTracker.stop()
        }
    }

    val distanceKm = locationTracker.distanceMeters / 1000.0
    val paceSeconds = if (distanceKm > 0.01) elapsed / distanceKm else 0.0
    val pace = if (paceSeconds > 0) formatPace(paceSeconds) else "--:--"
    val speedKmh = locationTracker.speedMps * 3.6
    val calories = estimatedCalories(profile.weightKg, elapsed, speedKmh)

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF172022))) {
            Canvas(Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF263337), radius = 190f, center = center, style = Stroke(2f))
                drawCircle(Color(0xFF263337), radius = 110f, center = center, style = Stroke(2f))
                drawLine(Color(0xFF263337), androidx.compose.ui.geometry.Offset(center.x, 0f), androidx.compose.ui.geometry.Offset(center.x, size.height), 2f)
                drawLine(Color(0xFF263337), androidx.compose.ui.geometry.Offset(0f, center.y), androidx.compose.ui.geometry.Offset(size.width, center.y), 2f)
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOn, null, tint = Green)
                Text(if (locationTracker.gpsAvailable) "GPS ACTIVE" else "WAITING FOR GPS", color = if (locationTracker.gpsAvailable) Green else Coral, fontSize = 12.sp)
            }
            Text("Live route trace • ${"%.2f".format(distanceKm)} km", color = Muted, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        }

        Surface(color = Card, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text(formatDurationPhase2(elapsed), color = Color.White, fontSize = 42.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat2("Steps", tracker.steps.toString())
                    Stat2("Distance", "%.2f km".format(distanceKm))
                    Stat2("Pace", pace)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat2("Speed", "%.1f km/h".format(speedKmh))
                    Stat2("Calories", "%.0f kcal".format(calories))
                    Stat2("GPS", if (locationTracker.gpsAvailable) "Ready" else "Searching")
                }
                if (profile.weightKg <= 0f) Text("Set your weight in Profile to estimate calories.", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        if (running) { accumulated = elapsed; locationTracker.stop(); tracker.pause(); running = false }
                        else { startedAt = SystemClock.elapsedRealtime(); locationTracker.start(); tracker.resume(); running = true }
                    }, modifier = Modifier.weight(1f).height(54.dp), shape = CircleShape) {
                        Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp)); Text(if (running) "Pause" else "Resume")
                    }
                    Button(onClick = { onFinish(tracker.steps, elapsed, locationTracker.distanceMeters, calories) }, modifier = Modifier.weight(1f).height(54.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
                        Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Finish")
                    }
                }
            }
        }
    }
}

private fun estimatedCalories(weightKg: Float, durationSeconds: Long, speedKmh: Double): Double {
    if (weightKg <= 0f || durationSeconds <= 0L) return 0.0
    val met = when {
        speedKmh >= 9.0 -> 8.3
        speedKmh >= 6.5 -> 5.5
        else -> 3.5
    }
    return met * 3.5 * weightKg / 200.0 * durationSeconds / 60.0
}

private fun formatPace(secondsPerKm: Double): String {
    val total = max(1, secondsPerKm.toInt())
    return "%02d:%02d".format(total / 60, total % 60)
}

private fun formatDurationPhase2(seconds: Long): String = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)

@Composable private fun Stat2(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontSize = 16.sp); Text(label, color = Muted, fontSize = 11.sp) } }
