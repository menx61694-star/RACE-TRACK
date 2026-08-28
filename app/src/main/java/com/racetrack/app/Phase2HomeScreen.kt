package com.racetrack.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun Phase2HomeScreen(steps: Int, profile: ProfileDataStore, onStart: () -> Unit) {
    val goal = profile.dailyGoal.coerceAtLeast(1)
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Good morning${profile.name.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}", color = Color.White, fontSize = 27.sp) }
        item { Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(steps.toString(), color = Color.White, fontSize = 46.sp); Text("/ $goal steps", color = Muted); Spacer(Modifier.height(16.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Green); Text("${(progress * 100).roundToInt()}% of today's goal", color = Green, modifier = Modifier.padding(top = 10.dp)) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric2("Distance", "GPS only", Modifier.weight(1f)); Metric2("Calories", "Workout estimate", Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric2("Active time", "Workout sessions", Modifier.weight(1f)); Metric2("Water", "Not configured", Modifier.weight(1f)) } }
        item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = CircleShape) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Start Live Walk / Run") } }
    }
}

@Composable private fun Metric2(label: String, value: String, modifier: Modifier) { Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(16.dp)) { Text(label, color = Muted); Text(value, color = Color.White, fontSize = 15.sp) } } }
