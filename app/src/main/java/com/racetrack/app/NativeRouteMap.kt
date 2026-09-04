package com.racetrack.app

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Google Maps renderer for the current workout.
 *
 * The route is the exact trajectory supplied by LocationTracker. There is no
 * road snapping, map matching, artificial geometry, or heavy route smoothing.
 */
@Composable
fun NativeRouteMap(route: List<Location>, modifier: Modifier = Modifier) {
    val cameraPositionState = rememberCameraPositionState()
    var hasCenteredOnLocation by remember { mutableStateOf(false) }

    val points = remember(route) {
        route.map { LatLng(it.latitude, it.longitude) }
    }
    val lastPoint = points.lastOrNull()
    val markerState = remember { MarkerState(position = LatLng(0.0, 0.0)) }

    LaunchedEffect(lastPoint) {
        if (lastPoint != null) {
            markerState.position = lastPoint
            if (!hasCenteredOnLocation) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(lastPoint, 16f))
                hasCenteredOnLocation = true
            }
        } else {
            hasCenteredOnLocation = false
        }
    }

    Box(modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.SATELLITE),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
            ),
        ) {
            if (points.size >= 2) {
                Polyline(
                    points = points,
                    color = Color(0xFF00E676),
                    width = 5f,
                    geodesic = false,
                )
            }

            if (lastPoint != null) {
                Marker(
                    state = markerState,
                    title = "Current location",
                )
            }
        }

        Text(
            "© Google Maps",
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 8.sp,
        )
    }
}
