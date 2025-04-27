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
        val workoutSessions = manager.readExerciseSessions(date)
        Log.d("HealthConnectRepository", "readExerciseSessions for $date: $workoutSessions")

        // Fetch steps for distance estimate if no sessions are found or distance is null
        val steps = getSteps(date)
        val estimatedDistance = steps * 0.7 // Rough estimate: 0.7m per step

        // If no sessions are found, return a default WorkoutData
        if (workoutSessions.isEmpty()) {
            return WorkoutData(
                distance = estimatedDistance.toDouble(),
                duration = Duration.ZERO,
                calories = 0.0,
                heartRateAvg = 0L,
                start = null,
                end = null,
                type = "Unknown"
            ).also {
                Log.d("HealthConnectRepository", "No workout sessions found for $date, returning default: $it")
            }
        }

        // Select the most recent session (based on start time)
        val mostRecentSession = workoutSessions.maxByOrNull { it.start?.toEpochSecond(ZoneId.systemDefault().rules.getOffset(it.start)) ?: 0 }
            ?: workoutSessions.first()

        return WorkoutData(
            distance = mostRecentSession.distance ?: estimatedDistance.toDouble(),
            duration = mostRecentSession.duration ?: Duration.ZERO,
            calories = mostRecentSession.calories ?: (mostRecentSession.duration?.toMinutes()?.times(10)?.toDouble() ?: 0.0),
            heartRateAvg = mostRecentSession.heartRateAvg ?: 0L,
            start = mostRecentSession.start,
            end = mostRecentSession.end,
            type = mostRecentSession.type
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