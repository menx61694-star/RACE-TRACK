package com.racetrack.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Phase2OnboardingScreen(profile: ProfileDataStore, onComplete: () -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var height by remember { mutableStateOf(if (profile.heightCm > 0) profile.heightCm.toInt().toString() else "") }
    var weight by remember { mutableStateOf(if (profile.weightKg > 0) profile.weightKg.toString() else "") }
    var age by remember { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var goal by remember { mutableFloatStateOf(profile.dailyGoal.toFloat()) }
    val valid = name.trim().isNotEmpty() && (height.toFloatOrNull() ?: 0f) > 0f && (weight.toFloatOrNull() ?: 0f) > 0f && (age.toIntOrNull() ?: 0) > 0

    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 24.dp)) {
        item { Text("Welcome to Race Track", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black); Text("Set up your profile once. Your tracking estimates will use these values.", color = Muted) }
        item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(height, { height = it.filter(Char::isDigit) }, label = { Text("Height (cm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("Daily step goal: ${goal.toInt()}", color = Color.White, fontWeight = FontWeight.Bold); Slider(value = goal, onValueChange = { goal = (it / 500f).toInt() * 500f }, valueRange = 3000f..30000f, steps = 53) }
        item { Button(enabled = valid, onClick = { profile.name = name; profile.heightCm = height.toFloat(); profile.weightKg = weight.toFloat(); profile.age = age.toInt(); profile.dailyGoal = goal.toInt(); profile.onboardingComplete = true; onComplete() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Get Started") } }
    }
}

@Composable
fun Phase2ProfileScreen(profile: ProfileDataStore, onBack: () -> Unit, onEdit: () -> Unit) {
    val bmi = profile.bmi()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text("Profile", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp)) } }
        item { Card(colors = CardDefaults.cardColors(containerColor = Card)) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(profile.name.ifBlank { "Profile not set" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Height  ${if(profile.heightCm > 0) "%.0f cm".format(profile.heightCm) else "Not set"}", color = Muted); Text("Weight  ${if(profile.weightKg > 0) "%.1f kg".format(profile.weightKg) else "Not set"}", color = Muted); Text("Age  ${if(profile.age > 0) profile.age else "Not set"}", color = Muted); Text("BMI  ${bmi?.let { "%.1f".format(it) } ?: "Not available"}", color = Muted); Text("Daily goal  ${profile.dailyGoal} steps", color = Green, fontWeight = FontWeight.Bold) } } }
        item { Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit profile") } }
    }
}
