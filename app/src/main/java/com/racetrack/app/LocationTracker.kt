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
        currentLocation = null
    }

    private fun acceptLocation(location: Location, initial: Boolean) {
        val acquisitionMaxAccuracy = 50f
        if (location.accuracy <= 0f || location.accuracy > acquisitionMaxAccuracy) {
            statusCallback?.invoke("GPS accuracy is too low (${location.accuracy.roundToInt()} m)")
            return
        }

        currentLocation = Location(location)

        val trackingMaxAccuracy = 25f
        val previous = lastTrackingLocation
        if (location.accuracy > trackingMaxAccuracy) {
            statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m • waiting for accurate tracking fix")
            publish(location.speedOrZero())
            return
        }

        if (previous != null) {
            val dt = ((location.time - previous.time).coerceAtLeast(500L)) / 1000f
            val segment = previous.distanceTo(location)
            val movementThreshold = max(3f, max(previous.accuracy, location.accuracy) * 0.75f)
            val maxSpeed = if (location.hasSpeed() && location.speed >= 0f) max(12f, location.speed + 6f) else 8f
            val maxSegment = maxSpeed * dt + max(2f, max(previous.accuracy, location.accuracy) * 0.25f)

            if (segment > maxSegment) {
                statusCallback?.invoke("GPS jump ignored")
                publish(location.speedOrZero())
                return
            }

            if (segment >= movementThreshold) totalMeters += segment
        }

        lastTrackingLocation = Location(location)
        routePoints.add(Location(location))
        if (routePoints.size > 2000) routePoints.removeAt(0)
        statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m")
        publish(location.speedOrZero())
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
