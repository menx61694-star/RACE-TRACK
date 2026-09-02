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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WorkoutHistoryScreen(
    workoutStore: WorkoutStore,
    onReplay: (WorkoutStore.Session) -> Unit
) {
    val sessions = workoutStore.sessions().reversed()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Workout History", color = Color.White, fontSize = 26.sp)

        if (sessions.isEmpty()) {
            Text("No workouts saved yet.", color = Muted, fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sessions) { session ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Card)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(session.activity, color = Green, fontSize = 18.sp)
                            Text(workoutStore.formatDate(session.date), color = Muted, fontSize = 12.sp)
                        }
                        Text("${session.steps} steps", color = Color.White, fontSize = 14.sp)
                        Text("%.2f km  •  %d cal".format(session.distanceMeters / 1000f, session.calories.toInt()), color = Muted, fontSize = 13.sp)
                        Text("GPS points: ${session.route.size}", color = Muted, fontSize = 12.sp)
                        if (session.route.isNotEmpty()) {
                            Button(onClick = { onReplay(session) }, modifier = Modifier.fillMaxWidth()) {
                                Text("▶  Replay route")
                            }
                        }
                    }
                }
            }
        }
    }
}
