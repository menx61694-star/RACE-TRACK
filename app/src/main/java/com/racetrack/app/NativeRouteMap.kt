package com.racetrack.app

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maptiler.maptilersdk.MTConfig
import com.maptiler.maptilersdk.annotations.MTMarker
import com.maptiler.maptilersdk.map.LngLat
import com.maptiler.maptilersdk.map.MTMapOptions
import com.maptiler.maptilersdk.map.MTMapView
import com.maptiler.maptilersdk.map.MTMapViewController
import com.maptiler.maptilersdk.map.MTMapViewDelegate
import com.maptiler.maptilersdk.map.style.MTMapReferenceStyle
import com.maptiler.maptilersdk.helpers.MTPolylineLayerOptions
import com.maptiler.maptilersdk.events.MTEvent
import com.maptiler.maptilersdk.map.types.MTData
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

private const val CUSTOM_MAP_STYLE_ID = "01a067ec-2c74-7cbd-b963-26d058d9f73d"
private const val ROUTE_SOURCE_ID = "race-track-current-route-source"
private const val ROUTE_LAYER_ID = "race-track-current-route-layer"

private fun routeGeoJson(route: List<Location>): String {
    val coordinates = JSONArray()
    route.forEach { location ->
        // GeoJSON uses [longitude, latitude]. Keep every filtered GPS point;
        // there is deliberately no road matching or synthetic geometry here.
        coordinates.put(JSONArray().put(location.longitude).put(location.latitude))
    }

    val geometry = JSONObject()
        .put("type", "LineString")
        .put("coordinates", coordinates)

    val feature = JSONObject()
        .put("type", "Feature")
        .put("properties", JSONObject())
        .put("geometry", geometry)

    return JSONObject()
        .put("type", "FeatureCollection")
        .put("features", JSONArray().put(feature))
        .toString()
}

@Composable
fun NativeRouteMap(route: List<Location>, modifier: Modifier = Modifier) {
    val apiKey = BuildConfig.MAPTILER_API_KEY
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { MTMapViewController(context) }
    var mapReady by remember { mutableStateOf(false) }
    var hasCenteredOnLocation by remember { mutableStateOf(false) }
    var renderedRouteKey by remember { mutableStateOf<String?>(null) }
    var currentMarker by remember { mutableStateOf<MTMarker?>(null) }

    if (apiKey.isBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "MapTiler API key is not configured",
                color = Color.White,
                fontSize = 12.sp,
            )
        }
        return
    }

    // MapTiler requires the API key to be set before the first map is created.
    MTConfig.apiKey = apiKey

    LaunchedEffect(controller) {
        controller.delegate = object : MTMapViewDelegate {
            override fun onMapViewInitialized() {
                mapReady = true
            }

            override fun onEventTriggered(event: MTEvent, data: MTData?) = Unit
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.delegate = null
            currentMarker?.let { marker ->
                controller.style?.removeMarker(marker)
            }
            controller.destroy()
        }
    }

    Box(modifier) {
        MTMapView(
            referenceStyle = MTMapReferenceStyle.CUSTOM(
                URL("https://api.maptiler.com/maps/$CUSTOM_MAP_STYLE_ID/style.json?key=$apiKey")
            ),
            options = MTMapOptions(zoom = 15.0),
            controller = controller,
            modifier = Modifier.fillMaxSize(),
        )

        LaunchedEffect(mapReady, route) {
            if (!mapReady) return@LaunchedEffect
            val style = controller.style ?: return@LaunchedEffect

            // Avoid rebuilding the route unless the actual route snapshot changed.
            val routeKey = if (route.isEmpty()) "empty" else {
                val first = route.first()
                val last = route.last()
                "${route.size}:${first.time}:${first.latitude}:${first.longitude}:${last.time}:${last.latitude}:${last.longitude}"
            }
            if (routeKey == renderedRouteKey) return@LaunchedEffect

            // The helper creates a GeoJSON source + line layer from our exact
            // filtered GPS coordinates. No snapping, map matching or polygon
            // generation is performed.
            runCatching {
                style.removeLayerById(ROUTE_LAYER_ID)
                style.removeSourceById(ROUTE_SOURCE_ID)
            }

            currentMarker?.let { style.removeMarker(it) }
            currentMarker = null

            if (route.size >= 2) {
                val helper = style.polylineHelper()
                helper.addPolyline(
                    MTPolylineLayerOptions(
                        data = routeGeoJson(route),
                        layerId = ROUTE_LAYER_ID,
                        sourceId = ROUTE_SOURCE_ID,
                        lineColor = "#00E676",
                        lineWidth = 5.0,
                        lineOpacity = 1.0,
                    )
                )
            }

            if (route.isNotEmpty()) {
                val last = route.last()
                val lastLngLat = LngLat(last.longitude, last.latitude)
                val marker = MTMarker(lastLngLat, android.graphics.Color.rgb(30, 136, 229))
                style.addMarker(marker)
                currentMarker = marker

                if (!hasCenteredOnLocation) {
                    controller.setZoom(16.0)
                    controller.setCenter(lastLngLat)
                    hasCenteredOnLocation = true
                }
            } else {
                hasCenteredOnLocation = false
            }

            renderedRouteKey = routeKey
        }

        Text(
            "© MapTiler • OpenStreetMap contributors",
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 8.sp,
        )
    }
}
