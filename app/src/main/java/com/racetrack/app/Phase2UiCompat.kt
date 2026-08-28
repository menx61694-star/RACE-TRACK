package com.racetrack.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared MetricCard for Phase 2 screens. */
@Composable
fun MetricCard(
    icon: String,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    action: String? = null
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Card),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 19.sp)
                Spacer(Modifier.width(7.dp))
                Text(title, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (action != null) Text(action, color = Green)
            }
            Text(value, color = androidx.compose.ui.graphics.Color.White, fontSize = 19.sp)
        }
    }
}

/** Package-local compatibility wrapper for screens that use Modifier.width without importing it. */
fun Modifier.width(value: Dp): Modifier = androidx.compose.foundation.layout.width(this, value)
