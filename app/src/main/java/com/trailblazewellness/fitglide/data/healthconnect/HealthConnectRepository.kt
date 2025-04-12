package com.trailblazewellness.fitglide.data.healthconnect

import android.util.Log
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HealthConnectRepository(private val manager: HealthConnectManager) {
    suspend fun getSteps(date: LocalDate): Long {
        val steps = manager.readSteps(date) // Direct fetch from manager
        Log.d("HealthConnectRepository", "readSteps for $date: $steps")
        return steps
    }

    suspend fun getSleep(date: LocalDate): SleepData {
        val sleepData = manager.readSleepSessions(date) // Use LocalDate directly
        Log.d("HealthConnectRepository", "readSleepSessions for $date: $sleepData")
        return sleepData // Already computed in HealthConnectManager
    }

    suspend fun getWorkout(date: LocalDate): WorkoutData {
        val workoutData = manager.readExerciseSessions(date)
        Log.d("HealthConnectRepository", "readExerciseSessions for $date: $workoutData")

        // Fetch steps for distance estimate if workoutData.distance is null
        val steps = getSteps(date)
        val estimatedDistance = steps * 0.7 // Rough estimate: 0.7m per step

        return WorkoutData(
            distance = workoutData.distance ?: estimatedDistance.toDouble(),
            duration = workoutData.duration,
            calories = workoutData.calories ?: (workoutData.duration?.toMinutes()?.times(10)?.toDouble() ?: 0.0), // Estimate if null
            heartRateAvg = workoutData.heartRateAvg,
            start = workoutData.start,
            end = workoutData.end,
            type = workoutData.type
        ).also {
            Log.d("HealthConnectRepository", "Workout data for $date: $it")
        }
    }

    suspend fun getNutrition(date: LocalDate): NutritionData {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val nutritionRecords = manager.readNutritionRecords(start, end)
        Log.d("HealthConnectRepository", "readNutritionRecords: ${nutritionRecords.size} records")

        val calories = nutritionRecords.sumOf { it.energy?.inKilocalories ?: 0.0 }.toFloat()
        val protein = nutritionRecords.sumOf { it.protein?.inGrams ?: 0.0 }.toFloat()
        val carbs = nutritionRecords.sumOf { it.totalCarbohydrate?.inGrams ?: 0.0 }.toFloat()
        val fat = nutritionRecords.sumOf { it.totalFat?.inGrams ?: 0.0 }.toFloat()

        return NutritionData(
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat
        ).also {
            Log.d("HealthConnectRepository", "Nutrition data for $date: $it")
        }
    }
}

data class NutritionData(
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float
)