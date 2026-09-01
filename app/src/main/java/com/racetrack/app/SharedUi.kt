package com.racetrack.app

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

val Green = Color(0xFF00E676)
val Cyan = Color(0xFF00B0FF)
val Coral = Color(0xFFFF5252)
val Charcoal = Color(0xFF121212)
val Card = Color(0xFF1E1E1E)
val Muted = Color(0xFF9E9E9E)

@Composable
fun RowScope.NavItem(
    key: String,
    icon: ImageVector,
    label: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    NavigationBarItem(
        selected = selected == key,
        onClick = { onSelect(key) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Green,
            selectedTextColor = Green,
            indicatorColor = Color.Transparent,
            unselectedIconColor = Muted,
            unselectedTextColor = Muted
        )
    )
}
