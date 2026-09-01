package com.racetrack.app

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun Phase1LiveScreen(
    tracker: StepTracker,
    onFinish: (steps: Int, durationSeconds: Long) -> Unit
) {
    var running by remember { mutableStateOf(true) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    var accumulatedBeforePause by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        tracker.start()
        startedAt = SystemClock.elapsedRealtime()
        while (true) {
            if (running) {
                elapsed = accumulatedBeforePause + (SystemClock.elapsedRealtime() - startedAt) / 1000L
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose { tracker.stop() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Step Session", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Phase 1 • Real phone step sensor", color = Muted, fontSize = 13.sp)

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Card)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(formatDurationPhase1(elapsed), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(28.dp))
                Text(tracker.steps.toString(), color = Green, fontSize = 52.sp, fontWeight = FontWeight.Black)
                Text("session steps", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(22.dp))
                Text(
                    if (tracker.isAvailable) "Sensor active" else "Step counter sensor unavailable",
                    color = if (tracker.isAvailable) Green else Coral,
                    fontWeight = FontWeight.Bold
                )
                if (!tracker.isAvailable) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This phone does not expose Android's hardware step-counter sensor. No fake step value will be shown.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    if (running) {
                        accumulatedBeforePause = elapsed
                        pausedAt = SystemClock.elapsedRealtime()
                        tracker.pause()
                        running = false
                    } else {
                        startedAt = SystemClock.elapsedRealtime()
                        tracker.resume()
                        running = true
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = CircleShape
            ) {
                Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Spacer(Modifier.padding(4.dp))
                Text(if (running) "Pause" else "Resume")
            }
            Button(
                onClick = { onFinish(tracker.steps, elapsed) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Coral)
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.padding(4.dp))
                Text("Finish")
            }
        }
    }
}

private fun formatDurationPhase1(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
