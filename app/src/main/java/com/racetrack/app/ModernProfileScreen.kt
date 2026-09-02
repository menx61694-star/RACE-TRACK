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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProfileBg = Color(0xFF121212)
private val ProfileCard = Color(0xFF1E1E1E)
private val ProfileGreen = Color(0xFF00E676)
private val ProfileCyan = Color(0xFF00B0FF)
private val ProfileMuted = Color(0xFF9E9E9E)

@Composable
fun ModernProfileSetupScreen(existing: ProfileStore, onSaved: () -> Unit, onBack: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(existing.name) }
    var height by remember { mutableStateOf(if (existing.heightCm > 0) existing.heightCm.toInt().toString() else "") }
    var weight by remember { mutableStateOf(if (existing.weightKg > 0) existing.weightKg.toString() else "") }
    var age by remember { mutableStateOf(if (existing.age > 0) existing.age.toString() else "") }
    var goal by remember { mutableStateOf(existing.dailyGoal.toString()) }

    val heightValue = height.toFloatOrNull()
    val weightValue = weight.toFloatOrNull()
    val ageValue = age.toIntOrNull()
    val goalValue = goal.toIntOrNull()
    val valid = name.trim().isNotEmpty() && heightValue != null && heightValue in 100f..250f &&
        weightValue != null && weightValue in 25f..250f && ageValue != null && ageValue in 10..120 &&
        goalValue != null && goalValue in 1000..100000

    Column(Modifier.fillMaxSize().background(ProfileBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f).padding(horizontal = if (onBack == null) 10.dp else 0.dp)) {
                Text(if (existing.isComplete) "Edit profile" else "Create your profile", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Personalize your tracking estimates", color = ProfileMuted, fontSize = 13.sp)
            }
        }

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = ProfileCard), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Basic information", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        ProfileField(name, { name = it }, "Name", Icons.Default.Person, KeyboardType.Text)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileCompactField(height, { height = it.filter(Char::isDigit) }, "Height", "cm", Icons.Default.Height, Modifier.weight(1f))
                    ProfileCompactField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, "Weight", "kg", Icons.Default.Scale, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileCompactField(age, { age = it.filter(Char::isDigit) }, "Age", "years", Icons.Default.Person, Modifier.weight(1f))
                    ProfileCompactField(goal, { goal = it.filter(Char::isDigit) }, "Daily goal", "steps", Icons.Default.Flag, Modifier.weight(1f))
                }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProfileCard), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Why we ask", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Height and weight help estimate distance and calories. Your workout history stays on this device in this phase.", color = ProfileMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
            item {
                Button(
                    enabled = valid,
                    onClick = { existing.save(name, heightValue!!, weightValue!!, ageValue!!, goalValue!!); onSaved() },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProfileGreen, contentColor = ProfileBg)
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (existing.isComplete) "Save Changes" else "Save & Continue", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileField(value: String, onValueChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, keyboard: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedLabelColor = ProfileGreen, unfocusedLabelColor = ProfileMuted,
            focusedIndicatorColor = ProfileGreen, unfocusedIndicatorColor = ProfileMuted,
            cursorColor = ProfileGreen
        ),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ProfileCompactField(value: String, onValueChange: (String) -> Unit, label: String, suffix: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        trailingIcon = { Text(suffix, color = ProfileMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 10.dp)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedLabelColor = ProfileGreen, unfocusedLabelColor = ProfileMuted,
            focusedIndicatorColor = ProfileGreen, unfocusedIndicatorColor = ProfileMuted,
            cursorColor = ProfileGreen
        ),
        shape = RoundedCornerShape(16.dp)
    )
}
