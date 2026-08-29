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
        val accuracyMeters: Float = 0f,
        val route: List<Location> = emptyList()
    )

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastLocation: Location? = null
    private var active = false
    private var paused = false
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
        paused = false
        lastLocation = null
        totalMeters = 0f
        elapsedSeconds = 0L
        routePoints.clear()
        snapshot = Snapshot()
        requestUpdates()

        val seeded = runCatching {
            when {
                isGpsEnabled() -> manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                isNetworkEnabled() -> manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                else -> null
            }
        }.getOrNull()
        if (seeded != null && seeded.accuracy in 0.1f..35f && System.currentTimeMillis() - seeded.time <= 120_000L) {
            lastLocation = Location(seeded)
            routePoints.add(Location(seeded))
            publish(seeded.speedOrZero())
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (!active || paused) return
        when {
            isGpsEnabled() -> manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this)
            isNetworkEnabled() -> manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 3f, this)
        }
    }

    private fun isGpsEnabled(): Boolean = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    private fun isNetworkEnabled(): Boolean = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

    fun addElapsedSeconds(seconds: Long) {
        if (!active || paused) return
        elapsedSeconds = max(0L, seconds)
        publish(lastLocation?.speedOrZero() ?: 0f)
    }

    @SuppressLint("MissingPermission")
    fun pause() {
        if (!active || paused) return
        paused = true
        manager.removeUpdates(this)
        lastLocation = null
    }

    @SuppressLint("MissingPermission")
    fun resume() {
        if (!active || !paused) return
        paused = false
        requestUpdates()
    }

    fun stop() {
        active = false
        paused = false
        manager.removeUpdates(this)
        callback = null
        lastLocation = null
    }

    override fun onLocationChanged(location: Location) {
        if (!active || paused) return
        if (location.accuracy <= 0f || location.accuracy > 35f) return

        val previous = lastLocation
        if (previous != null) {
            val elapsedSecondsBetweenFixes = ((location.time - previous.time).coerceAtLeast(1000L)) / 1000f
            val segment = previous.distanceTo(location)
            val reportedSpeed = location.speedOrZero()
            val maxAllowedSpeed = if (reportedSpeed > 0f) max(15f, reportedSpeed + 8f) else 15f
            val maxReasonableSegment = (maxAllowedSpeed * elapsedSecondsBetweenFixes + max(previous.accuracy, location.accuracy)).coerceAtLeast(10f)
            if (segment > maxReasonableSegment) return
            if (segment >= 1.5f) totalMeters += segment
        }

        lastLocation = Location(location)
        routePoints.add(Location(location))
        if (routePoints.size > 1000) routePoints.removeAt(0)
        publish(location.speedOrZero())
    }

    private fun publish(speed: Float) {
        val average = if (elapsedSeconds > 0) totalMeters / elapsedSeconds else 0f
        val accuracy = lastLocation?.accuracy ?: 0f
        snapshot = Snapshot(totalMeters, speed, average, accuracy, routePoints.toList())
        callback?.invoke(snapshot)
    }

    private fun Location.speedOrZero(): Float = if (hasSpeed()) speed.coerceAtLeast(0f) else 0f

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
}
