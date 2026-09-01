package com.racetrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsGreen = Color(0xFF00E676)
private val SettingsCyan = Color(0xFF00B0FF)
private val SettingsCoral = Color(0xFFFF5252)
private val SettingsCharcoal = Color(0xFF121212)
private val SettingsCard = Color(0xFF1E1E1E)
private val SettingsMuted = Color(0xFF9E9E9E)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOnboarding: () -> Unit
) {
    var section by remember { mutableStateOf<String?>(null) }

    when (section) {
        "sensitivity" -> StepSensitivityScreen(onBack = { section = null })
        "health" -> HealthConnectScreen(onBack = { section = null })
        "notifications" -> NotificationSettingsScreen(onBack = { section = null })
        else -> SettingsListScreen(
            onBack = onBack,
            onSensitivity = { section = "sensitivity" },
            onHealth = { section = "health" },
            onNotifications = { section = "notifications" },
            onOnboarding = onOnboarding
        )
    }
}

@Composable
private fun SettingsListScreen(
    onBack: () -> Unit,
    onSensitivity: () -> Unit,
    onHealth: () -> Unit,
    onNotifications: () -> Unit,
    onOnboarding: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SettingsCharcoal).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text("Settings", color = Color.White, fontSize = 27.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
            }
        }
        item { SettingsButton(Icons.Default.Tune, "Step sensitivity & calibration", "Configure how activity is detected", onSensitivity) }
        item { SettingsButton(Icons.Default.Watch, "Connected devices / Health Connect", "Manage health data connections", onHealth) }
        item { SettingsButton(Icons.Default.Notifications, "Notifications & reminders", "Inactivity and hydration reminders", onNotifications) }
        item { SettingsButton(Icons.Default.Slideshow, "View onboarding", "Review the app introduction", onOnboarding) }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SettingsCard), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Privacy", color = Color.White, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Your activity data is stored locally until cloud sync is implemented.", color = SettingsMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SettingsGreen)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = SettingsMuted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = SettingsMuted)
        }
    }
}

@Composable
private fun StepSensitivityScreen(onBack: () -> Unit) {
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    LazyColumn(Modifier.fillMaxSize().background(SettingsCharcoal).padding(20.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item { SettingsHeader("Step sensitivity", onBack) }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SettingsCard)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Sensor", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp)
                    Text("The app uses the phone's step-counter sensor when available.", color = SettingsMuted, fontSize = 13.sp)
                    Text("Sensitivity: ${when { sensitivity < 0.34f -> "Low"; sensitivity > 0.66f -> "High"; else -> "Normal" }}", color = SettingsGreen, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = SettingsGreen, activeTrackColor = SettingsGreen))
                    Text("Normal is recommended. Calibration options will be expanded when the sensor pipeline is complete.", color = SettingsMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HealthConnectScreen(onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(SettingsCharcoal).padding(20.dp), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsHeader("Health Connect", onBack) }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SettingsCard)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Connection status", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp)
                    Text("Not connected", color = SettingsCoral, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Health Connect integration is planned for a later phase. No external health data is being read yet.", color = SettingsMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsScreen(onBack: () -> Unit) {
    var inactivity by remember { mutableStateOf(false) }
    var hydration by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(SettingsCharcoal).padding(20.dp), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsHeader("Notifications", onBack) }
        item { ToggleRow("Inactivity alert", "Remind me after a period of inactivity", inactivity) { inactivity = it } }
        item { ToggleRow("Hydration reminder", "Remind me to drink water", hydration) { hydration = it } }
        item { Text("These switches control local preferences only. Scheduling will be wired in the notifications phase.", color = SettingsMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SettingsCard), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 15.sp); Text(subtitle, color = SettingsMuted, fontSize = 12.sp) }
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SettingsGreen))
        }
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
    }
}
