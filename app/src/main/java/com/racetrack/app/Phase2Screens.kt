package com.racetrack.app

import android.content.Intent
import android.location.Location
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun Phase2HomeScreen(steps: Int, profile: ProfileStore, workoutStore: WorkoutStore, onStart: () -> Unit) {
    val today = workoutStore.forRange(startOfDay(), startOfDay() + DAY_MS)
    val stepDistance = (steps * (profile.heightCm.coerceAtLeast(150f) * 0.415f)) / 100000f
    val calories = steps * profile.weightKg.coerceAtLeast(45f) * 0.00045f
    val goal = profile.dailyGoal.coerceAtLeast(1)
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(if (profile.name.isBlank()) "Good morning" else "Good morning, ${profile.name}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp); Text("Ready to move?", color = Muted, fontSize = 14.sp) }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
                    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) { drawArc(Color(0xFF303030), -90f, 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round)); if (progress > 0) drawArc(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Green, Cyan, Green)), -90f, 360f * progress, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round)) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$steps", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black); Text("/ $goal steps", color = Muted); Text("${(progress * 100).roundToInt()}%", color = Green, fontWeight = FontWeight.Bold) }
                    }
                    Text("Today's movement", color = Muted)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("🔥", "Calories", "${calories.roundToInt()} kcal", Modifier.weight(1f)); MetricCard("📏", "Distance", "%.2f km".format(stepDistance), Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("⏱", "Active Time", "${today.sumOf { it.durationSeconds } / 60L} min", Modifier.weight(1f)); MetricCard("👟", "Workouts", today.size.toString(), Modifier.weight(1f)) } }
        item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.size(8.dp)); Text("Start Live Walk / Run", fontWeight = FontWeight.ExtraBold) } }
    }
}

@Composable
fun Phase2LiveScreen(profile: ProfileStore, tracker: StepTracker, onFinish: (Int, Long, Float, Float, String, List<LapRecord>) -> Unit) {
    val context = LocalContext.current
    val location = remember { LocationTracker(context) }
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
    val sessionFinalized = remember { AtomicBoolean(false) }
    val weight = profile.weightKg.coerceAtLeast(45f)
    val latestSteps by rememberUpdatedState(tracker.steps)
    val latestElapsed by rememberUpdatedState(elapsed)
    val latestDistance by rememberUpdatedState(distance)
    val latestCalories by rememberUpdatedState((if (activity == "Run") 7.0 else 3.5) * 3.5 * weight / 200.0 * (elapsed / 60.0))
    val latestActivity by rememberUpdatedState(activity)
    val latestLaps by rememberUpdatedState(lapRecords)

    LaunchedEffect(Unit) {
        tracker.start()
        location.start(
            onUpdate = { snapshot ->
                distance = snapshot.distanceMeters
                speed = snapshot.currentSpeedMps
                accuracy = snapshot.accuracyMeters
                route = snapshot.route
                val newLaps = lapTracker.update(snapshot.distanceMeters, elapsed)
                if (newLaps.isNotEmpty()) lapRecords = lapTracker.records.toList()
            },
            onStatus = { gpsStatus = it }
        )
        while (true) {
            delay(1000)
            if (running) {
                elapsed++
                location.addElapsedSeconds(elapsed)
                val newLaps = lapTracker.update(distance, elapsed)
                if (newLaps.isNotEmpty()) lapRecords = lapTracker.records.toList()
            }
        }
    }

    // If the user enables Location after entering the screen, retry without resetting the workout.
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            if (running && route.isEmpty()) location.retry()
        }
    }

    LaunchedEffect(lapChoice) {
        if (distance <= 0f) {
            lapRecords = emptyList()
            lapTracker.reset()
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            tracker.stop()
            location.stop()
            if (sessionFinalized.compareAndSet(false, true)) workoutStore.saveSession(latestSteps, latestElapsed, latestDistance, latestCalories.toFloat(), latestActivity, latestLaps)
        }
    }

    BackHandler {
        if (sessionFinalized.compareAndSet(false, true)) onFinish(latestSteps, latestElapsed, latestDistance, latestCalories.toFloat(), latestActivity, latestLaps)
    }

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
                        Text(if (gpsStatus == "Location services are OFF" || gpsStatus == "Turn on Location / GPS") "Location is Off" else "Waiting for GPS", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(gpsStatus, color = Muted, fontSize = 12.sp)
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }) { Text("Open Location Settings") }
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
                    LiveMetric("Steps", tracker.steps.toString())
                    LiveMetric("Distance", "%.2f km".format(distance / 1000f))
                    LiveMetric("Pace", if (paceSecondsPerKm > 0) "%d:%02d".format((paceSecondsPerKm / 60).toInt(), (paceSecondsPerKm % 60).toInt()) else "—")
                    LiveMetric("Calories", "${calories.roundToInt()} kcal")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Lap ${lapRecords.size + 1}", color = Green, fontWeight = FontWeight.Bold)
                    Text("%.0f / %.0f m".format(currentLapDistance, lapChoice), color = Muted)
                    Text(formatWorkoutDuration(currentLapTime), color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (lapRecords.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        lapRecords.takeLast(3).forEach { Text("L${it.number} ${formatWorkoutDuration(it.elapsedSeconds)}", color = Green, fontSize = 12.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { if (running) { tracker.pause(); location.pause(); running = false } else { tracker.resume(); location.resume(); running = true } }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text(if (running) "Pause" else "Resume") }
                    Button(onClick = { if (sessionFinalized.compareAndSet(false, true)) onFinish(tracker.steps, elapsed, distance, calories, activity, lapRecords) }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Icon(Icons.Default.Stop, null); Spacer(Modifier.size(6.dp)); Text("Finish") }
                }
            }
        }
    }
}

