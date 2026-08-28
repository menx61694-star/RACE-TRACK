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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RaceTrackApp() }
    }
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
        if (onboarding) {
            OnboardingScreen(onFinish = { onboarding = false })
        } else {
            Scaffold(containerColor = Charcoal, bottomBar = {
                if (screen != "live" && screen != "settings") NavigationBar(containerColor = Card) {
                    NavItem("home", Icons.Default.Home, "Home", screen) { selected -> screen = selected }
                    NavItem("analytics", Icons.Default.BarChart, "Stats", screen) { selected -> screen = selected }
                    NavItem("community", Icons.Default.EmojiEvents, "Community", screen) { selected -> screen = selected }
                    NavItem("profile", Icons.Default.Person, "Profile", screen) { selected -> screen = selected }
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

@Composable private fun StepRing(steps: Int, goal: Int) {
    val progress = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) { Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize()) { drawArc(Color(0xFF303030), -90f, 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round)); if (progress > 0) drawArc(Brush.sweepGradient(listOf(Green, Cyan, Green)), -90f, 360f * progress, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round)) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(steps.toString(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black); Text("/ ${goal} steps", color = Muted); Text("${(progress * 100).toInt()}%", color = Green, fontWeight = FontWeight.Bold) } }; Text("Today's movement", color = Muted, fontSize = 13.sp) } }

@Composable
fun MetricCard(icon: String, title: String, value: String, modifier: Modifier, action: String? = null) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 19.sp); Spacer(Modifier.width(7.dp)); Text(title, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); if (action != null) Text(action, color = Green, fontWeight = FontWeight.Bold) }; Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun WeeklyCard() { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("This week", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("0 steps", color = Green, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) { listOf("M","T","W","T","F","S","S").forEach { day -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.width(22.dp).height(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030))); Spacer(Modifier.height(7.dp)); Text(day, color = Muted, fontSize = 11.sp) } } } } } }

@Composable
fun LiveTrackingScreen(tracker: StepTracker, onFinish: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { tracker.start(); while (true) { delay(1000); if (running) elapsed++ } }
    DisposableEffect(Unit) { onDispose { tracker.stop() } }
    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF20282A)), contentAlignment = Alignment.Center) { Surface(color = Color(0xDD1E1E1E), shape = RoundedCornerShape(14.dp)) { Text(if (tracker.isAvailable) "STEP SENSOR • ACTIVE" else "STEP SENSOR • UNAVAILABLE", color = if (tracker.isAvailable) Green else Coral, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal=14.dp, vertical=9.dp)) } }
        Surface(color=Card, shape=RoundedCornerShape(topStart=30.dp, topEnd=30.dp)) { Column(Modifier.padding(22.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) { Text(formatDuration(elapsed), color=Color.White, fontSize=42.sp, fontWeight=FontWeight.Black); Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { LiveStat("Steps", tracker.steps.toString()); LiveStat("Session", if(running) "Active" else "Paused"); LiveStat("Sensor", if(tracker.isAvailable) "Ready" else "N/A") }; if (!tracker.isAvailable) Text("This phone does not expose the hardware step-counter sensor. GPS-based tracking will be added in the next phase.", color=Muted, fontSize=12.sp); Row(horizontalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()) { OutlinedButton(onClick={ if(running){tracker.pause();running=false}else{tracker.resume();running=true} }, modifier=Modifier.weight(1f).height(54.dp), shape=CircleShape) { Icon(if(running) Icons.Default.Pause else Icons.Default.PlayArrow,null); Spacer(Modifier.width(6.dp)); Text(if(running) "Pause" else "Resume") }; Button(onClick=onFinish, modifier=Modifier.weight(1f).height(54.dp), shape=CircleShape, colors=ButtonDefaults.buttonColors(containerColor=Coral)) { Icon(Icons.Default.Stop,null); Spacer(Modifier.width(6.dp)); Text("Finish") } } } }
    }
}

private fun formatDuration(seconds: Long): String = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
@Composable private fun LiveStat(label:String,value:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=Color.White,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(label,color=Muted,fontSize=11.sp)}}

@Composable
fun AnalyticsScreen(){ LazyColumn(Modifier.fillMaxSize().padding(20.dp),contentPadding=PaddingValues(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{Text("Your Progress",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Black)};item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("Day","Week","Month","Year").forEachIndexed{i,t->SegmentedButton(i==1,onClick={},shape=SegmentedButtonDefaults.itemShape(i,4)){Text(t)}}}};item{Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Card)){Column(Modifier.padding(18.dp)){Text("Steps",color=Muted);Text("0",color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Black);Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Bottom){repeat(7){Box(Modifier.width(25.dp).height(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030)))}}}}};item{InfoCard("— 0%","No previous activity to compare yet.",Muted)};item{Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Card)){Column(Modifier.padding(18.dp)){Text("Lifetime milestones",color=Color.White,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Text("0",color=Green,fontSize=28.sp,fontWeight=FontWeight.Black);Text("Total steps",color=Muted);Spacer(Modifier.height(10.dp));Text("0 km",color=Cyan,fontSize=28.sp,fontWeight=FontWeight.Black);Text("Total distance",color=Muted)}}}}}

