package com.racetrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RaceTrackProfileEditScreen(existing: ProfileStore, onSaved: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(existing.name) }
    var height by remember { mutableStateOf(if (existing.heightCm > 0) existing.heightCm.toInt().toString() else "") }
    var weight by remember { mutableStateOf(if (existing.weightKg > 0) existing.weightKg.toString() else "") }
    var age by remember { mutableStateOf(if (existing.age > 0) existing.age.toString() else "") }
    var goal by remember { mutableStateOf(existing.dailyGoal.toString()) }
    var error by remember { mutableStateOf("") }

    fun save() {
        val h = height.toFloatOrNull()
        val w = weight.toFloatOrNull()
        val a = age.toIntOrNull()
        val g = goal.toIntOrNull()
        error = when {
            name.isBlank() -> "Please enter your name"
            h == null || h !in 100f..250f -> "Height must be between 100 and 250 cm"
            w == null || w !in 25f..300f -> "Weight must be between 25 and 300 kg"
            a == null || a !in 5..120 -> "Age must be between 5 and 120"
            g == null || g !in 1000..100000 -> "Daily goal must be between 1,000 and 100,000 steps"
            else -> {
                existing.save(name, h, w, a, g)
                onSaved()
                ""
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Charcoal)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.padding(start = 4.dp)) {
                Text(if (existing.isComplete) "Edit Profile" else "Create Profile", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Personalise your Race Track experience", color = Muted, fontSize = 12.sp)
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.size(58.dp).background(Green, RoundedCornerShape(18.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Person, null, tint = Charcoal, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.size(14.dp))
                        Column {
                            Text(if (name.isBlank()) "Your profile" else name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Your data stays on this device for now", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            item { ProfileField("Name", name, { name = it }, Icons.Default.Person) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileField("Height (cm)", height, { height = it.filter(Char::isDigit) }, Icons.Default.Height, Modifier.weight(1f))
                    ProfileField("Weight (kg)", weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, Icons.Default.Scale, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileField("Age", age, { age = it.filter(Char::isDigit) }, Icons.Default.Cake, Modifier.weight(1f))
                    ProfileField("Daily steps", goal, { goal = it.filter(Char::isDigit) }, Icons.Default.DirectionsWalk, Modifier.weight(1f))
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Card)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Why we ask", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("Height and weight help estimate distance and calories. Your daily goal controls the progress ring on Home.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
            if (error.isNotEmpty()) item { Text(error, color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp)) }
            item {
                Button(onClick = ::save, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Charcoal)) {
                    Text("Save Changes", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String, onValueChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}
