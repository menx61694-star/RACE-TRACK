package com.racetrack.app

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker

@Composable
fun NativeRouteMap(route: List<Location>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Polyline || it is Marker }
            if (route.isNotEmpty()) {
                val points = route.map { GeoPoint(it.latitude, it.longitude) }
                val line = Polyline(map).apply {
                    setPoints(points)
                    color = android.graphics.Color.rgb(0, 230, 118)
                    width = 10f
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
    )
}
