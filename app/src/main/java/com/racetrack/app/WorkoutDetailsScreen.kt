package com.racetrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun WorkoutDetailsScreen(
    session: WorkoutStore.Session,
    onReplay: () -> Unit,
    onBack: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val paceSeconds = if (session.distanceMeters > 1f) {
        (session.durationSeconds.toDouble() / session.distanceMeters.toDouble() * 1000.0).roundToInt()
    } else 0

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${session.activity} Details", color = Color.White, fontSize = 25.sp)
            TextButton(onClick = onBack) { Text("Back", color = Green) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            DetailsTab("Track", tab == 0) { tab = 0 }
            DetailsTab("Chart", tab == 1) { tab = 1 }
        }

        if (tab == 0) {
            TrackDetails(session, paceSeconds, onReplay)
        } else {
            ChartDetails(session, paceSeconds)
        }
    }
}

@Composable
private fun TrackDetails(session: WorkoutStore.Session, paceSeconds: Int, onReplay: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (session.route.isNotEmpty()) {
                NativeRouteMap(session.route, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No GPS route recorded for this workout", color = Muted)
                }
            }

            Card(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Card.copy(alpha = 0.94f))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(workoutDate(session.date), color = Muted, fontSize = 11.sp)
                    Text("${session.activity} • ${formatDistance(session.distanceMeters)}", color = Color.White, fontSize = 20.sp)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Card, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailMetric("Distance", formatDistance(session.distanceMeters))
                DetailMetric("Time", formatDetailsDuration(session.durationSeconds))
                DetailMetric("Pace", formatPace(paceSeconds))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailMetric("Calories", "${session.calories.roundToInt()} kcal")
                DetailMetric("Steps", session.steps.toString())
                DetailMetric("Laps", session.laps.size.toString())
            }
            Button(
                onClick = onReplay,
                enabled = session.route.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal),
                shape = RoundedCornerShape(14.dp)
            ) { Text("▶  Replay route") }
        }
    }
}

@Composable
private fun ChartDetails(session: WorkoutStore.Session, paceSeconds: Int) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pace", color = Color.White, fontSize = 21.sp)
                Text("Average pace  ${formatPace(paceSeconds)} /km", color = Muted, fontSize = 13.sp)
                Text("Distance  ${formatDistance(session.distanceMeters)}", color = Muted, fontSize = 13.sp)
            }
        }

        if (session.laps.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) {
                Text(
                    "No kilometre splits were recorded for this workout. Future runs can show each completed kilometre here.",
                    color = Muted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            Text("Kilometre splits", color = Color.White, fontSize = 18.sp)
            session.laps.forEach { lap ->
                val lapPace = if (lap.distanceMeters > 0f) (lap.elapsedSeconds.toDouble() / lap.distanceMeters * 1000.0).roundToInt() else 0
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Card)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${lap.number} km", color = Color.White, fontSize = 15.sp)
                        Text(formatPace(lapPace), color = Green, fontSize = 15.sp)
                        Text(formatDetailsDuration(lap.elapsedSeconds), color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("GPS points: ${session.route.size}", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RowScope.DetailsTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Text(label, color = if (selected) Green else Muted, fontSize = 15.sp)
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 17.sp)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

private fun formatDistance(meters: Float): String = "%.2f km".format(meters / 1000f)

private fun formatPace(seconds: Int): String {
    if (seconds <= 0) return "—"
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatDetailsDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val secs = safe % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}

private fun workoutDate(time: Long): String =
    java.text.SimpleDateFormat("dd MMM yyyy • HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))
