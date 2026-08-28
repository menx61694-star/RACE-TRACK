package com.racetrack.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Phase2AnalyticsScreen(store: WorkoutStore) {
    var selected by remember { mutableStateOf(Period.WEEK) }
    val summary = store.summary(selected)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item { Text("Your Progress", color = Color.White, fontSize = 28.sp) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Period.DAY to "Day", Period.WEEK to "Week", Period.MONTH to "Month", Period.YEAR to "Year").forEachIndexed { index, pair ->
                    SegmentedButton(selected = selected == pair.first, onClick = { selected = pair.first }, shape = SegmentedButtonDefaults.itemShape(index, 4)) { Text(pair.second) }
                }
            }
        }
        item { SummaryCard(summary) }
        item { Card(colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp)) { Text("Period", color = Muted); Text(pairLabel(selected), color = Color.White, fontSize = 18.sp); Spacer(Modifier.height(12.dp)); Text("Steps", color = Muted); Text(summary.steps.toString(), color = Green, fontSize = 30.sp); Text("Distance  %.2f km".format(summary.distanceMeters / 1000.0), color = Color.White); Text("Calories  %.0f kcal".format(summary.calories), color = Color.White); Text("Active time  ${formatMinutes(summary.durationSeconds)}", color = Color.White) } } }
    }
}

@Composable private fun SummaryCard(summary: WorkoutStore.Summary) { Card(colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(22.dp)) { Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Tracked activity", color = Muted); Text("${summary.steps} steps", color = Color.White, fontSize = 22.sp) }; Text("%.2f km".format(summary.distanceMeters / 1000.0), color = Cyan, fontSize = 18.sp) } } }
private fun pairLabel(period: Period) = when(period) { Period.DAY -> "Today"; Period.WEEK -> "This week"; Period.MONTH -> "This month"; Period.YEAR -> "This year" }
private fun formatMinutes(seconds: Long) = "${seconds / 60} min"
