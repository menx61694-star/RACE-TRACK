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

private val Green = Color(0xFF00E676)
private val Cyan = Color(0xFF00B0FF)
private val Coral = Color(0xFFFF5252)
private val Charcoal = Color(0xFF121212)
private val Card = Color(0xFF1E1E1E)
private val Muted = Color(0xFF9E9E9E)

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

    val startLive = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) screen = "live"
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Green, secondary = Cyan, background = Charcoal, surface = Card, error = Coral)) {
        if (onboarding) {
            OnboardingScreen(onFinish = { onboarding = false })
        } else {
            Scaffold(
                containerColor = Charcoal,
                bottomBar = {
                    if (screen != "live") NavigationBar(containerColor = Card) {
                        NavItem("home", Icons.Default.Home, "Home", screen) { screen = it }
                        NavItem("analytics", Icons.Default.BarChart, "Stats", screen) { screen = it }
                        NavItem("community", Icons.Default.EmojiEvents, "Community", screen) { screen = it }
                        NavItem("profile", Icons.Default.Person, "Profile", screen) { screen = it }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (screen) {
                        "home" -> HomeScreen(onStart = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                                screen = "live"
                            } else startLive.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        })
                        "live" -> LiveTrackingScreen(tracker = tracker, onFinish = { tracker.stop(); screen = "home" })
                        "analytics" -> AnalyticsScreen()
                        "community" -> CommunityScreen()
                        "profile" -> ProfileScreen(onOnboarding = { onboarding = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(key: String, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: String, onSelect: (String) -> Unit) {
    NavigationBarItem(selected = selected == key, onClick = { onSelect(key) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color.Transparent))
}

@Composable
fun HomeScreen(onStart: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Green, Cyan))), contentAlignment = Alignment.Center) { Text("V", color = Charcoal, fontWeight = FontWeight.Black, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Good morning, Vivek", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp); Text("Ready to move?", color = Muted, fontSize = 13.sp) }
                Text("🔥 5", color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.width(14.dp)); Icon(Icons.Default.NotificationsNone, null, tint = Color.White)
            }
        }
        item { StepRing() }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("🔥", "Calories", "320 kcal", Modifier.weight(1f)); MetricCard("📏", "Distance", "5.2 km", Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { MetricCard("⏱", "Active Time", "48 min", Modifier.weight(1f)); MetricCard("💧", "Water", "1.5 / 3.0 L", Modifier.weight(1f), "+") } }
        item { WeeklyCard() }
        item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Start Live Walk / Run", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) } }
    }
}

@Composable
private fun StepRing() {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color(0xFF303030), -90f, 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Brush.sweepGradient(listOf(Green, Cyan, Green)), -90f, 266f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("7,450", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black); Text("/ 10,000 steps", color = Muted, fontSize = 14.sp); Spacer(Modifier.height(5.dp)); Text("74%", color = Green, fontWeight = FontWeight.Bold) }
            }
            Text("Today's movement", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MetricCard(icon: String, title: String, value: String, modifier: Modifier, action: String? = null) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 19.sp); Spacer(Modifier.width(7.dp)); Text(title, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); if (action != null) Text(action, color = Green, fontWeight = FontWeight.Bold) }; Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun WeeklyCard() {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("This week", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("42.6k steps", color = Green, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) { listOf(45,65,52,80,70,92,74).forEachIndexed { index,h -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.width(22.dp).height((h*.65f).dp).clip(RoundedCornerShape(8.dp)).background(if(index==6) Brush.verticalGradient(listOf(Cyan,Green)) else Brush.verticalGradient(listOf(Color(0xFF3A3A3A),Color(0xFF2A2A2A))))); Spacer(Modifier.height(7.dp)); Text(listOf("M","T","W","T","F","S","S")[index], color=if(index==6) Green else Muted, fontSize=11.sp) } } } }
    }
}

