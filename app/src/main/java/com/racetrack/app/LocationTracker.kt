package com.racetrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlin.math.max

class LocationTracker(private val context: Context) : LocationListener {
    data class Snapshot(
        val distanceMeters: Float = 0f,
        val currentSpeedMps: Float = 0f,
        val averageSpeedMps: Float = 0f,
        val route: List<Location> = emptyList()
    )

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastLocation: Location? = null
    private var active = false
    private var totalMeters = 0f
    private var elapsedSeconds = 0L
    private var callback: ((Snapshot) -> Unit)? = null
    private val routePoints = mutableListOf<Location>()

    var snapshot: Snapshot = Snapshot()
        private set

    @SuppressLint("MissingPermission")
    fun start(onUpdate: (Snapshot) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return

        callback = onUpdate
        active = true
        lastLocation = null
        totalMeters = 0f
        elapsedSeconds = 0L
        routePoints.clear()
        snapshot = Snapshot()

        // Prefer GPS for workout tracking. Network is only a fallback when GPS is unavailable.
        val gpsEnabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val networkEnabled = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        if (gpsEnabled) {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this)
        } else if (networkEnabled) {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 3f, this)
        }
    }

    fun addElapsedSeconds(seconds: Long) {
        if (!active) return
        elapsedSeconds = max(0L, seconds)
        publish(lastLocation?.let { if (it.hasSpeed()) it.speed else 0f } ?: 0f)
    }

    fun stop() {
        active = false
        manager.removeUpdates(this)
        callback = null
    }

    override fun onLocationChanged(location: Location) {
        if (!active) return

        // Reject weak fixes. Keeping noisy GPS points makes both distance and the drawn route worse.
        if (location.accuracy > 40f) return

        val previous = lastLocation
        if (previous != null) {
            val elapsedMs = (location.time - previous.time).coerceAtLeast(1L)
            val elapsedSecondsBetweenFixes = elapsedMs / 1000f
            val segment = previous.distanceTo(location)

            // Reject impossible jumps caused by GPS/network glitches.
            val maxReasonableSegment = (14f * elapsedSecondsBetweenFixes + max(previous.accuracy, location.accuracy) * 0.75f).coerceAtLeast(8f)
            if (segment > maxReasonableSegment) return

            // Ignore tiny GPS jitter instead of accumulating it as walking distance.
            if (segment >= 2f) totalMeters += segment
        }

        lastLocation = Location(location)
        routePoints.add(Location(location))
        publish(if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else 0f)
    }

    private fun publish(speed: Float) {
        val average = if (elapsedSeconds > 0) totalMeters / elapsedSeconds else 0f
        snapshot = Snapshot(totalMeters, speed, average, routePoints.toList())
        callback?.invoke(snapshot)
    }

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
}
