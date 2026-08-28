package com.racetrack.app

import android.location.Location
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun Phase2HomeScreen(
    steps: Int,
    profile: ProfileStore,
    workoutStore: WorkoutStore,
    onStart: () -> Unit
) {
    val today = workoutStore.forRange(startOfDay(), startOfDay() + DAY_MS)
    val distance = today.sumOf { it.distanceMeters.toDouble() }.toFloat()
    val calories = today.sumOf { it.calories.toDouble() }.toFloat()
    val active = today.sumOf { it.durationSeconds } / 60L
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
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("🔥", "Calories", "${calories.roundToInt()} kcal", Modifier.weight(1f)); MetricCard("📏", "Distance", "${"%.2f".format(distance / 1000f)} km", Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("⏱", "Active Time", "$active min", Modifier.weight(1f)); MetricCard("👟", "Workouts", today.size.toString(), Modifier.weight(1f)) } }
        item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.size(8.dp)); Text("Start Live Walk / Run", fontWeight = FontWeight.ExtraBold) } }
    }
}

@Composable
fun Phase2LiveScreen(profile: ProfileStore, tracker: StepTracker, onFinish: (Int, Long, Float, Float) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val location = remember { LocationTracker(context) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    var distance by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    var route by remember { mutableStateOf<List<Location>>(emptyList()) }
    var activity by remember { mutableStateOf("Walk") }
    val weight = profile.weightKg.coerceAtLeast(1f)

    LaunchedEffect(Unit) {
        tracker.start()
        location.start { snapshot ->
            distance = snapshot.distanceMeters
            speed = snapshot.currentSpeedMps
            route = snapshot.route
        }
        while (true) { delay(1000); if (running) { elapsed++; location.addElapsedSeconds(elapsed) } }
    }
    DisposableEffect(Unit) { onDispose { tracker.stop(); location.stop() } }

    val met = if (activity == "Run") 7.0 else 3.5
    val calories = (met * 3.5 * weight / 200.0 * (elapsed / 60.0)).toFloat()
    val pace = if (speed > 0.3f) 1000f / speed else 0f

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxWidth().weight(1f)) { RouteMap(route) }
        Card(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Live ${activity.lowercase()}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black); Row { FilterChip(selected = activity == "Walk", onClick = { activity = "Walk" }, label = { Text("Walk") }); Spacer(Modifier.size(6.dp)); FilterChip(selected = activity == "Run", onClick = { activity = "Run" }, label = { Text("Run") }) } }
                Text("%02d:%02d:%02d".format(elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { LiveMetric("Steps", tracker.steps.toString()); LiveMetric("Distance", "%.2f km".format(distance / 1000f)); LiveMetric("Pace", if (pace > 0) "%d:%02d /km".format((pace / 60).toInt(), (pace % 60).toInt()) else "—") ; LiveMetric("Calories", "${calories.roundToInt()} kcal") }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { if (running) { tracker.pause(); running = false } else { tracker.resume(); running = true } }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text(if (running) "Pause" else "Resume") }
                    Button(onClick = { onFinish(tracker.steps, elapsed, distance, calories) }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Icon(Icons.Default.Stop, null); Spacer(Modifier.size(6.dp)); Text("Finish") }
                }
            }
        }
    }
}

@Composable
private fun RouteMap(route: List<Location>) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadDataWithBaseURL("https://www.openstreetmap.org/", mapHtml(), "text/html", "UTF-8", null)
        }
    }, update = { web ->
        if (route.isNotEmpty()) {
            val points = route.joinToString(",") { "[${it.latitude},${it.longitude}]" }
            web.evaluateJavascript("window.updateRoute([$points]);", null)
        }
    }, modifier = Modifier.fillMaxSize())
}

private fun mapHtml() = """
<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'>
<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>
<style>html,body,#map{height:100%;margin:0;background:#121212}</style></head><body><div id='map'></div>
<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>
const map=L.map('map').setView([20.5937,78.9629],5); L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
let line=L.polyline([],{color:'#00E676',weight:6}).addTo(map); let marker=null;
window.updateRoute=function(points){if(!points.length)return;line.setLatLngs(points); if(marker)map.removeLayer(marker); marker=L.circleMarker(points[points.length-1],{radius:7,color:'#00B0FF',fillColor:'#00E676',fillOpacity:1}).addTo(map); map.fitBounds(line.getBounds(),{padding:[30,30],maxZoom:17});};
</script></body></html>
"""

@Composable
private fun LiveMetric(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(label, color = Muted, fontSize = 10.sp) } }

@Composable
fun ProfileSetupScreen(existing: ProfileStore, onSaved: () -> Unit) {
    var name by remember { mutableStateOf(existing.name) }
    var height by remember { mutableStateOf(if (existing.heightCm > 0) existing.heightCm.toInt().toString() else "") }
    var weight by remember { mutableStateOf(if (existing.weightKg > 0) existing.weightKg.toInt().toString() else "") }
    var age by remember { mutableStateOf(if (existing.age > 0) existing.age.toString() else "") }
    var goal by remember { mutableStateOf(existing.dailyGoal.toString()) }
    val valid = name.isNotBlank() && height.toFloatOrNull() != null && weight.toFloatOrNull() != null && age.toIntOrNull() != null && goal.toIntOrNull() != null
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(if (existing.isComplete) "Edit profile" else "Set up your profile", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black); Text("These values power your goal, BMI and calorie estimates.", color = Muted) }
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
    val distance = workoutStore.sessions().sumOf { it.distanceMeters.toDouble() }.toFloat()
    val points = if (selected == 0) values else values.takeLast(if (selected == 1) 7 else 12)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your Progress", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) { labels.forEachIndexed { i, label -> FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(label) }) } } }
        item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(18.dp)) { Text("Steps", color = Muted); Text("%,d".format(total), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(20.dp)); BarChart(points) } } }
        item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(18.dp)) { Text("Tracked distance", color = Muted); Text("%.2f km".format(distance / 1000f), color = Cyan, fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Only GPS workout distance is included.", color = Muted, fontSize = 12.sp) } } }
    }
}

@Composable
private fun BarChart(values: List<Int>) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) { values.forEach { value -> Box(Modifier.width(18.dp).height((120f * value / max).coerceAtLeast(4f).dp).background(Green, RoundedCornerShape(8.dp))) } }
}

private const val DAY_MS = 86_400_000L
private fun startOfDay(): Long { val c = java.util.Calendar.getInstance(); c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0); return c.timeInMillis }
