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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private const val ROUTE_SOURCE_ID = "race-track-current-route-source"
private const val ROUTE_LAYER_ID = "race-track-current-route-layer"
private const val ROUTE_REDRAW_EVERY_POINTS = 3

private fun routeGeoJson(route: List<Location>): String {
    val coordinates = JSONArray()
    route.forEach { location ->
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
fun NativeRouteMap(
    route: List<Location>,
    modifier: Modifier = Modifier,
    onMapReady: () -> Unit = {},
) {
    val apiKey = BuildConfig.MAPTILER_API_KEY
    val context = androidx.compose.ui.platform.LocalContext.current

    if (apiKey.isBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("MapTiler API key is not configured", color = Color.White, fontSize = 12.sp)
        }
        return
    }

    // MapTiler requires the key before the first MTMapView is created.
    MTConfig.apiKey = apiKey

    var mapReady by remember { mutableStateOf(false) }
    var hasCenteredOnLocation by remember { mutableStateOf(false) }
    var lastRenderedRouteSize by remember { mutableStateOf(-1) }
    var currentMarker by remember { mutableStateOf<MTMarker?>(null) }

    // Install the delegate while the controller is created. Installing it from
    // a LaunchedEffect can race MTMapView initialization on fast devices.
    val controller = remember {
        MTMapViewController(context).apply {
            delegate = object : MTMapViewDelegate {
                override fun onMapViewInitialized() {
                    mapReady = true
                    onMapReady()
                }

                override fun onEventTriggered(event: MTEvent, data: MTData?) = Unit
            }
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.delegate = null
            currentMarker?.let { marker -> controller.style?.removeMarker(marker) }
            controller.destroy()
        }
    }

    Box(modifier) {
        MTMapView(
            referenceStyle = MTMapReferenceStyle.SATELLITE,
            options = MTMapOptions(
                zoom = 15.0,
                maptilerLogoIsVisible = true,
            ),
            controller = controller,
            modifier = Modifier.fillMaxSize(),
        )

        LaunchedEffect(mapReady, route) {
            if (!mapReady) return@LaunchedEffect
            val style = controller.style ?: return@LaunchedEffect

            if (route.isEmpty()) {
                style.removeLayerById(ROUTE_LAYER_ID)
                style.removeSourceById(ROUTE_SOURCE_ID)
                currentMarker?.let { style.removeMarker(it) }
                currentMarker = null
                hasCenteredOnLocation = false
                lastRenderedRouteSize = 0
                return@LaunchedEffect
            }

            val last = route.last()
            val lastLngLat = LngLat(last.longitude, last.latitude)
            val marker = currentMarker
            if (marker == null) {
                val newMarker = MTMarker(lastLngLat, android.graphics.Color.rgb(30, 136, 229))
                style.addMarker(newMarker)
                currentMarker = newMarker
            } else {
                marker.setCoordinates(lastLngLat, controller)
            }

            if (!hasCenteredOnLocation) {
                controller.setZoom(16.0)
                controller.setCenter(lastLngLat)
                hasCenteredOnLocation = true
            }

            // Do not tear down and recreate the route layer on every 1 Hz GPS
            // callback. That was causing visible map flicker and tile/style
            // work while the user was moving. Redraw in small batches instead.
            val shouldRenderRoute = route.size >= 2 &&
                (lastRenderedRouteSize < 0 ||
                    route.size - lastRenderedRouteSize >= ROUTE_REDRAW_EVERY_POINTS)

            if (shouldRenderRoute) {
                runCatching {
                    style.removeLayerById(ROUTE_LAYER_ID)
                    style.removeSourceById(ROUTE_SOURCE_ID)
                }

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
                lastRenderedRouteSize = route.size
            }
        }

        Text(
            "© MapTiler • OpenStreetMap contributors",
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 8.sp,
        )
    }
}
