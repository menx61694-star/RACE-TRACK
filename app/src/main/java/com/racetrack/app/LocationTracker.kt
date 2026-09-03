package com.racetrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.tasks.CancellationTokenSource
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

    private var lastTrackingLocation: Location? = null
    private var rawPreviousLocation: Location? = null
    private var smoothedLocation: Location? = null
    private var currentLocation: Location? = null

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
            .setWaitForAccurateLocation(false)
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
            .setWaitForAccurateLocation(false)
            .build()
        val currentRequest = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(5_000L)
            .setDurationMillis(15_000L)
            .build()

        statusCallback?.invoke("Acquiring GPS fix…")
        client.getCurrentLocation(currentRequest, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (!active || paused) return@addOnSuccessListener
                if (location != null) acceptLocation(location, initial = true)
                else statusCallback?.invoke("Searching for GPS signal…")
            }
            .addOnFailureListener { error -> statusCallback?.invoke("GPS fix failed: ${error.javaClass.simpleName}") }

        client.requestLocationUpdates(request, locationCallback, context.mainLooper)
            .addOnFailureListener { error -> statusCallback?.invoke("GPS updates failed: ${error.javaClass.simpleName}") }

        client.lastLocation.addOnSuccessListener { location ->
            if (!active || paused || location == null || currentLocation != null) return@addOnSuccessListener
            if (location.accuracy in 0.1f..50f && System.currentTimeMillis() - location.time <= 120_000L) {
                acceptLocation(location, initial = true)
            }
        }
    }

    private fun resetSession() {
        active = false
        paused = false
        lastTrackingLocation = null
        rawPreviousLocation = null
        smoothedLocation = null
        currentLocation = null
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
        publish(lastTrackingLocation?.speedOrZero() ?: 0f)
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
        lastTrackingLocation = null
        rawPreviousLocation = null
        smoothedLocation = null
        currentLocation = null
    }

    private fun acceptLocation(location: Location, initial: Boolean) {
        // A high-accuracy fused fix is the source of truth for the track.
        // Keep a looser acquisition limit for the live marker, but do not let
        // low-quality fixes draw large false shapes on the recorded route.
        val acquisitionMaxAccuracy = 60f
        if (location.accuracy <= 0f || location.accuracy > acquisitionMaxAccuracy) {
            statusCallback?.invoke("GPS accuracy is too low (${location.accuracy.roundToInt()} m)")
            return
        }

        currentLocation = Location(location)

        // Route points use a stricter quality gate than the live marker.
        // 0-25 m: normal. 25-35 m: usable only when movement is consistent.
        // Above 35 m: keep the marker, but wait for a better route fix.
        val routeAccuracyLimit = 35f
        if (location.accuracy > routeAccuracyLimit) {
            statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m • waiting for better fix")
            publish(location.speedOrZero())
            return
        }

        val previousRaw = rawPreviousLocation
        val previousSmoothed = smoothedLocation

        if (previousRaw == null || previousSmoothed == null || initial) {
            smoothedLocation = Location(location)
            lastTrackingLocation = Location(location)
            rawPreviousLocation = Location(location)
            routePoints.add(Location(location))
            statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m")
            publish(location.speedOrZero())
            return
        }

        // Use the actual provider timestamps. Do not invent a 500 ms interval:
        // doing so can make a delayed/batched fix look like an impossible jump.
        val timeDeltaMillis = location.time - previousRaw.time
        if (timeDeltaMillis <= 0L) {
            statusCallback?.invoke("Duplicate/out-of-order GPS fix ignored")
            return
        }
        val dt = (timeDeltaMillis / 1000f).coerceAtLeast(0.1f)
        val rawSegment = previousRaw.distanceTo(location)

        val reportedSpeed = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else 0f
        val previousSpeed = if (previousRaw.hasSpeed()) previousRaw.speed.coerceAtLeast(0f) else 0f

        // Running/walking should never produce an 80-100 m teleport in a short
        // interval. Allow generous headroom, but reject obvious GPS spikes.
        val maxRealisticSpeed = 15f // 54 km/h; deliberately generous for running.
        val speedLimit = max(maxRealisticSpeed, max(reportedSpeed, previousSpeed) + 6f)
        val accuracyAllowance = max(5f, max(previousRaw.accuracy, location.accuracy) * 0.35f)
        val maxRawSegment = speedLimit * dt + accuracyAllowance

        if (rawSegment > maxRawSegment) {
            // Do NOT connect the spike to the route. Keep the last good route
            // point intact and wait for the next trustworthy fix.
            statusCallback?.invoke("GPS jump rejected (${rawSegment.roundToInt()} m)")
            publish(location.speedOrZero())
            return
        }

        // Very large lateral reversals in one short interval are a common GPS
        // multipath/bounce signature. Only apply this check at real running speed
        // and for a meaningful displacement so genuine turns are preserved.
        if (rawSegment >= 12f && reportedSpeed >= 3f && previousSpeed >= 3f &&
            previousRaw.hasBearing() && location.hasBearing()) {
            val bearingDelta = angularDifference(previousRaw.bearing, location.bearing)
            if (bearingDelta > 140f && rawSegment < maxRawSegment * 0.65f) {
                statusCallback?.invoke("GPS direction spike rejected")
                return
            }
        }

        // Light adaptive smoothing. This is intentionally much closer to the
        // measured GPS point than a heavy moving average: it removes jitter while
        // preserving lane/side changes and real turns between repeated laps.
        val accuracy = location.accuracy.coerceIn(3f, routeAccuracyLimit)
        val accuracyTrust = (1f - ((accuracy - 3f) / (routeAccuracyLimit - 3f))).coerceIn(0f, 1f)
        val movement = rawSegment / dt
        val movementTrust = (movement / 5f).coerceIn(0f, 1f)
        val alpha = (0.62f + accuracyTrust * 0.18f + movementTrust * 0.14f).coerceIn(0.62f, 0.94f)

        val filtered = blendLocations(previousSmoothed, location, alpha)
        val filteredSegment = previousSmoothed.distanceTo(filtered)

        // Small sub-meter/low-noise changes are not useful route vertices, but
        // normal running movement is retained. No fixed 3/5/10 m cutoff is used.
        val minimumRouteMovement = if (movement < 1.2f) 1.0f else 0.75f
        if (filteredSegment >= minimumRouteMovement) {
            totalMeters += filteredSegment
            lastTrackingLocation = Location(filtered)
            routePoints.add(Location(filtered))
            if (routePoints.size > 4000) routePoints.removeAt(0)
        }

        smoothedLocation = filtered
        rawPreviousLocation = Location(location)
        statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m")
        publish(location.speedOrZero())
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
        return kotlin.math.abs(difference)
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
