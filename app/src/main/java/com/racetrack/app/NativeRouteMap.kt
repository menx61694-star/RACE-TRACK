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
// generates z/x/y. Use an explicit tile source so the request order is correct.
// The source name is versioned to prevent old failed tiles from the previous
// implementation being reused from the osmdroid tile cache.
private val WORLD_SATELLITE = object : OnlineTileSourceBase(
    "World Satellite ArcGIS Fixed v2",
    0,
    19,
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

@Composable
fun NativeRouteMap(route: List<Location>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredOnLocation by remember { mutableStateOf(false) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                Configuration.getInstance().load(
                    context.applicationContext,
                    context.applicationContext.getSharedPreferences(
                        "osmdroid",
                        android.content.Context.MODE_PRIVATE
                    )
                )
                Configuration.getInstance().userAgentValue = context.packageName

                MapView(context).apply {
                    setTileSource(WORLD_SATELLITE)
                    setUseDataConnection(true)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    isTilesScaledToDpi = true
                    minZoomLevel = 3.0
                    maxZoomLevel = 19.0
                    controller.setZoom(15.0)
                    onResume()
                    mapView = this
                }
            },
            update = { map ->
                map.overlays.removeAll { it is Polyline || it is Marker }

                if (route.isNotEmpty()) {
                    val points = route.map { GeoPoint(it.latitude, it.longitude) }

                    map.overlays.add(Polyline(map).apply {
                        setPoints(points)
                        setColor(android.graphics.Color.rgb(0, 230, 118))
                        setWidth(9f)
                    })

                    val last = points.last()
                    map.overlays.add(Marker(map).apply {
                        position = last
                        icon = ContextCompat.getDrawable(context, R.drawable.current_location_marker)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Current location"
                        snippet = "GPS position"
                    })

                    if (!hasCenteredOnLocation) {
                        map.controller.setZoom(17.0)
                        map.controller.setCenter(last)
                        hasCenteredOnLocation = true
                    }
                } else {
                    hasCenteredOnLocation = false
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map?.onPause()
            mapView = null
        }
    }
}
