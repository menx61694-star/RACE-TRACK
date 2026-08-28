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

class LocationTracker(context: Context) : LocationListener {
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

    var snapshot: Snapshot = Snapshot()
        private set

    @SuppressLint("MissingPermission")
    fun start(onUpdate: (Snapshot) -> Unit) {
        if (ContextCompat.checkSelfPermission(context(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        callback = onUpdate
        active = true
        lastLocation = null
        totalMeters = 0f
        elapsedSeconds = 0L
        snapshot = Snapshot()
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        providers.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }.forEach { provider ->
            manager.requestLocationUpdates(provider, 1000L, 2f, this)
        }
    }

    fun addElapsedSeconds(seconds: Long) {
        if (!active) return
        elapsedSeconds = max(0L, seconds)
        publish(lastLocation?.speed ?: 0f)
    }

    fun stop() {
        active = false
        manager.removeUpdates(this)
        callback = null
    }

    override fun onLocationChanged(location: Location) {
        if (!active) return
        val previous = lastLocation
        if (previous != null) {
            val segment = previous.distanceTo(location)
            if (segment >= 1f && location.accuracy <= 50f) totalMeters += segment
        }
        lastLocation = location
        publish(location.speed.coerceAtLeast(0f))
    }

    private fun publish(speed: Float) {
        val average = if (elapsedSeconds > 0) totalMeters / elapsedSeconds else 0f
        snapshot = Snapshot(totalMeters, speed, average, buildList { lastLocation?.let { add(it) } })
        callback?.invoke(snapshot)
    }

    private fun context(): Context = manager.javaClass.let { // context is not exposed by LocationManager; permission is checked before construction by caller.
        throw IllegalStateException("LocationTracker.start must be called only after location permission is granted")
    }

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
}