@Composable
fun LiveTrackingScreen(tracker: StepTracker, onFinish: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { tracker.start(); while (true) { delay(1000); if (running) elapsed++ } }
    DisposableEffect(Unit) { onDispose { tracker.stop() } }

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF20282A)), contentAlignment = Alignment.Center) {
            Surface(color = Color(0xDD1E1E1E), shape = RoundedCornerShape(14.dp)) { Text(if (tracker.isAvailable) "STEP SENSOR • ACTIVE" else "STEP SENSOR • UNAVAILABLE", color = if (tracker.isAvailable) Green else Coral, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal=14.dp, vertical=9.dp)) }
        }
        Surface(color=Card, shape=RoundedCornerShape(topStart=30.dp, topEnd=30.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(formatDuration(elapsed), color=Color.White, fontSize=42.sp, fontWeight=FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { LiveStat("Steps", tracker.steps.toString()); LiveStat("Session", if(running) "Active" else "Paused"); LiveStat("Sensor", if(tracker.isAvailable) "Ready" else "N/A") }
                if (!tracker.isAvailable) Text("This phone does not expose the hardware step-counter sensor. GPS-based tracking will be added in the next phase.", color=Muted, fontSize=12.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick={ if(running){tracker.pause();running=false}else{tracker.resume();running=true} }, modifier=Modifier.weight(1f).height(54.dp), shape=CircleShape) { Icon(if(running) Icons.Default.Pause else Icons.Default.PlayArrow,null); Spacer(Modifier.width(6.dp)); Text(if(running) "Pause" else "Resume") }
                    Button(onClick=onFinish, modifier=Modifier.weight(1f).height(54.dp), shape=CircleShape, colors=ButtonDefaults.buttonColors(containerColor=Coral)) { Icon(Icons.Default.Stop,null); Spacer(Modifier.width(6.dp)); Text("Finish") }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)

@Composable private fun LiveStat(label:String,value:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=Color.White,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(label,color=Muted,fontSize=11.sp)}}

@Composable
fun AnalyticsScreen(){ LazyColumn(Modifier.fillMaxSize().padding(20.dp),contentPadding=PaddingValues(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{Text("Your Progress",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Black)};item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("Day","Week","Month","Year").forEachIndexed{i,t->SegmentedButton(i==1,onClick={},shape=SegmentedButtonDefaults.itemShape(i,4)){Text(t)}}}};item{Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Card)){Column(Modifier.padding(18.dp)){Text("Steps",color=Muted);Text("42,680",color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Black);Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Bottom){listOf(42,55,48,72,62,88,74).forEach{v->Box(Modifier.width(25.dp).height((v*1.25f).dp).clip(RoundedCornerShape(8.dp)).background(Green))}}}}};item{InfoCard("↗ 14%","You walked 14% more than last week.",Green)};item{Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Card)){Column(Modifier.padding(18.dp)){Text("Lifetime milestones",color=Color.White,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Text("1.2M",color=Green,fontSize=28.sp,fontWeight=FontWeight.Black);Text("Total steps",color=Muted);Spacer(Modifier.height(10.dp));Text("890 km",color=Cyan,fontSize=28.sp,fontWeight=FontWeight.Black);Text("Total distance",color=Muted)}}}}}

@Composable private fun InfoCard(title:String,body:String,color:Color){Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Card)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text(title,color=color,fontWeight=FontWeight.Black,fontSize=19.sp);Spacer(Modifier.width(14.dp));Text(body,color=Color.White,fontSize=13.sp)}}}

@Composable
fun CommunityScreen(){LazyColumn(Modifier.fillMaxSize().padding(20.dp),contentPadding=PaddingValues(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{Text("Community",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Black)};item{TabRow(selectedTabIndex=0,containerColor=Color.Transparent,contentColor=Green){listOf("Friends","Global","Challenges").forEachIndexed{i,t->Tab(i==0,{},text={Text(t)})}}};item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Card)){Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Bottom){listOf("🥈\nAman\n9.2k","🥇\nRiya\n12.4k","🥉\nNeha\n8.7k").forEach{Text(it,color=Color.White,textAlign=TextAlign.Center,fontWeight=FontWeight.Bold)}}}};item{Text("Today's leaderboard",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)};items(6){i->LeaderRow(i+1,listOf("Riya","Aman","Neha","Vivek","Arjun","Priya")[i],listOf("12,420","9,240","8,720","7,450","6,980","6,430")[i])};item{InfoCard("🏆 Weekend 20k","Join the challenge and reach 20,000 steps.",Green)}}}

