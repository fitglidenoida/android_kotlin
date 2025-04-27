package com.trailblazewellness.fitglide.presentation.workouts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    navController: NavController,
    workoutId: String,
    viewModel: WorkoutViewModel
) {
    val workoutData by viewModel.workoutData.collectAsState()
    val workout = workoutData.schedule.find { it.id == workoutId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (workout != null) {
                Text(
                    text = "${workout.type} Session",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Distance: ${workout.moves.firstOrNull()?.repsOrTime ?: "N/A"}")
                Text("Duration: ${workout.time}")
                Text("Calories: ${workoutData.caloriesBurned.toInt()} cal")
                Text("Heart Rate: ${workoutData.heartRate.toInt()} BPM")
                Text("Route: [Placeholder for route map]")
            } else {
                Text("Workout not found", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}