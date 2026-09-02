package com.racetrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WorkoutHistoryScreen(workoutStore: WorkoutStore, onReplay: (WorkoutStore.Session) -> Unit) {
    val sessions = workoutStore.sessions().reversed()
    val totalSteps = sessions.sumOf { it.steps }
    val totalDistance = sessions.sumOf { it.distanceMeters.toDouble() }.toFloat()

    Column(Modifier.fillMaxSize().background(Charcoal).padding(horizontal = 20.dp)) {
        Column(Modifier.padding(top = 18.dp, bottom = 14.dp)) {
            Text("Workout History", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text(if (sessions.isEmpty()) "Your completed walks and runs will appear here." else "${sessions.size} workouts • ${totalSteps} total steps", color = Muted, fontSize = 13.sp)
        }

        if (sessions.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Card)) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DirectionsWalk, null, tint = Green, modifier = Modifier.padding(bottom = 8.dp))
                    Text("No workouts yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Finish your first live walk or run and it will be saved on this device.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        } else {
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    HistorySummary("Workouts", sessions.size.toString())
                    HistorySummary("Distance", "%.1f km".format(totalDistance / 1000f))
                    HistorySummary("Steps", totalSteps.toString())
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sessions) { session ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(if (session.activity.equals("Run", true)) Icons.Default.DirectionsRun else Icons.Default.DirectionsWalk, null, tint = Green)
                                    Text(session.activity, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(workoutStore.formatDate(session.date), color = Muted, fontSize = 12.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                HistoryStat("Steps", session.steps.toString())
                                HistoryStat("Distance", "%.2f km".format(session.distanceMeters / 1000f))
                                HistoryStat("Time", formatWorkoutDuration(session.durationSeconds))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${session.calories.toInt()} kcal  •  ${session.laps.size} laps  •  ${session.route.size} GPS points", color = Muted, fontSize = 11.sp)
                                if (session.route.isNotEmpty()) {
                                    Button(onClick = { onReplay(session) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Text("Replay", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummary(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp); Text(label, color = Muted, fontSize = 10.sp) } }

@Composable
private fun HistoryStat(label: String, value: String) { Column { Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(label, color = Muted, fontSize = 10.sp) } }
