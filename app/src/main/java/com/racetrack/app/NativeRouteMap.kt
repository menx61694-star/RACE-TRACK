package com.racetrack.app

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val WORLD_SATELLITE = XYTileSource(
    "World Satellite",
    1,
    19,
    256,
    ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Sources: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
)

@Composable
fun NativeRouteMap(route: List<Location>, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { context ->
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(context).apply {
                setTileSource(WORLD_SATELLITE)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                isTilesScaledToDpi = true
                controller.setZoom(16.0)
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Polyline || it is Marker }

            if (route.isNotEmpty()) {
                val points = route.map { GeoPoint(it.latitude, it.longitude) }
                val line = Polyline(map).apply {
                    setPoints(points)
                    setColor(android.graphics.Color.rgb(0, 230, 118))
                    setWidth(9f)
                }
                map.overlays.add(line)

                val last = points.last()
                val marker = Marker(map).apply {
                    position = last
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Current location"
                }
                map.overlays.add(marker)
                map.controller.animateTo(last)
                if (points.size == 1) map.controller.setZoom(17.0)
            }
            map.invalidate()
        }
    ).also { /* lifecycle is handled below through the same composition */ }

    DisposableEffect(lifecycleOwner) {
        var currentMap: MapView? = null
        val observer = LifecycleEventObserver { _, event ->
            // AndroidView owns the actual MapView; lifecycle events are forwarded by the
            // owner when the composable enters/leaves the foreground.
            if (event == Lifecycle.Event.ON_PAUSE) currentMap?.onPause()
            if (event == Lifecycle.Event.ON_RESUME) currentMap?.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentMap?.onDetach()
        }
    }
}
