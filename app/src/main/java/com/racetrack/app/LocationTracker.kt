package com.racetrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (active && !paused) acceptLocation(location, initial = lastLocation == null)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(onUpdate: (Snapshot) -> Unit, onStatus: (String) -> Unit = {}) {
        stop()
        callback = onUpdate
        statusCallback = onStatus
        snapshot = Snapshot()
        if (!hasLocationPermission()) {
            statusCallback?.invoke("Location permission is not granted")
            callback?.invoke(snapshot)
            return
        }
        if (!isLocationEnabled()) {
            statusCallback?.invoke("Location services are OFF")
            callback?.invoke(snapshot)
            return
        }
        active = true
        statusCallback?.invoke("Acquiring GPS fix…")
        startLocationSources()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationSources() {
        if (!active || paused || !hasLocationPermission()) return
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1f)
            .setWaitForAccurateLocation(false)
            .build()

        client.requestLocationUpdates(request, locationCallback, context.mainLooper)
            .addOnFailureListener { statusCallback?.invoke("Fused GPS unavailable; using device GPS…") }

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, gpsListener, context.mainLooper)
            } catch (_: SecurityException) {
                statusCallback?.invoke("GPS permission unavailable")
            }
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, gpsListener, context.mainLooper)
            } catch (_: SecurityException) { }
        }

        val lastGps = try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
        val lastNetwork = try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
        listOfNotNull(lastGps, lastNetwork)
            .filter { it.accuracy > 0f && System.currentTimeMillis() - it.time <= 60_000L }
            .minByOrNull { it.accuracy }
            ?.let { acceptLocation(it, initial = true) }

        statusCallback?.invoke(if (lastLocation != null) "GPS ± ${lastLocation!!.accuracy.roundToInt()} m" else "Searching for GPS signal…")
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
        statusCallback?.invoke("Retrying GPS fix…")
        startLocationSources()
    }

    private fun resetSession() {
        lastLocation = null
        totalMeters = 0f
        elapsedSeconds = 0L
        routePoints.clear()
        snapshot = Snapshot()
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
        publish(lastLocation?.speedOrZero() ?: 0f)
    }

    @SuppressLint("MissingPermission")
    fun pause() {
        if (!active || paused) return
        paused = true
        removeUpdates()
        lastLocation = null
    }

    @SuppressLint("MissingPermission")
    fun resume() {
        if (!active || !paused) return
        paused = false
        retry()
    }

    @SuppressLint("MissingPermission")
    private fun removeUpdates() {
        client.removeLocationUpdates(locationCallback)
        try { locationManager.removeUpdates(gpsListener) } catch (_: SecurityException) { }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        active = false
        paused = false
        removeUpdates()
        callback = null
        statusCallback = null
        lastLocation = null
    }

    private fun acceptLocation(location: Location, initial: Boolean) {
        val maxAccuracy = if (initial) 100f else 75f
        if (location.accuracy <= 0f || location.accuracy > maxAccuracy) {
            statusCallback?.invoke("GPS accuracy is low (${location.accuracy.roundToInt()} m)…")
            return
        }
        val previous = lastLocation
        if (previous != null) {
            val dt = ((location.time - previous.time).coerceAtLeast(500L)) / 1000f
            val segment = previous.distanceTo(location)
            val speed = location.speedOrZero()
            val maxSpeed = if (speed > 0.5f) max(10f, speed + 5f) else 10f
            val maxSegment = (maxSpeed * dt + max(previous.accuracy, location.accuracy)).coerceAtLeast(10f)
            if (segment > maxSegment) return
            if (segment >= 1.5f) totalMeters += segment
        }
        lastLocation = Location(location)
        routePoints.add(Location(location))
        if (routePoints.size > 4000) routePoints.removeAt(0)
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
