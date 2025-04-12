package com.trailblazewellness.fitglide.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailblazewellness.fitglide.data.HealthStats
import com.trailblazewellness.fitglide.data.healthconnect.WorkoutData

@Composable
fun DailyStats(stats: HealthStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard(
                title = "Steps",
                value = stats.steps,
                color = MaterialTheme.colorScheme.primary
            )
            StatCard(
                title = "Sleep",
                value = stats.sleep,
                color = MaterialTheme.colorScheme.secondary
            )
            StatCard(
                title = "Calories",
                value = stats.calories,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard(
                title = "Water",
                value = stats.water,
                color = MaterialTheme.colorScheme.primary.copy(blue = 0.8f),
                size = 80.dp
            )
            WorkoutStatCard( // New: Specialized card for WorkoutData
                title = "Workouts",
                workout = stats.workouts,
                color = MaterialTheme.colorScheme.secondary.copy(green = 0.6f),
                size = 80.dp
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, color: androidx.compose.ui.graphics.Color, size: Dp = 100.dp) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(size),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
fun WorkoutStatCard(title: String, workout: WorkoutData, color: androidx.compose.ui.graphics.Color, size: Dp = 100.dp) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(size),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (workout.type != "None") "${workout.type} ${workout.duration?.toMinutes() ?: 0}m"
                else "None",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}