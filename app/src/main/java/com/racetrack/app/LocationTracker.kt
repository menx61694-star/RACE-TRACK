package com.racetrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Foreground location tracker. Distance is accumulated only from usable GPS fixes. */
class LocationTracker(private val context: Context) : LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var distanceMeters by mutableDoubleStateOf(0.0)
        private set
    var speedMps by mutableDoubleStateOf(0.0)
        private set
    var lastLocation by mutableStateOf<Location?>(null)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var gpsAvailable by mutableStateOf(false)
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) return
        stop()
        distanceMeters = 0.0
        speedMps = 0.0
        lastLocation = null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var requested = false
        providers.forEach { provider ->
            if (manager.isProviderEnabled(provider)) {
                manager.requestLocationUpdates(provider, 1000L, 2f, this)
                requested = true
            }
        }
        gpsAvailable = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        isRunning = requested
    }

    fun stop() {
        manager.removeUpdates(this)
        isRunning = false
    }

    override fun onLocationChanged(location: Location) {
        val previous = lastLocation
        if (location.accuracy <= 30f) {
            if (previous != null) {
                val delta = previous.distanceTo(location)
                if (delta >= 1f && delta <= 100f) distanceMeters += delta
            }
            speedMps = location.speed.coerceAtLeast(0f).toDouble()
            lastLocation = location
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) gpsAvailable = true
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) gpsAvailable = false
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
