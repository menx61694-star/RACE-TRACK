package com.racetrack.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun MetricCard(icon: String, title: String, value: String, modifier: Modifier, action: String? = null) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Card), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 19.sp)
                Text(title, color = Color(0xFF9E9E9E), fontSize = 12.sp, modifier = Modifier.padding(start = 7.dp))
                if (action != null) Text(action, color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 7.dp))
            }
            Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}
