package com.racetrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.max

class LocationTracker(private val context: Context) {
    data class Snapshot(
        val distanceMeters: Float = 0f,
        val currentSpeedMps: Float = 0f,
        val averageSpeedMps: Float = 0f,
        val accuracyMeters: Float = 0f,
        val route: List<Location> = emptyList()
    )

    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var callback: ((Snapshot) -> Unit)? = null
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
            result.locations.forEach(::acceptLocation)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(onUpdate: (Snapshot) -> Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            onUpdate(snapshot)
            return
        }

        callback = onUpdate
        active = true
        paused = false
        lastLocation = null
        totalMeters = 0f
        elapsedSeconds = 0L
        routePoints.clear()
        snapshot = Snapshot()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(2f)
            .setWaitForAccurateLocation(false)
            .build()

        client.requestLocationUpdates(request, locationCallback, context.mainLooper)
            .addOnFailureListener { publish(0f) }

        client.lastLocation.addOnSuccessListener { location ->
            if (!active || paused || location == null) return@addOnSuccessListener
            if (location.accuracy in 0.1f..50f && System.currentTimeMillis() - location.time <= 120_000L) {
                lastLocation = Location(location)
                routePoints.add(Location(location))
                publish(location.speedOrZero())
            }
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
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(2f)
            .setWaitForAccurateLocation(false)
            .build()
        client.requestLocationUpdates(request, locationCallback, context.mainLooper)
    }

    fun stop() {
        active = false
        paused = false
        client.removeLocationUpdates(locationCallback)
        callback = null
        lastLocation = null
    }

    private fun acceptLocation(location: Location) {
        if (location.accuracy <= 0f || location.accuracy > 50f) return
        val previous = lastLocation
        if (previous != null) {
            val dt = ((location.time - previous.time).coerceAtLeast(500L)) / 1000f
            val segment = previous.distanceTo(location)
            val speed = location.speedOrZero()
            val maxSpeed = if (speed > 0f) max(12f, speed + 6f) else 12f
            val maxSegment = (maxSpeed * dt + max(previous.accuracy, location.accuracy)).coerceAtLeast(12f)
            if (segment > maxSegment) return
            if (segment >= 1.5f) totalMeters += segment
        }
        lastLocation = Location(location)
        routePoints.add(Location(location))
        if (routePoints.size > 1500) routePoints.removeAt(0)
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
