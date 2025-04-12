package com.trailblazewellness.fitglide.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickLinks() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickLinkButton("Dumbbell", "Workouts")
        QuickLinkButton("Fork", "Meals")
        QuickLinkButton("Moon", "Sleep")
    }
}

@Composable
fun QuickLinkButton(icon: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f) // Gray tint
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    icon,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground // Theme-aware
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground, // Theme-aware
            style = MaterialTheme.typography.bodySmall
        )
    }
}