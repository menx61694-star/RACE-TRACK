package com.racetrack.app

import android.location.Location
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun Phase2HomeScreen(
    steps: Int,
    distanceKm: Double,
    calories: Int,
    onStart: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Today's Activity", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("👟", "Steps", steps.toString(), Modifier.weight(1f))
            MetricCard("📏", "Distance", String.format("%.2f km", distanceKm), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("🔥", "Calories", "$calories kcal", Modifier.weight(1f))
            MetricCard("⏱", "Active", "Live", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start Walk / Run")
        }
    }
}

@Composable
fun Phase2LiveScreen(
    steps: Int,
    weightKg: Float,
    onFinish: (Double, Int, Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var points by remember { mutableStateOf(emptyList<Pair<Double, Double>>()) }
    var paused by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0L) }

    LaunchedEffect(paused) {
        while (!paused) {
            delay(1000)
            seconds++
        }
    }

    val distanceKm = remember(points) {
        points.zipWithNext().sumOf { (a, b) ->
            val result = FloatArray(1)
            Location.distanceBetween(a.first, a.second, b.first, b.second, result)
            result[0].toDouble()
        } / 1000.0
    }
    val calories = (distanceKm * weightKg.coerceAtLeast(1f) * 0.75).toInt()

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(null, """
                        <html><body style='margin:0;background:#121212;display:flex;align-items:center;justify-content:center;color:white;font-family:sans-serif'>
                        <div><h2>Live Route</h2><p>GPS route will appear here</p></div></body></html>
                    """.trimIndent(), "text/html", "UTF-8", null)
                }
            }
        )
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60), fontSize = 34.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("$steps steps")
                Text(String.format("%.2f km", distanceKm))
                Text("$calories kcal")
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { paused = !paused }, modifier = Modifier.weight(1f)) {
                    Text(if (paused) "Resume" else "Pause")
                }
                Button(onClick = { onFinish(distanceKm, calories, seconds) }, modifier = Modifier.weight(1f)) {
                    Text("Finish")
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Back") }
        }
    }
}

@Composable
fun Phase2ProgressScreen(
    steps: List<Int>,
    selectedPeriod: String,
    onPeriodChange: (String) -> Unit
) {
    val periods = listOf("Day", "Week", "Month", "Year")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Your Progress", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                periods.forEach { period ->
                    Button(onClick = { onPeriodChange(period) }, modifier = Modifier.weight(1f)) { Text(period, fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Selected: $selectedPeriod")
            Spacer(Modifier.height(12.dp))
            BarChart(steps)
        }
    }
}

@Composable
private fun BarChart(values: List<Int>) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        values.forEach { value ->
            val barHeight = (value.toFloat() / max).coerceIn(0.05f, 1f) * 150f
            Box(Modifier.width(18.dp).height(barHeight.dp).background(Green, RoundedCornerShape(6.dp)))
        }
    }
}