@Composable private fun LeaderRow(rank:Int,name:String,steps:String){Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Card)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("$rank",color=Muted,modifier=Modifier.width(28.dp),fontWeight=FontWeight.Bold);Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF303030)),contentAlignment=Alignment.Center){Text(name.take(1),color=Green,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(12.dp));Text(name,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(steps,color=Muted);Spacer(Modifier.width(8.dp));Icon(Icons.Default.FavoriteBorder,null,tint=Green,modifier=Modifier.size(18.dp))}}}

@Composable
fun ProfileScreen(onOnboarding:()->Unit){LazyColumn(Modifier.fillMaxSize().padding(20.dp),contentPadding=PaddingValues(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{Text("Profile",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Black)};item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Card)){Column(Modifier.padding(20.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(62.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Green,Cyan))),contentAlignment=Alignment.Center){Text("V",color=Charcoal,fontSize=25.sp,fontWeight=FontWeight.Black)};Spacer(Modifier.width(14.dp));Column{Text("Vivek",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Black);Text("Gold Walker • Level 12",color=Green,fontSize=13.sp)}};Spacer(Modifier.height(18.dp));Text("Height • 175 cm     Weight • 68 kg     BMI • 22.2",color=Muted,fontSize=12.sp)}}};item{Text("Achievements",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)};item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf("🏆\nFirst 10k","🌅\nEarly Bird","🔥\n7-Day Streak").forEach{Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(it.substringBefore("\n"),fontSize=24.sp);Text(it.substringAfter("\n"),color=Muted,fontSize=11.sp,textAlign=TextAlign.Center)}}}};item{Text("Settings",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)};item{SettingsRow(Icons.Default.Tune,"Step sensitivity & calibration")};item{SettingsRow(Icons.Default.Watch,"Connected devices / Health Connect")};item{SettingsRow(Icons.Default.Notifications,"Notifications & reminders")};item{SettingsRow(Icons.Default.DarkMode,"Dark mode",true)};item{OutlinedButton(onClick=onOnboarding,modifier=Modifier.fillMaxWidth()){Text("View onboarding")}}}}

@Composable private fun SettingsRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,enabled:Boolean=false){Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Card)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green);Spacer(Modifier.width(14.dp));Text(title,color=Color.White,modifier=Modifier.weight(1f));if(enabled)Switch(true,{})else Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}

@Composable
fun OnboardingScreen(onFinish:()->Unit){var page by remember{mutableIntStateOf(0)};val titles=listOf("Track every movement","Reach your health targets","Compete with friends");val subtitles=listOf("Track your movement in real-time with steps, distance and GPS.","Understand calories, distance and daily activity at a glance.","Challenge friends, climb the leaderboard and stay consistent.");Column(Modifier.fillMaxSize().background(Charcoal).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(40.dp));Box(Modifier.size(90.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Green,Cyan))),contentAlignment=Alignment.Center){Icon(Icons.Default.DirectionsWalk,null,tint=Charcoal,modifier=Modifier.size(48.dp))};Spacer(Modifier.height(50.dp));Text(titles[page],color=Color.White,fontSize=29.sp,fontWeight=FontWeight.Black,textAlign=TextAlign.Center);Spacer(Modifier.height(12.dp));Text(subtitles[page],color=Muted,fontSize=15.sp,textAlign=TextAlign.Center);Spacer(Modifier.weight(1f));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){repeat(3){i->Box(Modifier.width(if(i==page)26.dp else 8.dp).height(8.dp).clip(CircleShape).background(if(i==page)Green else Color(0xFF444444)))}};Spacer(Modifier.height(24.dp));Button(onClick={if(page<2)page++ else onFinish()},modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Green,contentColor=Charcoal)){Text(if(page==2)"Get Started" else "Continue",fontWeight=FontWeight.ExtraBold,fontSize=16.sp)};Spacer(Modifier.height(18.dp));if(page<2)TextButton(onClick=onFinish){Text("Skip",color=Muted)}}}
