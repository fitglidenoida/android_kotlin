package com.trailblazewellness.fitglide.data

import com.trailblazewellness.fitglide.data.healthconnect.WorkoutData

data class HealthStats(
    val steps: String = "0",
    val sleep: String = "0h",
    val calories: String = "0",
    val water: String = "0",
    val workouts: WorkoutData = WorkoutData(null, null, null, null, null, null, "None")
)