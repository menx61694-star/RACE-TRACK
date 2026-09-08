package com.racetrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
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
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ((Snapshot) -> Unit)? = null
    private var statusCallback: ((String) -> Unit)? = null
    private var active = false
    private var paused = false
    private var updatesRequested = false
    private var lastFixElapsedRealtimeNanos = 0L

    private var lastRouteLocation: Location? = null
    private var previousRawLocation: Location? = null
    private var currentLocation: Location? = null
    private var sessionStarted = false

    private var totalMeters = 0f
    private var elapsedSeconds = 0L
    private val routePoints = mutableListOf<Location>()

    var snapshot: Snapshot = Snapshot()
        private set

    private val retryRunnable = object : Runnable {
        override fun run() {
            if (!active || paused) return
            val now = android.os.SystemClock.elapsedRealtimeNanos()
            val noRecentFix = lastFixElapsedRealtimeNanos == 0L ||
                (now - lastFixElapsedRealtimeNanos) > 5_000_000_000L
            if (noRecentFix) requestSettingsAndLocation(showAcquiringStatus = false)
            handler.postDelayed(this, 5_000L)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!active || paused) return
            result.locations.forEach { acceptLocation(it) }
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!active || paused) return
            if (!availability.isLocationAvailable) {
                statusCallback?.invoke("GPS signal unavailable • searching…")
            }
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
        handler.postDelayed(retryRunnable, 5_000L)
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
    private fun requestSettingsAndLocation(showAcquiringStatus: Boolean = true) {
        if (!active || paused) return
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = buildLocationRequest(priority)

        LocationServices.getSettingsClient(context)
            .checkLocationSettings(
                LocationSettingsRequest.Builder()
                    .addLocationRequest(request)
                    .setAlwaysShow(true)
                    .build()
            )
            .addOnSuccessListener { requestFreshLocation(priority, showAcquiringStatus) }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) statusCallback?.invoke("Turn on Location / GPS")
                else statusCallback?.invoke("Location settings unavailable")
                requestFreshLocation(priority, showAcquiringStatus)
            }
    }

    private fun buildLocationRequest(priority: Int): LocationRequest =
        LocationRequest.Builder(priority, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1f)
            // Waiting for an especially accurate fix can make the tracker appear
            // frozen. The acceptance filter below handles accuracy instead.
            .setWaitForAccurateLocation(false)
            .build()

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(priority: Int, showAcquiringStatus: Boolean) {
        if (!active || paused) return
        val request = buildLocationRequest(priority)
        val currentRequest = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(1_000L)
            .setDurationMillis(10_000L)
            .build()

        client.removeLocationUpdates(locationCallback)
        updatesRequested = false
        if (showAcquiringStatus) statusCallback?.invoke("Acquiring GPS fix…")

        client.getCurrentLocation(currentRequest, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (!active || paused || location == null) return@addOnSuccessListener
                acceptLocation(location)
            }
            .addOnFailureListener { error ->
                if (active && !paused) statusCallback?.invoke("GPS retrying… (${error.javaClass.simpleName})")
            }

        client.requestLocationUpdates(request, locationCallback, context.mainLooper)
            .addOnSuccessListener {
                updatesRequested = true
            }
            .addOnFailureListener { error ->
                updatesRequested = false
                if (active && !paused) statusCallback?.invoke("GPS updates failed: ${error.javaClass.simpleName}")
            }
    }

    private fun resetSession() {
        active = false
        paused = false
        updatesRequested = false
        lastFixElapsedRealtimeNanos = 0L
        handler.removeCallbacks(retryRunnable)
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
        handler.removeCallbacks(retryRunnable)
        client.removeLocationUpdates(locationCallback)
        updatesRequested = false
    }

    @SuppressLint("MissingPermission")
    fun resume() {
        if (!active || !paused) return
        paused = false
        retry()
        handler.postDelayed(retryRunnable, 5_000L)
    }

    fun stop() {
        active = false
        paused = false
        handler.removeCallbacks(retryRunnable)
        client.removeLocationUpdates(locationCallback)
        updatesRequested = false
        callback = null
        statusCallback = null
        lastRouteLocation = null
        previousRawLocation = null
        currentLocation = null
        sessionStarted = false
    }

    private fun acceptLocation(location: Location) {
        if (!location.hasAccuracy() || location.accuracy <= 0f) {
            statusCallback?.invoke("Waiting for GPS accuracy…")
            return
        }

        val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val locationElapsed = location.elapsedRealtimeNanos
        if (locationElapsed > 0L && nowNanos - locationElapsed > 15_000_000_000L) {
            statusCallback?.invoke("Stale GPS fix ignored")
            return
        }

        // Allow a coarse first fix to put the blue/current-position marker on the
        // map, but only count reasonably accurate points toward the route distance.
        val acquisitionMaxAccuracy = 80f
        if (location.accuracy > acquisitionMaxAccuracy) {
            statusCallback?.invoke("GPS accuracy is too low (${location.accuracy.roundToInt()} m)")
            return
        }

        lastFixElapsedRealtimeNanos = nowNanos
        currentLocation = Location(location)

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

        val speedLimit = max(12f, max(reportedSpeed, previousSpeed) + 5f)
        val accuracyAllowance = min(30f, max(5f, max(previousRaw.accuracy, location.accuracy) * 0.60f))
        val maxRawSegment = speedLimit * dt + accuracyAllowance
        if (rawSegment > maxRawSegment) {
            // Crucial recovery fix: advance the raw anchor even when a point is
            // rejected. Keeping the old bad anchor caused repeated jump rejection.
            previousRawLocation = Location(location)
            statusCallback?.invoke("GPS jump rejected (${rawSegment.roundToInt()} m)")
            publish(reportedSpeed)
            return
        }

        val routeAccuracyLimit = 65f
        if (location.accuracy > routeAccuracyLimit) {
            previousRawLocation = Location(location)
            statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m • waiting for better fix")
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

        val filtered = Location(location)
        val filteredSegment = previousRoute.distanceTo(filtered)
        val movement = rawSegment / dt
        val minimumRouteMovement = when {
            movement < 0.8f -> 0.75f
            movement < 2.0f -> 0.55f
            else -> 0.35f
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
