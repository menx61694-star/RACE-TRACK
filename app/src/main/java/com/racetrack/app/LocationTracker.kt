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

        // GPS is preferred for fitness tracking. Requesting GPS + network together can
        // double-count segments when the providers report the same movement differently.
        val gpsEnabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val provider = if (gpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        manager.requestLocationUpdates(provider, 1000L, 3f, this)
    }

    fun addElapsedSeconds(seconds: Long) {
        if (!active) return
        elapsedSeconds = max(0L, seconds)
        publish(lastLocation?.speed?.coerceAtLeast(0f) ?: 0f)
    }

    fun stop() {
        active = false
        manager.removeUpdates(this)
        callback = null
    }

    override fun onLocationChanged(location: Location) {
        if (!active) return
        if (location.accuracy > 35f) return
        if (location.isFromMockProvider) return

        val previous = lastLocation
        if (previous != null) {
            val segment = previous.distanceTo(location)
            val dtSeconds = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000f
            if (dtSeconds <= 0f || dtSeconds > 15f) {
                lastLocation = location
                return
            }

            // Reject GPS jitter and impossible jumps. The threshold scales with the
            // reported accuracy, while the upper bound prevents teleport-like spikes.
            val noiseThreshold = max(3f, (previous.accuracy + location.accuracy) * 0.25f)
            val maxReasonable = max(35f, dtSeconds * 8f)
            if (segment < noiseThreshold || segment > maxReasonable) return
            totalMeters += segment
        }

        lastLocation = Location(location)
        routePoints.add(Location(location))
        val speed = if (location.hasSpeed() && location.speed >= 0f) {
            location.speed
        } else if (previous != null) {
            val dt = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000f
            if (dt > 0f) previous.distanceTo(location) / dt else 0f
        } else 0f
        publish(speed.coerceAtLeast(0f))
    }

    private fun publish(speed: Float) {
        val average = if (elapsedSeconds > 0) totalMeters / elapsedSeconds else 0f
        snapshot = Snapshot(totalMeters, speed, average, routePoints.toList())
        callback?.invoke(snapshot)
    }

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
}
