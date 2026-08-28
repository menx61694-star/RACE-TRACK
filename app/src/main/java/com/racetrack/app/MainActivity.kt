package com.racetrack.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { RaceTrackApp() } }
}

@Composable
fun RaceTrackApp() {
    val context = LocalContext.current
    val tracker = remember { StepTracker(context) }
    var screen by remember { mutableStateOf("home") }
    var onboarding by remember { mutableStateOf(false) }
    DisposableEffect(tracker) { onDispose { tracker.release() } }
    val startLive = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) screen = "live" }
    MaterialTheme(colorScheme = darkColorScheme(primary = Green, secondary = Cyan, background = Charcoal, surface = Card, error = Coral)) {
        if (onboarding) OnboardingScreen(onFinish = { onboarding = false }) else Scaffold(containerColor = Charcoal, bottomBar = {
            if (screen != "live" && screen != "settings") NavigationBar(containerColor = Card) {
                NavItem("home", Icons.Default.Home, "Home", screen) { screen = it }
                NavItem("analytics", Icons.Default.BarChart, "Stats", screen) { screen = it }
                NavItem("community", Icons.Default.EmojiEvents, "Community", screen) { screen = it }
                NavItem("profile", Icons.Default.Person, "Profile", screen) { screen = it }
            }
        }) { padding -> Box(Modifier.padding(padding)) {
            when (screen) {
                "home" -> HomeScreen(onStart = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) screen = "live" else startLive.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
                "live" -> LiveTrackingScreen(tracker, onFinish = { tracker.stop(); screen = "home" })
                "analytics" -> AnalyticsScreen()
                "community" -> CommunityScreen()
                "profile" -> ProfileScreen { screen = "settings" }
                "settings" -> SettingsScreen({ screen = "profile" }) { onboarding = true }
            }
        } }
    }
}

@Composable
fun HomeScreen(onStart: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Green, Cyan))), contentAlignment = Alignment.Center) { Text("R", color = Charcoal, fontWeight = FontWeight.Black, fontSize = 20.sp) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Good morning", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp); Text("Ready to move?", color = Muted, fontSize = 13.sp) }; Icon(Icons.Default.NotificationsNone, null, tint = Color.White) } }
        item { StepRing(0, 10000) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("🔥", "Calories", "0 kcal", Modifier.weight(1f)); MetricCard("📏", "Distance", "0.0 km", Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("⏱", "Active Time", "0 min", Modifier.weight(1f)); MetricCard("💧", "Water", "0.0 / 3.0 L", Modifier.weight(1f), "+") } }
        item { WeeklyCard() }
        item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Start Live Walk / Run", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) } }
    }
}

@Composable
fun MetricCard(icon: String, title: String, value: String, modifier: Modifier, action: String? = null) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 19.sp); Spacer(Modifier.width(7.dp)); Text(title, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); if (action != null) Text(action, color = Green, fontWeight = FontWeight.Bold) }; Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun StepRing(steps: Int, goal: Int) {
    val progress = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) { Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize()) { drawArc(Color(0xFF303030), -90f, 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round)); if (progress > 0) drawArc(Brush.sweepGradient(listOf(Green, Cyan, Green)), -90f, 360f * progress, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round)) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(steps.toString(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black); Text("/ ${goal} steps", color = Muted); Text("${(progress * 100).toInt()}%", color = Green, fontWeight = FontWeight.Bold) } }; Text("Today's movement", color = Muted, fontSize = 13.sp) } }

@Composable private fun WeeklyCard() { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("This week", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("0 steps", color = Green, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) { listOf("M","T","W","T","F","S","S").forEach { day -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.width(22.dp).height(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030))); Spacer(Modifier.height(7.dp)); Text(day, color = Muted, fontSize = 11.sp) } } } } } }

@Composable fun LiveTrackingScreen(tracker: StepTracker, onFinish: () -> Unit) { var elapsed by remember { mutableLongStateOf(0L) }; var running by remember { mutableStateOf(true) }; LaunchedEffect(Unit) { tracker.start(); while (true) { delay(1000); if (running) elapsed++ } }; DisposableEffect(Unit) { onDispose { tracker.stop() } }; Column(Modifier.fillMaxSize().background(Charcoal)) { Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF20282A)), contentAlignment = Alignment.Center) { Surface(color = Color(0xDD1E1E1E), shape = RoundedCornerShape(14.dp)) { Text(if (tracker.isAvailable) "STEP SENSOR • ACTIVE" else "STEP SENSOR • UNAVAILABLE", color = if (tracker.isAvailable) Green else Coral, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal=14.dp, vertical=9.dp)) } }; Surface(color=Card, shape=RoundedCornerShape(topStart=30.dp, topEnd=30.dp)) { Column(Modifier.padding(22.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) { Text(formatDuration(elapsed), color=Color.White, fontSize=42.sp, fontWeight=FontWeight.Black); Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { LiveStat("Steps", tracker.steps.toString()); LiveStat("Session", if(running) "Active" else "Paused"); LiveStat("Sensor", if(tracker.isAvailable) "Ready" else "N/A") }; Row(horizontalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()) { OutlinedButton(onClick={ if(running){tracker.pause();running=false}else{tracker.resume();running=true} }, modifier=Modifier.weight(1f).height(54.dp), shape=CircleShape) { Text(if(running) "Pause" else "Resume") }; Button(onClick=onFinish, modifier=Modifier.weight(1f).height(54.dp), shape=CircleShape, colors=ButtonDefaults.buttonColors(containerColor=Coral)) { Text("Finish") } } } } } }
private fun formatDuration(seconds: Long): String = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
@Composable private fun LiveStat(label:String,value:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=Color.White,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(label,color=Muted,fontSize=11.sp)}}
