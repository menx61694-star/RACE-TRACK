package com.racetrack.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun Phase2SettingsScreen(
    context: Context,
    onEditProfile: () -> Unit,
    onAccountSync: () -> Unit,
    onBack: () -> Unit
) {
    val activity = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    val location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", color = Color.White, fontSize = 28.sp) }
        item { SettingCard("Step tracking", if (activity) "Active • Activity recognition allowed" else "Permission required") }
        item { SettingCard("GPS tracking", if (location) "Ready • Location permission allowed" else "Permission required") }
        item { Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Card)) { Text("Edit profile", color = Color.White) } }
        item { Button(onClick = onAccountSync, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Card)) { Text("Account & cloud sync", color = Color.White) } }
        item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) { Text("Back to profile") } }
    }
}

@Composable
private fun SettingCard(title: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, color = Color.White, fontSize = 17.sp); Text(status, color = Muted, fontSize = 13.sp) }
    }
}
