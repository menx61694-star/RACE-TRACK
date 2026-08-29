package com.racetrack.app

import android.location.Location
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean

private const val V2_DAY_MS = 86_400_000L
private const val V2_STRIDE_FACTOR = 0.414f

private fun v2StartOfDay(): Long {
    val c = java.util.Calendar.getInstance()
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun v2StrideMeters(profile: ProfileStore): Float =
    profile.heightCm.coerceIn(100f, 230f) / 100f * V2_STRIDE_FACTOR

private fun v2Distance(steps: Int, profile: ProfileStore): Float =
    steps.coerceAtLeast(0) * v2StrideMeters(profile)

private fun v2StepCalories(steps: Int, profile: ProfileStore): Float =
    steps.coerceAtLeast(0) * 0.04f * (profile.weightKg.coerceIn(30f, 250f) / 70f)

@Composable
fun WorkoutExperienceHome(steps: Int, profile: ProfileStore, workoutStore: WorkoutStore, onStart: () -> Unit) {
    val today = workoutStore.forRange(v2StartOfDay(), v2StartOfDay() + V2_DAY_MS)
    val goal = profile.dailyGoal.coerceAtLeast(1)
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    val distance = v2Distance(steps, profile)
    val calories = v2StepCalories(steps, profile)
    val activeMinutes = today.sumOf { it.durationSeconds } / 60L

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(if (profile.name.isBlank()) "Good morning" else "Good morning, ${profile.name}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 27.sp)
            Text("Ready to move?", color = Muted, fontSize = 14.sp)
        }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
                    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawArc(Color(0xFF303030), -90f, 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                            if (progress > 0f) drawArc(Brush.sweepGradient(listOf(Green, Cyan, Green)), -90f, 360f * progress, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(steps.toString(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                            Text("/ $goal steps", color = Muted)
                            Text("${(progress * 100).roundToInt()}%", color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Today's movement", color = Muted)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { V2Metric("🔥", "Calories", "${calories.roundToInt()} kcal", Modifier.weight(1f)); V2Metric("📏", "Distance", "%.2f km".format(distance / 1000f), Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { V2Metric("⏱", "Active Time", "$activeMinutes min", Modifier.weight(1f)); V2Metric("👟", "Workouts", today.size.toString(), Modifier.weight(1f)) } }
        item {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) {
                Icon(Icons.Default.LocationOn, null); Spacer(Modifier.size(8.dp)); Text("Start Live Walk / Run", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun V2Metric(icon: String, title: String, value: String, modifier: Modifier) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 19.sp); Spacer(Modifier.size(7.dp)); Text(title, color = Muted, fontSize = 12.sp) }
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WorkoutExperienceLive(profile: ProfileStore, tracker: StepTracker, onFinish: (Int, Long, Float, Float, String) -> Unit) {
    val context = LocalContext.current
    val locationTracker = remember { LocationTracker(context) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    var distance by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    var route by remember { mutableStateOf<List<Location>>(emptyList()) }
    var activity by remember { mutableStateOf("Walk") }
    val finished = remember { AtomicBoolean(false) }
    val latestSteps by rememberUpdatedState(tracker.steps)
    val latestElapsed by rememberUpdatedState(elapsed)
    val latestDistance by rememberUpdatedState(distance)
    val latestActivity by rememberUpdatedState(activity)

    LaunchedEffect(Unit) {
        tracker.start()
        locationTracker.start { s -> distance = s.distanceMeters; accuracy = s.accuracyMeters; route = s.route }
        while (true) {
            delay(1000)
            if (running) { elapsed++; locationTracker.addElapsedSeconds(elapsed) }
        }
    }
    DisposableEffect(Unit) { onDispose { tracker.stop(); locationTracker.stop() } }

    fun finishOnce() {
        if (finished.compareAndSet(false, true)) onFinish(latestSteps, latestElapsed, latestDistance, v2WorkoutCalories(profile, latestActivity, latestElapsed), latestActivity)
    }
    BackHandler { finishOnce() }

    val calories = v2WorkoutCalories(profile, activity, elapsed)
    val pace = if (distance >= 20f && elapsed > 0) elapsed.toFloat() * 1000f / distance else 0f

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            V2RouteMap(route)
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xCC1E1E1E)), modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Text(if (accuracy > 0f) "GPS ±${accuracy.roundToInt()} m" else "Waiting for GPS", color = if (accuracy in 0.1f..20f) Green else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Card(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Live ${activity.lowercase()}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Row {
                        FilterChip(selected = activity == "Walk", onClick = { if (!running) activity = "Walk" }, label = { Text("Walk") })
                        Spacer(Modifier.size(6.dp))
                        FilterChip(selected = activity == "Run", onClick = { if (!running) activity = "Run" }, label = { Text("Run") })
                    }
                }
                Text("%02d:%02d:%02d".format(elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    V2Live("Steps", tracker.steps.toString()); V2Live("Distance", "%.2f km".format(distance / 1000f)); V2Live("Pace", if (pace > 0f) v2Pace(pace) else "—"); V2Live("Calories", "${calories.roundToInt()} kcal")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { if (running) { tracker.pause(); locationTracker.pause(); running = false } else { tracker.resume(); locationTracker.resume(); running = true } }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) {
                        Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text(if (running) "Pause" else "Resume")
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
private fun V2Live(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(label, color = Muted, fontSize = 10.sp) } }

private fun v2WorkoutCalories(profile: ProfileStore, activity: String, seconds: Long): Float {
    if (seconds <= 0) return 0f
    val weight = profile.weightKg.coerceIn(30f, 250f)
    val met = if (activity == "Run") 7.0 else 3.5
    return (met * 3.5 * weight / 200.0 * (seconds / 60.0)).toFloat()
}

private fun v2Pace(secondsPerKm: Float): String { val s = secondsPerKm.roundToInt().coerceAtMost(5999); return "%d:%02d /km".format(s / 60, s % 60) }

@Composable
fun WorkoutExperienceProgress(stepStore: StepDataStore, workoutStore: WorkoutStore, profile: ProfileStore) {
    var selected by remember { mutableIntStateOf(1) }
    val labels = listOf("Day", "Week", "Month", "Year")
    val days = when (selected) { 0 -> 1; 1 -> 7; 2 -> 30; else -> 365 }
    val values = stepStore.dailyBuckets(days)
    val totalSteps = values.sum()
    val sessions = workoutStore.forRange(v2StartOfDay() - (days - 1L) * V2_DAY_MS, v2StartOfDay() + V2_DAY_MS)
    val distance = v2Distance(totalSteps, profile)
    val calories = v2StepCalories(totalSteps, profile)
    val active = sessions.sumOf { it.durationSeconds } / 60L
    val points = values.takeLast(minOf(12, values.size))

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your Progress", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) { labels.forEachIndexed { i, label -> FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(label) }) } } }
        item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(18.dp)) { Text("Steps", color = Muted); Text("%,d".format(totalSteps), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(20.dp)); V2BarChart(points) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { V2Metric("📏", "Distance", "%.2f km".format(distance / 1000f), Modifier.weight(1f)); V2Metric("🔥", "Calories", "${calories.roundToInt()} kcal", Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { V2Metric("⏱", "Active Time", "$active min", Modifier.weight(1f)); V2Metric("👟", "Workouts", sessions.size.toString(), Modifier.weight(1f)) } }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Workout history", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (sessions.isEmpty()) Text("No Walk / Run sessions recorded in this period.", color = Muted, fontSize = 13.sp)
                    else sessions.takeLast(10).reversed().forEach { s ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("${s.activity} • ${workoutStore.formatDate(s.date)}", color = Color.White, fontWeight = FontWeight.Bold); Text("${s.steps} steps • %.2f km • ${s.calories.roundToInt()} kcal".format(s.distanceMeters / 1000f), color = Muted, fontSize = 12.sp) }
                            Text("%02d:%02d".format(s.durationSeconds / 60, s.durationSeconds % 60), color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V2BarChart(values: List<Int>) {
    val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        values.forEach { value -> Box(Modifier.width(18.dp).height((120f * value / maxValue).coerceAtLeast(4f).dp).background(Green, RoundedCornerShape(8.dp))) }
    }
}

@Composable
private fun V2RouteMap(route: List<Location>) {
    var pageReady by remember { mutableStateOf(false) }
    val latestRoute by rememberUpdatedState(route)
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    val points = latestRoute.joinToString(",") { "[${it.latitude},${it.longitude}]" }
                    if (points.isNotEmpty()) view?.evaluateJavascript("window.updateRoute([$points]);", null)
                }
            }
            loadDataWithBaseURL("https://www.openstreetmap.org/", v2MapHtml(), "text/html", "UTF-8", null)
        }
    }, update = { web ->
        if (pageReady && route.isNotEmpty()) {
            val points = route.joinToString(",") { "[${it.latitude},${it.longitude}]" }
            web.evaluateJavascript("window.updateRoute([$points]);", null)
        }
    }, modifier = Modifier.fillMaxSize())
}

private fun v2MapHtml() = """
<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no'>
<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>
<style>html,body,#map{height:100%;width:100%;margin:0;background:#121212}</style></head><body><div id='map'></div>
<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>
let map=null,line=null,marker=null,ready=false;
function boot(){if(typeof L==='undefined'){setTimeout(boot,250);return;} map=L.map('map',{zoomControl:true}).setView([20.5937,78.9629],5); L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map); line=L.polyline([],{color:'#00E676',weight:6,lineCap:'round',lineJoin:'round'}).addTo(map); ready=true; setTimeout(function(){map.invalidateSize();},150);}
window.updateRoute=function(points){if(!ready||!points||!points.length)return; line.setLatLngs(points); if(marker)map.removeLayer(marker); marker=L.circleMarker(points[points.length-1],{radius:7,color:'#00B0FF',weight:3,fillColor:'#00E676',fillOpacity:1}).addTo(map); if(points.length===1)map.setView(points[0],17);else map.fitBounds(line.getBounds(),{padding:[30,30],maxZoom:17}); setTimeout(function(){map.invalidateSize();},50);}; boot();
</script></body></html>
"""
