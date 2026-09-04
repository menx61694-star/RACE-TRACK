package com.racetrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class LocationTracker(private val context: Context) {
    data class Snapshot(
        val distanceMeters: Float = 0f,
        val currentSpeedMps: Float = 0f,
        val averageSpeedMps: Float = 0f,
        val accuracyMeters: Float = 0f,
        val route: List<Location> = emptyList(),
        val currentLocation: Location? = null
    )

    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var callback: ((Snapshot) -> Unit)? = null
    private var statusCallback: ((String) -> Unit)? = null
    private var active = false
    private var paused = false

    private var lastRouteLocation: Location? = null
    private var previousRawLocation: Location? = null
    private var currentLocation: Location? = null
    private var sessionStarted = false

    private var totalMeters = 0f
    private var elapsedSeconds = 0L
    private val routePoints = mutableListOf<Location>()

    var snapshot: Snapshot = Snapshot()
        private set

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!active || paused) return
            result.locations.forEach { acceptLocation(it, initial = false) }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(onUpdate: (Snapshot) -> Unit, onStatus: (String) -> Unit = {}) {
        callback = onUpdate
        statusCallback = onStatus
        resetSession()
        RouteReplaySession.clear()
        if (!hasLocationPermission()) {
            onStatus("Location permission is not granted")
            onUpdate(snapshot)
            return
        }
        active = true
        if (!isLocationEnabled()) {
            onStatus("Location services are OFF")
            return
        }
        requestSettingsAndLocation()
    }

    fun retry() {
        if (!active || paused) return
        if (!hasLocationPermission()) {
            statusCallback?.invoke("Location permission is not granted")
            return
        }
        if (!isLocationEnabled()) {
            statusCallback?.invoke("Location services are OFF")
            return
        }
        requestSettingsAndLocation()
    }

    @SuppressLint("MissingPermission")
    private fun requestSettingsAndLocation() {
        if (!active || paused) return
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1f)
            .setWaitForAccurateLocation(true)
            .build()

        LocationServices.getSettingsClient(context)
            .checkLocationSettings(
                LocationSettingsRequest.Builder()
                    .addLocationRequest(request)
                    .setAlwaysShow(true)
                    .build()
            )
            .addOnSuccessListener { requestFreshLocation(priority) }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) statusCallback?.invoke("Turn on Location / GPS")
                else statusCallback?.invoke("Location settings unavailable")
                requestFreshLocation(priority)
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(priority: Int) {
        if (!active || paused) return
        val request = LocationRequest.Builder(priority, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1f)
            .setWaitForAccurateLocation(true)
            .build()
        val currentRequest = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(2_000L)
            .setDurationMillis(15_000L)
            .build()

        statusCallback?.invoke("Acquiring GPS fix…")
        client.getCurrentLocation(currentRequest, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (!active || paused || location == null) return@addOnSuccessListener
                // This callback can race with the continuous location callback.
                // Never let a late one-shot fix reset an already-started route.
                acceptLocation(location, initial = !sessionStarted)
            }
            .addOnFailureListener { error -> statusCallback?.invoke("GPS fix failed: ${error.javaClass.simpleName}") }

        client.requestLocationUpdates(request, locationCallback, context.mainLooper)
            .addOnFailureListener { error -> statusCallback?.invoke("GPS updates failed: ${error.javaClass.simpleName}") }
    }

    private fun resetSession() {
        active = false
        paused = false
        lastRouteLocation = null
        previousRawLocation = null
        currentLocation = null
        sessionStarted = false
        totalMeters = 0f
        elapsedSeconds = 0L
        routePoints.clear()
        snapshot = Snapshot()
        client.removeLocationUpdates(locationCallback)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun addElapsedSeconds(seconds: Long) {
        if (!active || paused) return
        elapsedSeconds = max(0L, seconds)
        publish(lastRouteLocation?.speedOrZero() ?: 0f)
    }

    @SuppressLint("MissingPermission")
    fun pause() {
        if (!active || paused) return
        paused = true
        client.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    fun resume() {
        if (!active || !paused) return
        paused = false
        retry()
    }

    fun stop() {
        active = false
        paused = false
        client.removeLocationUpdates(locationCallback)
        callback = null
        statusCallback = null
        lastRouteLocation = null
        previousRawLocation = null
        currentLocation = null
        sessionStarted = false
    }

    private fun acceptLocation(location: Location, initial: Boolean) {
        val acquisitionMaxAccuracy = 50f
        if (location.accuracy <= 0f || location.accuracy > acquisitionMaxAccuracy) {
            statusCallback?.invoke("GPS accuracy is too low (${location.accuracy.roundToInt()} m)")
            return
        }

        currentLocation = Location(location)

        // A route starts only from a genuinely fresh fix. In particular, do not
        // use a cached lastLocation as the first recorded route point.
        if (!sessionStarted) {
            sessionStarted = true
            lastRouteLocation = Location(location)
            previousRawLocation = Location(location)
            routePoints.add(Location(location))
            statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m")
            publish(location.speedOrZero())
            return
        }

        val previousRaw = previousRawLocation ?: run {
            previousRawLocation = Location(location)
            return
        }

        val timeDeltaMillis = elapsedRealtimeDeltaMillis(previousRaw, location)
        if (timeDeltaMillis <= 0L) {
            statusCallback?.invoke("Duplicate/out-of-order GPS fix ignored")
            return
        }
        val dt = (timeDeltaMillis / 1000f).coerceAtLeast(0.1f)
        val rawSegment = previousRaw.distanceTo(location)
        val reportedSpeed = location.speedOrZero()
        val previousSpeed = previousRaw.speedOrZero()

        // Reject impossible jumps, while allowing a large safety margin for
        // walking/running and temporary GPS drift.
        val speedLimit = max(12f, max(reportedSpeed, previousSpeed) + 5f)
        val accuracyAllowance = min(20f, max(3f, max(previousRaw.accuracy, location.accuracy) * 0.45f))
        val maxRawSegment = speedLimit * dt + accuracyAllowance
        if (rawSegment > maxRawSegment) {
            statusCallback?.invoke("GPS jump rejected (${rawSegment.roundToInt()} m)")
            // Do not move previousRawLocation to the spike. The next fix is
            // compared against the last trustworthy point instead.
            publish(reportedSpeed)
            return
        }

        // A very fast 180-degree bearing flip over a meaningful distance is
        // usually multipath noise. Do not apply this to slow movement/turns.
        if (rawSegment >= 10f && reportedSpeed >= 2.5f && previousSpeed >= 2.5f &&
            previousRaw.hasBearing() && location.hasBearing()) {
            val bearingDelta = angularDifference(previousRaw.bearing, location.bearing)
            if (bearingDelta > 155f && rawSegment < maxRawSegment * 0.7f) {
                statusCallback?.invoke("GPS direction spike rejected")
                publish(reportedSpeed)
                return
            }
        }

        val routeAccuracyLimit = 35f
        if (location.accuracy > routeAccuracyLimit) {
            // Keep the live marker current, but don't contaminate the recorded
            // route or its distance with a weak fix.
            statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m • waiting for better fix")
            previousRawLocation = Location(location)
            publish(reportedSpeed)
            return
        }

        val previousRoute = lastRouteLocation ?: run {
            lastRouteLocation = Location(location)
            previousRawLocation = Location(location)
            routePoints.add(Location(location))
            publish(reportedSpeed)
            return
        }

        // Accuracy-aware light filtering only. The accepted point stays close to
        // the actual GPS measurement; no road snapping or heavy moving average.
        // High-quality fixes are followed closely so different lines on the same
        // road remain visible.
        val accuracy = location.accuracy.coerceIn(3f, routeAccuracyLimit)
        val accuracyTrust = (1f - ((accuracy - 3f) / (routeAccuracyLimit - 3f))).coerceIn(0f, 1f)
        val movement = rawSegment / dt
        val movementTrust = (movement / 4f).coerceIn(0f, 1f)
        val alpha = (0.78f + accuracyTrust * 0.16f + movementTrust * 0.05f).coerceIn(0.78f, 0.97f)
        val filtered = blendLocations(previousRoute, location, alpha)
        val filteredSegment = previousRoute.distanceTo(filtered)

        // Preserve real movement while dropping only tiny GPS jitter.
        val minimumRouteMovement = when {
            movement < 0.8f -> 1.0f
            movement < 2.0f -> 0.7f
            else -> 0.5f
        }

        if (filteredSegment >= minimumRouteMovement) {
            totalMeters += filteredSegment
            lastRouteLocation = Location(filtered)
            routePoints.add(Location(filtered))
            if (routePoints.size > 5000) routePoints.removeAt(0)
        }

        previousRawLocation = Location(location)
        statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m")
        publish(reportedSpeed)
    }

    private fun elapsedRealtimeDeltaMillis(previous: Location, current: Location): Long {
        val previousElapsed = previous.elapsedRealtimeNanos
        val currentElapsed = current.elapsedRealtimeNanos
        return if (previousElapsed > 0L && currentElapsed > 0L) {
            (currentElapsed - previousElapsed) / 1_000_000L
        } else {
            current.time - previous.time
        }
    }

    private fun blendLocations(from: Location, to: Location, alpha: Float): Location {
        val result = Location(to)
        result.latitude = from.latitude + (to.latitude - from.latitude) * alpha
        result.longitude = from.longitude + (to.longitude - from.longitude) * alpha
        if (from.hasAltitude() && to.hasAltitude()) {
            result.altitude = from.altitude + (to.altitude - from.altitude) * alpha
        }
        return result
    }

    private fun angularDifference(first: Float, second: Float): Float {
        var difference = (second - first) % 360f
        if (difference < -180f) difference += 360f
        if (difference > 180f) difference -= 360f
        return abs(difference)
    }

    private fun publish(speed: Float) {
        val average = if (elapsedSeconds > 0) totalMeters / elapsedSeconds else 0f
        val accuracy = currentLocation?.accuracy ?: 0f
        snapshot = Snapshot(
            distanceMeters = totalMeters,
            currentSpeedMps = speed,
            averageSpeedMps = average,
            accuracyMeters = accuracy,
            route = routePoints.toList(),
            currentLocation = currentLocation?.let { Location(it) }
        )
        RouteReplaySession.update(snapshot.route, snapshot.distanceMeters, elapsedSeconds)
        callback?.invoke(snapshot)
    }

    private fun Location.speedOrZero(): Float = if (hasSpeed()) speed.coerceAtLeast(0f) else 0f
}
