package com.racetrack.app

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// ArcGIS REST uses level/row/column (z/y/x), while osmdroid's XYTileSource
// normally generates z/x/y. Use an explicit tile source so requests are correct.
private val WORLD_SATELLITE = object : OnlineTileSourceBase(
    "World Satellite ArcGIS Fixed v4",
    0,
    17,
    256,
    ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Sources: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
) {
    override fun getTileURLString(tileIndex: Long): String {
        val z = MapTileIndex.getZoom(tileIndex)
        val x = MapTileIndex.getX(tileIndex)
        val y = MapTileIndex.getY(tileIndex)
        return getBaseUrl() + z + "/" + y + "/" + x + ".jpg"
    }
}

private fun configurePersistentMapCache(context: Context) {
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    val configuration = Configuration.getInstance()
    configuration.load(appContext, prefs)
    configuration.userAgentValue = appContext.packageName

    // Keep a larger on-device tile cache so already downloaded imagery is reused
    // on the next workout instead of being fetched again.
    configuration.setCacheMapTileCount(32)
    configuration.setTileDownloadThreads(4)
    configuration.setTileFileSystemThreads(8)
    configuration.setTileDownloadMaxQueueSize(80)
    configuration.setTileFileSystemMaxQueueSize(80)
    configuration.setTileFileSystemCacheMaxBytes(1024L * 1024L * 1024L)
    configuration.setTileFileSystemCacheTrimBytes(900L * 1024L * 1024L)
    configuration.setMapViewHardwareAccelerated(true)
    configuration.save(appContext, prefs)
}

@Composable
fun NativeRouteMap(route: List<Location>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredOnLocation by remember { mutableStateOf(false) }
    var renderedRouteKey by remember { mutableStateOf<String?>(null) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                configurePersistentMapCache(context)

                MapView(context).apply {
                    setTileSource(WORLD_SATELLITE)
                    setUseDataConnection(true)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    isTilesScaledToDpi = true
                    minZoomLevel = 3.0
                    maxZoomLevel = 17.0
                    controller.setZoom(15.0)
                    onResume()
                    mapView = this
                }
            },
            update = { map ->
                // Never preload or display a previous workout route here.
                // This map belongs to the CURRENT workout only. When the route
                // is empty, only the satellite map is shown until current GPS
                // points arrive.
                val displayRoute = route
                val currentKey = if (displayRoute.isEmpty()) "empty" else {
                    val first = displayRoute.first()
                    val last = displayRoute.last()
                    "${displayRoute.size}:${first.latitude}:${first.longitude}:${last.latitude}:${last.longitude}"
                }

                if (currentKey != renderedRouteKey) {
                    map.overlays.removeAll { it is Polyline || it is Marker }

                    if (displayRoute.isNotEmpty()) {
                        val points = displayRoute.map { GeoPoint(it.latitude, it.longitude) }

                        map.overlays.add(Polyline(map).apply {
                            setPoints(points)
                            setColor(android.graphics.Color.rgb(0, 230, 118))
                            setWidth(9f)
                        })

                        // Only show the CURRENT GPS location marker.
                        val last = points.last()
                        map.overlays.add(Marker(map).apply {
                            position = last
                            icon = ContextCompat.getDrawable(context, R.drawable.current_location_marker)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Current location"
                            snippet = "GPS position"
                        })

                        if (!hasCenteredOnLocation) {
                            map.controller.setZoom(16.0)
                            map.controller.setCenter(points.last())
                            hasCenteredOnLocation = true
                        }
                    } else {
                        hasCenteredOnLocation = false
                    }

                    renderedRouteKey = currentKey
                    map.invalidate()
                }
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map?.onPause()
            mapView = null
        }
    }
}
