package com.racetrack.app

import android.content.Intent
import android.location.Location
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun Phase2LiveServiceScreen(
    profile: ProfileStore,
    tracker: StepTracker,
    onFinish: (Int, Long, Float, Float, String, List<LapRecord>) -> Unit
) {
    val context = LocalContext.current
    val workoutStore = remember { WorkoutStore(context) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    var distance by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    var route by remember { mutableStateOf<List<Location>>(emptyList()) }
    var gpsStatus by remember { mutableStateOf("Starting GPS…") }
    var activity by remember { mutableStateOf("Walk") }
    var lapChoice by remember { mutableFloatStateOf(1000f) }
    val lapTracker = remember(lapChoice) { LapTracker(lapChoice) }
    var lapRecords by remember { mutableStateOf<List<LapRecord>>(emptyList()) }
    val finalized = remember { AtomicBoolean(false) }
    val weight = profile.weightKg.coerceAtLeast(45f)

    LaunchedEffect(Unit) {
        tracker.start()
        WorkoutLocationServiceBridge.start(context)
        while (true) {
            delay(500)
            val snapshot = StepCountingService.workoutSnapshot
            distance = snapshot.distanceMeters
            speed = snapshot.currentSpeedMps
            accuracy = snapshot.accuracyMeters
            route = snapshot.route
            gpsStatus = StepCountingService.workoutStatus
            elapsed = RouteReplaySession.durationSeconds
            val newLaps = lapTracker.update(distance, elapsed)
            if (newLaps.isNotEmpty()) lapRecords = lapTracker.records.toList()
        }
    }

    LaunchedEffect(lapChoice) {
        if (distance <= 0f) {
            lapRecords = emptyList()
            lapTracker.reset()
        }
    }

    fun finishOnce() {
        if (!finalized.compareAndSet(false, true)) return
        WorkoutLocationServiceBridge.stop(context)
        val finalDistance = RouteReplaySession.distanceMeters
        val finalElapsed = RouteReplaySession.durationSeconds
        val calories = ((if (activity == "Run") 7.0 else 3.5) * 3.5 * weight / 200.0 * (finalElapsed / 60.0)).toFloat()
        onFinish(tracker.steps, finalElapsed, finalDistance, calories, activity, lapRecords)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (finalized.compareAndSet(false, true)) {
                WorkoutLocationServiceBridge.stop(context)
                val finalElapsed = RouteReplaySession.durationSeconds
                val finalDistance = RouteReplaySession.distanceMeters
                val calories = ((if (activity == "Run") 7.0 else 3.5) * 3.5 * weight / 200.0 * (finalElapsed / 60.0)).toFloat()
                workoutStore.saveSession(tracker.steps, finalElapsed, finalDistance, calories, activity, lapRecords)
            }
            tracker.stop()
        }
    }

    BackHandler { finishOnce() }

    val calories = ((if (activity == "Run") 7.0 else 3.5) * 3.5 * weight / 200.0 * (elapsed / 60.0)).toFloat()
    val paceSecondsPerKm = if (speed > 0.3f) 1000f / speed else 0f
    val currentLapDistance = lapTracker.currentLapDistance(distance)
    val currentLapTime = lapTracker.currentLapElapsed(elapsed)

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NativeRouteMap(route, Modifier.fillMaxSize())
            if (route.isEmpty()) {
                Card(Modifier.align(Alignment.TopCenter).padding(18.dp), colors = CardDefaults.cardColors(containerColor = Card.copy(alpha = 0.96f)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (gpsStatus.contains("OFF") || gpsStatus.contains("Turn on")) "Location is Off" else "Waiting for GPS", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(gpsStatus, color = Muted, fontSize = 12.sp)
                        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Open Location Settings") }
                    }
                }
            } else {
                Card(Modifier.align(Alignment.TopStart).padding(14.dp), colors = CardDefaults.cardColors(containerColor = Card.copy(alpha = 0.94f)), shape = RoundedCornerShape(14.dp)) {
                    Text("GPS ± %.0f m".format(accuracy), Modifier.padding(10.dp), color = if (accuracy <= 15f) Green else if (accuracy <= 30f) Color.Yellow else Coral, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Card(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Live ${activity.lowercase()}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Row { FilterChip(selected = activity == "Walk", onClick = { activity = "Walk" }, label = { Text("Walk") }); Spacer(Modifier.size(5.dp)); FilterChip(selected = activity == "Run", onClick = { activity = "Run" }, label = { Text("Run") }) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(400f to "400 m", 800f to "800 m", 1000f to "1 km").forEach { (meters, label) -> FilterChip(selected = lapChoice == meters, onClick = { if (distance < 5f) lapChoice = meters }, label = { Text(label) }) }
                }
                Text("%02d:%02d:%02d".format(elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ServiceLiveMetric("Steps", tracker.steps.toString())
                    ServiceLiveMetric("Distance", "%.2f km".format(distance / 1000f))
                    ServiceLiveMetric("Pace", if (paceSecondsPerKm > 0) "%d:%02d".format((paceSecondsPerKm / 60).toInt(), (paceSecondsPerKm % 60).toInt()) else "—")
                    ServiceLiveMetric("Calories", "${calories.roundToInt()} kcal")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Lap ${lapRecords.size + 1}", color = Green, fontWeight = FontWeight.Bold)
                    Text("%.0f / %.0f m".format(currentLapDistance, lapChoice), color = Muted)
                    Text(serviceFormatWorkoutDuration(currentLapTime), color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (lapRecords.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    lapRecords.takeLast(3).forEach { Text("L${it.number} ${serviceFormatWorkoutDuration(it.elapsedSeconds)}", color = Green, fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        if (running) {
                            WorkoutLocationServiceBridge.pause(context)
                            tracker.pause()
                            running = false
                        } else {
                            WorkoutLocationServiceBridge.resume(context)
                            tracker.resume()
                            running = true
                        }
                    }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) {
                        Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (running) "Pause" else "Resume")
                    }
                    Button(onClick = { finishOnce() }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
                        Icon(Icons.Default.Stop, null); Spacer(Modifier.size(6.dp)); Text("Finish")
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceLiveMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

private fun serviceFormatWorkoutDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