@Composable
private fun LiveMetric(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(label, color = Muted, fontSize = 10.sp) }
}

@Composable
private fun MetricCard(icon: String, title: String, value: String, modifier: Modifier) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = modifier) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(icon, fontSize = 19.sp); Text(title, color = Muted, fontSize = 11.sp); Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) } }
}

@Composable
fun ProfileSetupScreen(existing: ProfileStore, onSaved: () -> Unit) {
    var name by remember { mutableStateOf(existing.name) }
    var height by remember { mutableStateOf(if (existing.heightCm > 0) existing.heightCm.toInt().toString() else "") }
    var weight by remember { mutableStateOf(if (existing.weightKg > 0) existing.weightKg.toInt().toString() else "") }
    var age by remember { mutableStateOf(if (existing.age > 0) existing.age.toString() else "") }
    var goal by remember { mutableStateOf(existing.dailyGoal.toString()) }
    val valid = name.isNotBlank() && height.toFloatOrNull() != null && weight.toFloatOrNull() != null && age.toIntOrNull() != null && goal.toIntOrNull() != null
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(if (existing.isComplete) "Edit profile" else "Set up your profile", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Your height and weight are used for estimates.", color = Muted) }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true) }
        item { OutlinedTextField(height, { height = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Height (cm)") }, singleLine = true) }
        item { OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(), label = { Text("Weight (kg)") }, singleLine = true) }
        item { OutlinedTextField(age, { age = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Age") }, singleLine = true) }
        item { OutlinedTextField(goal, { goal = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Daily step goal") }, singleLine = true) }
        item { Button(enabled = valid, onClick = { existing.save(name, height.toFloat(), weight.toFloat(), age.toInt(), goal.toInt()); onSaved() }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal), shape = RoundedCornerShape(18.dp)) { Text("Save & Continue", fontWeight = FontWeight.ExtraBold) } }
    }
}

@Composable
fun Phase2ProfileScreen(profile: ProfileStore, onEdit: () -> Unit, onSettings: () -> Unit) {
    val bmi = profile.bmi()?.let { "%.1f".format(it) } ?: "—"
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Profile", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item { Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(62.dp).background(Green, CircleShape), contentAlignment = Alignment.Center) { Text(profile.name.take(1).uppercase().ifBlank { "R" }, color = Charcoal, fontSize = 25.sp, fontWeight = FontWeight.Black) }; Spacer(Modifier.size(14.dp)); Column(Modifier.weight(1f)) { Text(profile.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Daily goal • ${profile.dailyGoal}", color = Green) }; OutlinedButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) } }; Spacer(Modifier.height(18.dp)); Text("Height • ${profile.heightCm.toInt()} cm     Weight • ${profile.weightKg} kg", color = Muted); Text("Age • ${profile.age}     BMI • $bmi", color = Muted) } } }
        item { Text("Settings", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        item { Button(onClick = onEdit, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Card), shape = RoundedCornerShape(18.dp)) { Text("Edit profile", color = Color.White) } }
        item { Button(onClick = onSettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Card), shape = RoundedCornerShape(18.dp)) { Text("App settings", color = Color.White) } }
    }
}

@Composable
fun Phase2AnalyticsScreen(stepStore: StepDataStore, workoutStore: WorkoutStore) {
    var selected by remember { mutableIntStateOf(1) }
    val labels = listOf("Day", "Week", "Month", "Year")
    val days = when (selected) { 0 -> 1; 1 -> 7; 2 -> 30; else -> 365 }
    val values = stepStore.dailyBuckets(days)
    val total = values.sum()
    val sessions = workoutStore.sessions()
    val distance = sessions.sumOf { it.distanceMeters.toDouble() }.toFloat()
    val points = if (selected == 0) values else values.takeLast(if (selected == 1) 7 else 12)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your Progress", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { labels.forEachIndexed { i, label -> FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(label) }) } } }
        item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(18.dp)) { Text("Steps", color = Muted); Text("%,d".format(total), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(20.dp)); SimpleBarChart(points) } } }
        item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(18.dp)) { Text("Tracked distance", color = Muted); Text("%.2f km".format(distance / 1000f), color = Cyan, fontSize = 28.sp, fontWeight = FontWeight.Black); Text("GPS workout distance", color = Muted, fontSize = 12.sp) } } }
        item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Workout history", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); if (sessions.isEmpty()) Text("No Walk / Run sessions recorded yet.", color = Muted) else sessions.takeLast(10).reversed().forEach { s -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("${s.activity} • ${workoutStore.formatDate(s.date)}", color = Color.White, fontWeight = FontWeight.Bold); Text("${s.steps} steps • %.2f km • ${s.calories.roundToInt()} kcal".format(s.distanceMeters / 1000f), color = Muted, fontSize = 12.sp); if (s.laps.isNotEmpty()) Text(s.laps.joinToString("   ") { "L${it.number} ${formatWorkoutDuration(it.elapsedSeconds)}" }, color = Green, fontSize = 12.sp) }; Text(formatWorkoutDuration(s.durationSeconds), color = Green, fontWeight = FontWeight.Bold) } } } } }
    }
}

@Composable
private fun SimpleBarChart(values: List<Int>) {
    val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) { values.forEach { value -> Box(Modifier.width(18.dp).height((120f * value / maxValue).coerceAtLeast(4f).dp).background(Green, RoundedCornerShape(8.dp))) } }
}

private fun formatWorkoutDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
private const val DAY_MS = 86_400_000L
private fun startOfDay(): Long { val c = java.util.Calendar.getInstance(); c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0); return c.timeInMillis }
