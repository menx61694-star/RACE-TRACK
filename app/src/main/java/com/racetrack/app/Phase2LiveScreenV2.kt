package com.racetrack.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean

class FusedRouteTracker(context: Context) {
    data class Snapshot(
        val distanceMeters: Float = 0f,
        val currentSpeedMps: Float = 0f,
        val route: List<Location> = emptyList(),
        val accuracyMeters: Float = 0f
    )

    private val context = context.applicationContext
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var callback: ((Snapshot) -> Unit)? = null
    private var lastLocation: Location? = null
    private val points = mutableListOf<Location>()
    private var totalMeters = 0f
    private var active = false

    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
        .setMinUpdateIntervalMillis(1000L)
        .setMinUpdateDistanceMeters(2f)
        .setWaitForAccurateLocation(true)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::accept)
        }
    }

    @Suppress("MissingPermission")
    fun start(onUpdate: (Snapshot) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return

        callback = onUpdate
        active = true
        lastLocation = null
        points.clear()
        totalMeters = 0f
        publish(null)

        client.lastLocation.addOnSuccessListener { location ->
            if (active && location != null && location.accuracy in 0.1f..60f) accept(location, seedOnly = true)
        }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, com.google.android.gms.tasks.CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (active && location != null) accept(location, seedOnly = lastLocation == null)
            }
        client.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper())
    }

    @Suppress("MissingPermission")
    fun resume() {
        if (active) return
        active = true
        client.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper())
    }

    fun pause() {
        if (!active) return
        active = false
        client.removeLocationUpdates(locationCallback)
    }

    fun stop() {
        active = false
        client.removeLocationUpdates(locationCallback)
        callback = null
    }

    private fun accept(raw: Location, seedOnly: Boolean = false) {
        if (!active) return
        if (raw.accuracy <= 0f || raw.accuracy > 40f) return
        val location = Location(raw)
        val previous = lastLocation

        if (previous == null) {
            lastLocation = location
            points.add(location)
            publish(location)
            return
        }
        if (seedOnly) return

        val dt = ((location.time - previous.time).coerceAtLeast(1000L)) / 1000f
        val segment = previous.distanceTo(location)
        val speed = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else 0f
        val allowed = max(40f, (max(8f, speed + 6f) * dt) + max(previous.accuracy, location.accuracy))
        if (segment > allowed) return

        if (segment >= 2f) totalMeters += segment
        lastLocation = location
        points.add(location)
        if (points.size > 1500) points.removeAt(0)
        publish(location)
    }

    private fun publish(location: Location?) {
        val speed = if (location?.hasSpeed() == true) location.speed.coerceAtLeast(0f) else 0f
        callback?.invoke(Snapshot(totalMeters, speed, points.toList(), location?.accuracy ?: 0f))
    }
}

