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

        val gpsEnabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val networkEnabled = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        if (gpsEnabled) {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this)
        } else if (networkEnabled) {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 3f, this)
        }

        // Seed the map quickly from a recent Android location fix, if one exists.
        val seeded = runCatching {
            if (gpsEnabled) manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            else if (networkEnabled) manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            else null
        }.getOrNull()
        if (seeded != null && seeded.accuracy <= 35f && System.currentTimeMillis() - seeded.time <= 120_000L) {
            lastLocation = Location(seeded)
            routePoints.add(Location(seeded))
            publish(if (seeded.hasSpeed()) seeded.speed.coerceAtLeast(0f) else 0f)
        }
    }

    fun addElapsedSeconds(seconds: Long) {
        if (!active) return
        elapsedSeconds = max(0L, seconds)
        publish(lastLocation?.let { if (it.hasSpeed()) it.speed.coerceAtLeast(0f) else 0f } ?: 0f)
    }

    fun stop() {
        active = false
        manager.removeUpdates(this)
        callback = null
    }

    override fun onLocationChanged(location: Location) {
        if (!active) return
        if (location.accuracy <= 0f || location.accuracy > 35f) return

        val previous = lastLocation
        if (previous != null) {
            val elapsedSecondsBetweenFixes = ((location.time - previous.time).coerceAtLeast(1000L)) / 1000f
            val segment = previous.distanceTo(location)
            val reportedSpeed = if (location.hasSpeed()) location.speed else 0f
            val maxAllowedSpeed = if (reportedSpeed > 0f) max(15f, reportedSpeed + 8f) else 15f
            val maxReasonableSegment = (maxAllowedSpeed * elapsedSecondsBetweenFixes + max(previous.accuracy, location.accuracy)).coerceAtLeast(10f)

            // Reject GPS jumps that imply an impossible workout speed.
            if (segment > maxReasonableSegment) return

            // Do not turn small GPS jitter into walking distance.
            if (segment >= 1.5f) totalMeters += segment
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
