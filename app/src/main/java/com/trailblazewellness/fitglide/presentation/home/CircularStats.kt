package com.trailblazewellness.fitglide.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailblazewellness.fitglide.data.HealthStats

@Composable
fun CircularStats(stats: HealthStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularStat(
            title = "Sleep",
            value = stats.sleep.split("/")[0],
            color = MaterialTheme.colorScheme.secondary,
            size = 80.dp
        )
        CircularStat(
            title = "Steps",
            value = stats.steps.split("/")[0],
            color = MaterialTheme.colorScheme.primary,
            size = 100.dp // Bigger central circle
        )
        CircularStat(
            title = "Calories",
            value = stats.calories,
            color = MaterialTheme.colorScheme.tertiary,
            size = 80.dp
        )
    }
}

@Composable
fun CircularStat(title: String, value: String, color: androidx.compose.ui.graphics.Color, size: Dp = 80.dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(color.copy(alpha = 0.2f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                fontSize = if (size > 80.dp) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall
        )
    }
}