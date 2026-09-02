package com.racetrack.app

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * First experimental version of the Strava-style activity replay.
 * It replays the real GPS route on the existing satellite map instead of
 * generating a fake route or using the step count.
 */
@Composable
fun AnimatedRoutePostScreen(
    route: List<Location>,
    distanceMeters: Float,
    durationSeconds: Long,
    activity: String,
    onDone: () -> Unit
) {
    var frame by remember(route) { mutableIntStateOf(if (route.isEmpty()) 0 else 1) }
    var playing by remember(route) { mutableStateOf(true) }

    LaunchedEffect(route, playing) {
        while (playing && route.isNotEmpty() && frame < route.size) {
            delay(90L)
            frame++
        }
        if (frame >= route.size) playing = false
    }

    val visibleRoute = if (route.isEmpty()) emptyList() else route.take(frame.coerceAtLeast(1))
    val progress = if (route.size <= 1) 1f else ((frame - 1).toFloat() / (route.size - 1)).coerceIn(0f, 1f)
    val shownDistance = distanceMeters * progress

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NativeRouteMap(visibleRoute, Modifier.fillMaxSize())

            Column(
                Modifier.align(Alignment.TopStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("RACE-TRACK", color = Color.White, fontSize = 18.sp)
                Text("${activity.uppercase()} REPLAY", color = Green, fontSize = 12.sp)
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("%.2f km".format(shownDistance / 1000f), color = Color.White, fontSize = 30.sp)
                Text("Route replay", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .background(Card, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("%.2f km".format(distanceMeters / 1000f), color = Color.White, fontSize = 20.sp)
                    Text("Distance", color = Muted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatWorkoutDuration(durationSeconds), color = Color.White, fontSize = 20.sp)
                    Text("Time", color = Muted, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = {
                    frame = 1
                    playing = true
                }) {
                    Icon(Icons.Default.Replay, contentDescription = "Replay", tint = Color.White)
                }
                IconButton(onClick = { playing = !playing }) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = Green
                    )
                }
            }

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Done")
            }
        }
    }
}
