package com.racetrack.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(steps: Int, onStart: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Today's movement", color = Color.White, fontWeight = FontWeight.Black, fontSize = 27.sp)
                Text("Real steps from your phone sensor", color = Muted, fontSize = 13.sp)
            }
        }
        item { Phase1StepRing(steps.coerceAtLeast(0), 10000) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Phase1MetricCard("👟", "Steps", "%d steps".format(steps), Modifier.weight(1f))
                Phase1MetricCard("🔥", "Calories", "Not calculated", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Phase1MetricCard("📏", "Distance", "Not calculated", Modifier.weight(1f))
                Phase1MetricCard("⏱", "Active Time", "Not calculated", Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Phase 1 tracking", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Step counting is active in the background. GPS distance, pace and calorie estimation will be added in the next tracking phase.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
            }
        }
        item {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)
            ) {
                Icon(Icons.Default.LocationOn, null)
                Spacer(Modifier.size(8.dp))
                Text("Start Step Session", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Phase1StepRing(steps: Int, goal: Int) {
    val progress = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color(0xFF303030), -90f, 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                    if (progress > 0f) drawArc(Brush.sweepGradient(listOf(Green, Cyan, Green)), -90f, 360f * progress, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(steps.toString(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Text("/ $goal steps", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("${(progress * 100).toInt()}%", color = Green, fontWeight = FontWeight.Bold)
                }
            }
            Text("Today's movement", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Phase1MetricCard(icon: String, title: String, value: String, modifier: Modifier) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 19.sp)
                Spacer(Modifier.size(7.dp))
                Text(title, color = Muted, fontSize = 12.sp)
            }
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
