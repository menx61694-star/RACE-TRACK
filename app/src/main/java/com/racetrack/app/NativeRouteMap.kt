package com.racetrack.app

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    0,
    19,
    256,
    ".jpg",
    arrayOf("https://wi.maptiles.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile/"),
    "Sources: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
)

@Composable
fun NativeRouteMap(route: List<Location>, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                Configuration.getInstance().load(
                    context,
                    context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
                )
                Configuration.getInstance().userAgentValue = context.packageName

                MapView(context).apply {
                    setTileSource(WORLD_SATELLITE)
                    setUseDataConnection(true)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    isTilesScaledToDpi = true
                    controller.setZoom(16.0)
                    mapView = this
                    onResume()
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
        )

        Text(
            "Satellite imagery • Esri / data providers",
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 8.sp
        )
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val map = mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map?.onResume()
                Lifecycle.Event.ON_PAUSE -> map?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map?.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map?.onPause()
            map?.onDetach()
        }
    }
}
