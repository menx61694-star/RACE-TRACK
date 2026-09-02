package com.racetrack.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun WorkoutHistoryScreen(workoutStore: WorkoutStore, onReplay: (WorkoutStore.Session) -> Unit) {
    val sessions = workoutStore.sessions().reversed()
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Workout History", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text("Every Walk / Run with its route and complete stats", color = Muted, fontSize = 13.sp)
        }
        if (sessions.isEmpty()) {
            item { Text("No workouts recorded yet.", color = Muted, modifier = Modifier.padding(top = 20.dp)) }
        } else {
            items(sessions.size) { index ->
                val s = sessions[index]
                val pace = if (s.distanceMeters > 1f) s.durationSeconds / (s.distanceMeters / 1000f) else 0f
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Card),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(s.activity, color = Green, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text(workoutStore.formatDate(s.date), color = Muted, fontSize = 12.sp)
                            }
                            Text(formatWorkoutDuration(s.durationSeconds), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            HistoryMetric("Distance", "%.2f km".format(s.distanceMeters / 1000f))
                            HistoryMetric("Pace", if (pace > 0f) "%d:%02d".format((pace / 60).toInt(), (pace % 60).roundToInt()) else "—")
                            HistoryMetric("Calories", "${s.calories.roundToInt()} kcal")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            HistoryMetric("Steps", s.steps.toString())
                            HistoryMetric("Laps", s.laps.size.toString())
                            HistoryMetric("Route", if (s.route.size > 1) "${s.route.size} GPS pts" else "No route")
                        }
                        if (s.laps.isNotEmpty()) {
                            Text(s.laps.joinToString("   ") { "L${it.number}: ${formatWorkoutDuration(it.elapsedSeconds)}" }, color = Green, fontSize = 12.sp)
                        }
                        if (s.route.size > 1) {
                            Button(
                                onClick = { onReplay(s) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)
                            ) { Text("▶  Replay route", fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String) {
    Column {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}
