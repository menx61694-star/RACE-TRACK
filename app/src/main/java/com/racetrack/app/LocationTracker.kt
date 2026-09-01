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
        val route: List<Location> = emptyList()
    )

    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var callback: ((Snapshot) -> Unit)? = null
    private var statusCallback: ((String) -> Unit)? = null
    private var active = false
    private var paused = false
    private var lastLocation: Location? = null
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
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        callback = onUpdate
        statusCallback = onStatus

        if (!hasFine && !hasCoarse) {
            onStatus("Location permission is not granted")
            onUpdate(snapshot)
            return
        }

        active = true
        paused = false
        lastLocation = null
        totalMeters = 0f
        elapsedSeconds = 0L
        routePoints.clear()
        snapshot = Snapshot()

        if (!isLocationEnabled()) {
            onStatus("Location services are OFF")
            return
        }

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
                if (error is ResolvableApiException) onStatus("Turn on Location / GPS")
                else onStatus("Location settings unavailable")
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
            .addOnFailureListener { error ->
                statusCallback?.invoke("GPS fix failed: ${error.javaClass.simpleName}")
            }

        client.requestLocationUpdates(request, locationCallback, context.mainLooper)
            .addOnFailureListener { error ->
                statusCallback?.invoke("GPS updates failed: ${error.javaClass.simpleName}")
            }

        client.lastLocation.addOnSuccessListener { location ->
            if (!active || paused || location == null || lastLocation != null) return@addOnSuccessListener
            if (location.accuracy in 0.1f..100f && System.currentTimeMillis() - location.time <= 120_000L) {
                acceptLocation(location, initial = true)
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun addElapsedSeconds(seconds: Long) {
        if (!active || paused) return
        elapsedSeconds = max(0L, seconds)
        publish(lastLocation?.speedOrZero() ?: 0f)
    }

    @SuppressLint("MissingPermission")
    fun pause() {
        if (!active || paused) return
        paused = true
        client.removeLocationUpdates(locationCallback)
        lastLocation = null
    }

    @SuppressLint("MissingPermission")
    fun resume() {
        if (!active || !paused) return
        paused = false
        if (!isLocationEnabled()) {
            statusCallback?.invoke("Location services are OFF")
            return
        }
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        requestFreshLocation(priority)
    }

    fun stop() {
        active = false
        paused = false
        client.removeLocationUpdates(locationCallback)
        callback = null
        statusCallback = null
        lastLocation = null
    }

    private fun acceptLocation(location: Location, initial: Boolean) {
        val maxAccuracy = if (initial) 100f else 50f
        if (location.accuracy <= 0f || location.accuracy > maxAccuracy) {
            statusCallback?.invoke("GPS accuracy is too low (${location.accuracy.roundToInt()} m)")
            return
        }

        val previous = lastLocation
        if (previous != null) {
            val dt = ((location.time - previous.time).coerceAtLeast(500L)) / 1000f
            val segment = previous.distanceTo(location)
            val speed = location.speedOrZero()
            val maxSpeed = if (speed > 0f) max(10f, speed + 5f) else 10f
            val maxSegment = (maxSpeed * dt + max(previous.accuracy, location.accuracy)).coerceAtLeast(10f)
            if (segment > maxSegment) return
            if (segment >= 1.5f) totalMeters += segment
        }

        lastLocation = Location(location)
        routePoints.add(Location(location))
        if (routePoints.size > 2000) routePoints.removeAt(0)
        statusCallback?.invoke("GPS ± ${location.accuracy.roundToInt()} m")
        publish(location.speedOrZero())
    }

    private fun publish(speed: Float) {
        val average = if (elapsedSeconds > 0) totalMeters / elapsedSeconds else 0f
        val accuracy = lastLocation?.accuracy ?: 0f
        snapshot = Snapshot(totalMeters, speed, average, accuracy, routePoints.toList())
        callback?.invoke(snapshot)
    }

    private fun Location.speedOrZero(): Float = if (hasSpeed()) speed.coerceAtLeast(0f) else 0f
}
