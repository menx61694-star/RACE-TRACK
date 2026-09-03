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
        // Keep route continuity through normal temporary GPS degradation.
        val acquisitionMaxAccuracy = 60f
        if (location.accuracy <= 0f || location.accuracy > acquisitionMaxAccuracy) {
            statusCallback?.invoke("GPS accuracy is too low (${location.accuracy.roundToInt()} m)")
            return
        }

        // Keep the raw fix for the live marker/speed, while the route itself is
        // generated from an adaptive filter. This removes small GPS zig-zags
        // without applying a fixed smoothing amount that would lag at turns.
        currentLocation = Location(location)

        val trackingMaxAccuracy = 50f
        if (location.accuracy > trackingMaxAccuracy) {
            statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m • reacquiring")
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

        val rawDt = ((location.time - previousRaw.time).coerceAtLeast(500L)) / 1000f
        val rawSegment = previousRaw.distanceTo(location)
        val quality = location.accuracy.coerceIn(3f, 50f)
        val previousQuality = previousRaw.accuracy.coerceIn(3f, 50f)
        val qualityRatio = min(1f, 12f / max(quality, previousQuality))

        // Reject physically implausible fixes before smoothing. A GPS spike
        // should never pull the route across a street or create a long shortcut.
        val reportedSpeed = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else 0f
        val maxSpeed = if (reportedSpeed > 0.5f) max(9f, reportedSpeed + 7f) else 9f
        val maxRawSegment = maxSpeed * rawDt + max(4f, quality * 0.5f)
        if (rawSegment > maxRawSegment) {
            rawPreviousLocation = Location(location)
            statusCallback?.invoke("GPS jump filtered")
            publish(location.speedOrZero())
            return
        }

        // Adaptive exponential smoothing:
        // - better accuracy -> trust the new fix more
        // - real movement -> respond faster, reducing corner/turn lag
        // - near stationary -> smooth harder to suppress GPS drift
        val moving = reportedSpeed >= 1.0f || rawSegment >= 4f
        val baseAlpha = when {
            quality <= 6f -> 0.68f
            quality <= 10f -> 0.58f
            quality <= 20f -> 0.48f
            else -> 0.38f
        }
        val movementBoost = if (moving) 0.16f else -0.08f
        val alpha = (baseAlpha * qualityRatio + movementBoost).coerceIn(0.28f, 0.82f)

        val filtered = blendLocations(previousSmoothed, location, alpha)
        val filteredSegment = previousSmoothed.distanceTo(filtered)

        // Do not count tiny filter residue as movement. Distance is measured on
        // the same filtered route that is drawn on the map, keeping both consistent.
        val movementThreshold = max(1.5f, quality * 0.18f)
        if (filteredSegment >= movementThreshold) {
            totalMeters += filteredSegment
            lastTrackingLocation = Location(filtered)
        }

        smoothedLocation = filtered
        rawPreviousLocation = Location(location)

        routePoints.add(Location(filtered))
        if (routePoints.size > 2000) routePoints.removeAt(0)
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