@Composable
fun Phase2HomeScreenV2(steps: Int, profile: ProfileStore, workoutStore: WorkoutStore, onStart: () -> Unit) {
    val height = profile.heightCm.coerceIn(100f, 230f)
    val weight = profile.weightKg.coerceIn(30f, 250f)
    val strideMeters = height * 0.414f / 100f
    val estimatedDistanceKm = steps * strideMeters / 1000f
    val estimatedCalories = (weight * 0.96f * estimatedDistanceKm).roundToInt()
    val today = workoutStore.forRange(startOfDay(), startOfDay() + 86_400_000L)
    val workoutDistanceKm = today.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f
    val workoutCalories = today.sumOf { it.calories.toDouble() }.toFloat()
    val distanceKm = max(estimatedDistanceKm, workoutDistanceKm)
    val calories = max(estimatedCalories.toFloat(), workoutCalories)
    val active = today.sumOf { it.durationSeconds } / 60L
    val goal = profile.dailyGoal.coerceAtLeast(1)
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(if (profile.name.isBlank()) "Good morning" else "Good morning, ${profile.name}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text("Ready to move?", color = Muted, fontSize = 14.sp)
        }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
                    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawArc(Color(0xFF303030), -90f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(18.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                            if (progress > 0) drawArc(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Green, Cyan, Green)), -90f, 360f * progress, false, style = androidx.compose.ui.graphics.drawscope.Stroke(18.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$steps", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                            Text("/ $goal steps", color = Muted)
                            Text("${(progress * 100).roundToInt()}%", color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Today's movement", color = Muted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SimpleMetricCard("🔥", "Calories", "${calories.roundToInt()} kcal", Modifier.weight(1f))
                SimpleMetricCard("📏", "Distance", "%.2f km".format(distanceKm), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SimpleMetricCard("⏱", "Active Time", "$active min", Modifier.weight(1f))
                SimpleMetricCard("👟", "Workouts", today.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) {
                Icon(Icons.Default.LocationOn, null)
                Spacer(Modifier.size(8.dp))
                Text("Start Live Walk / Run", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun SimpleMetricCard(icon: String, title: String, value: String, modifier: Modifier) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, fontSize = 18.sp)
            Text(title, color = Muted, fontSize = 11.sp)
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Phase2LiveScreenV2(profile: ProfileStore, tracker: StepTracker, onFinish: (Int, Long, Float, Float, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val location = remember { FusedRouteTracker(context) }
    val workoutStore = remember { WorkoutStore(context) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    var distance by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    var route by remember { mutableStateOf<List<Location>>(emptyList()) }
    var activity by remember { mutableStateOf("Walk") }
    val sessionFinalized = remember { AtomicBoolean(false) }
    val weight = profile.weightKg.coerceAtLeast(1f)
    val latestSteps by rememberUpdatedState(tracker.steps)
    val latestElapsed by rememberUpdatedState(elapsed)
    val latestDistance by rememberUpdatedState(distance)
    val latestCalories by rememberUpdatedState((if (activity == "Run") 7.0 else 3.5) * 3.5 * weight / 200.0 * (elapsed / 60.0))
    val latestActivity by rememberUpdatedState(activity)

    LaunchedEffect(Unit) {
        tracker.start()
        location.start { snapshot ->
            distance = snapshot.distanceMeters
            speed = snapshot.currentSpeedMps
            accuracy = snapshot.accuracyMeters
            route = snapshot.route
        }
        while (true) {
            delay(1000)
            if (running) elapsed++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tracker.stop()
            location.stop()
            if (sessionFinalized.compareAndSet(false, true)) {
                workoutStore.saveSession(latestSteps, latestElapsed, latestDistance, latestCalories.toFloat(), latestActivity)
            }
        }
    }

    BackHandler {
        if (sessionFinalized.compareAndSet(false, true)) {
            onFinish(latestSteps, latestElapsed, latestDistance, latestCalories.toFloat(), latestActivity)
        }
    }

    val calories = latestCalories.toFloat()
    val pace = if (speed > 0.3f) 1000f / speed else 0f

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NativeRouteMap(route)
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.72f)), modifier = Modifier.padding(12.dp)) {
                Text(
                    if (accuracy <= 0f) "Waiting for GPS…" else "GPS ±${accuracy.roundToInt()} m",
                    color = if (accuracy in 0.1f..25f) Green else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
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
                    LiveMetricV2("Steps", tracker.steps.toString())
                    LiveMetricV2("Distance", "%.2f km".format(distance / 1000f))
                    LiveMetricV2("Pace", if (pace > 0) "%d:%02d /km".format((pace / 60).toInt(), (pace % 60).toInt()) else "—")
                    LiveMetricV2("Calories", "${calories.roundToInt()} kcal")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            if (running) {
                                tracker.pause()
                                location.pause()
                                running = false
                            } else {
                                tracker.resume()
                                location.resume()
                                running = true
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = CircleShape
                    ) {
                        Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (running) "Pause" else "Resume")
                    }
                    Button(
                        onClick = {
                            if (sessionFinalized.compareAndSet(false, true)) onFinish(tracker.steps, elapsed, distance, calories, activity)
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Coral)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Finish")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMetricV2(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun NativeRouteMap(route: List<Location>) {
    val latestRoute by rememberUpdatedState(route)
    val mapHolder = remember { MapHolder() }
    AndroidView(
        factory = { context ->
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setUseDataConnection(true)
                controller.setZoom(17.0)
                mapHolder.map = this
                mapHolder.ensureOverlays(this)
                onResume()
            }
        },
        update = { map ->
            mapHolder.ensureOverlays(map)
            val points = latestRoute.map { GeoPoint(it.latitude, it.longitude) }
            mapHolder.line?.setPoints(points)
            val last = points.lastOrNull()
            if (last != null) {
                mapHolder.marker?.position = last
                if (!mapHolder.centered) {
                    map.controller.animateTo(last)
                    map.controller.setZoom(17.0)
                    mapHolder.centered = true
                }
            }
            map.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
    DisposableEffect(Unit) {
        onDispose { mapHolder.map?.onPause() }
    }
}

private class MapHolder {
    var map: MapView? = null
    var line: Polyline? = null
    var marker: Marker? = null
    var centered = false

    fun ensureOverlays(mapView: MapView) {
        if (line == null) {
            line = Polyline(mapView).apply {
                outlinePaint.color = android.graphics.Color.rgb(0, 230, 118)
                outlinePaint.strokeWidth = 9f
            }
            mapView.overlays.add(line)
        }
        if (marker == null) {
            marker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Current location"
            }
            mapView.overlays.add(marker)
        }
    }
}