@Composable private fun InfoCard(title:String,body:String,color:Color){Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Card)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text(title,color=color,fontWeight=FontWeight.Black,fontSize=17.sp);Spacer(Modifier.width(14.dp));Text(body,color=Color.White,fontSize=13.sp)}}}

@Composable
fun CommunityScreen(){LazyColumn(Modifier.fillMaxSize().padding(20.dp),contentPadding=PaddingValues(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{Text("Community",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Black)};item{TabRow(selectedTabIndex=0,containerColor=Color.Transparent,contentColor=Green){listOf("Friends","Global","Challenges").forEachIndexed{i,t->Tab(i==0,{},text={Text(t)})}}};item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Card)){Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("No community activity yet",color=Color.White,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text("Friends, rankings and challenges will appear here after accounts and sync are implemented.",color=Muted,fontSize=13.sp,textAlign=TextAlign.Center)}}};item{Text("Today's leaderboard",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)};item{items(emptyList<String>()){} };item{InfoCard("🏆 Challenges","Challenges will be available after community accounts are implemented.",Green)}}}

@Composable
fun ProfileScreen(onSettings:()->Unit){LazyColumn(Modifier.fillMaxSize().padding(20.dp),contentPadding=PaddingValues(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{Text("Profile",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Black)};item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Card)){Column(Modifier.padding(20.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(62.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Green,Cyan))),contentAlignment=Alignment.Center){Text("R",color=Charcoal,fontSize=25.sp,fontWeight=FontWeight.Black)};Spacer(Modifier.width(14.dp));Column{Text("Your profile",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Black);Text("Getting started",color=Green,fontSize=13.sp)}};Spacer(Modifier.height(18.dp));Text("Height • Not set     Weight • Not set     BMI • Not available",color=Muted,fontSize=12.sp)}}};item{Text("Achievements",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Card)){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("No achievements yet",color=Color.White,fontWeight=FontWeight.Bold);Text("Complete your first tracked activity to start earning badges.",color=Muted,fontSize=12.sp,textAlign=TextAlign.Center)}}};item{Text("Settings",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)};item{SettingsRow(Icons.Default.Tune,"Step sensitivity & calibration",onSettings)};item{SettingsRow(Icons.Default.Watch,"Connected devices / Health Connect",onSettings)};item{SettingsRow(Icons.Default.Notifications,"Notifications & reminders",onSettings)};item{SettingsRow(Icons.Default.Settings,"App settings",onSettings)}}}

@Composable private fun SettingsRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,onClick:()->Unit){Card(onClick=onClick,shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Card),modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green);Spacer(Modifier.width(14.dp));Text(title,color=Color.White,modifier=Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}

@Composable
fun OnboardingScreen(onFinish:()->Unit){var page by remember{mutableIntStateOf(0)};val titles=listOf("Track every movement","Reach your health targets","Compete with friends");val subtitles=listOf("Track your movement in real-time with steps, distance and GPS.","Understand calories, distance and daily activity at a glance.","Challenge friends, climb the leaderboard and stay consistent.");Column(Modifier.fillMaxSize().background(Charcoal).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(40.dp));Box(Modifier.size(90.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Green,Cyan))),contentAlignment=Alignment.Center){Icon(Icons.Default.DirectionsWalk,null,tint=Charcoal,modifier=Modifier.size(48.dp))};Spacer(Modifier.height(50.dp));Text(titles[page],color=Color.White,fontSize=29.sp,fontWeight=FontWeight.Black,textAlign=TextAlign.Center);Spacer(Modifier.height(12.dp));Text(subtitles[page],color=Muted,fontSize=15.sp,textAlign=TextAlign.Center);Spacer(Modifier.weight(1f));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){repeat(3){i->Box(Modifier.width(if(i==page)26.dp else 8.dp).height(8.dp).clip(CircleShape).background(if(i==page)Green else Color(0xFF444444)))}};Spacer(Modifier.height(24.dp));Button(onClick={if(page<2)page++ else onFinish()},modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Green,contentColor=Charcoal)){Text(if(page==2)"Get Started" else "Continue",fontWeight=FontWeight.ExtraBold,fontSize=16.sp)};Spacer(Modifier.height(18.dp));if(page<2)TextButton(onClick=onFinish){Text("Skip",color=Muted)}}}
