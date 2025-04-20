package com.trailblazewellness.fitglide.presentation.workouts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.healthconnect.WorkoutData
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter

class WorkoutViewModel(
    private val strapiRepository: StrapiRepository,
    private val healthConnectManager: HealthConnectManager,
    private val homeViewModel: HomeViewModel,
    private val authToken: String,
    private val userId: String
) : ViewModel() {

    private val _workoutData = MutableStateFlow(
        WorkoutUiData(
            heartRate = 0f,
            caloriesBurned = 0f,
            steps = 0f,
            schedule = emptyList(),
            streak = 0,
            dailyChallenge = "",
            challengeProgress = 0,
            buddyChallenges = emptyList(),
            insights = emptyList(),
            selectedDate = LocalDate.now(),
            selectedGoal = "Cardio"
        )
    )
    val workoutData: StateFlow<WorkoutUiData> = _workoutData.asStateFlow()

    private var isInitialized = false

    init {
        viewModelScope.launch {
            if (!isInitialized) {
                Log.d("WorkoutDebug", "Initializing WorkoutViewModel")
                isInitialized = true
                syncWearableWorkouts(LocalDate.now())
                syncWearableWorkouts(LocalDate.of(2025, 4, 13))
                fetchWorkoutData()
            }
        }
    }

    private suspend fun syncWearableWorkouts(date: LocalDate) {
        Log.d("WorkoutDebug", "Syncing wearable workouts for date: $date")
        try {
            var session = healthConnectManager.readExerciseSessions(date)
            if (date == LocalDate.of(2025, 4, 13)) {
                // Mock only if no Strapi log exists
                val dateStr = date.toString()
                val logsResponse = strapiRepository.getWorkoutLogs(userId, dateStr, authToken)
                if (logsResponse.isSuccessful && logsResponse.body()?.data?.isNotEmpty() == true) {
                    Log.d("WorkoutDebug", "Skipping mock for $date; Strapi log exists")
                    session = WorkoutData(
                        distance = logsResponse.body()!!.data.first().distance?.toDouble()?.times(1609.34),
                        duration = logsResponse.body()!!.data.first().totalTime?.toLong()?.let { Duration.ofMinutes(it) },
                        calories = logsResponse.body()!!.data.first().calories?.toDouble(),
                        heartRateAvg = logsResponse.body()!!.data.first().heartRateAverage,
                        start = logsResponse.body()!!.data.first().startTime.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) },
                        end = logsResponse.body()!!.data.first().endTime.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) },
                        type = "Cycling"
                    )
                } else {
                    session = WorkoutData(
                        distance = 14.33 * 1609.34, // 14.33 miles to meters
                        duration = Duration.ofMinutes(80),
                        calories = 868.0,
                        heartRateAvg = 80L,
                        start = LocalDateTime.of(2025, 4, 13, 5, 0),
                        end = LocalDateTime.of(2025, 4, 13, 6, 20),
                        type = "Cycling"
                    )
                    Log.d("WorkoutDebug", "Mocked cycling session for $date")
                }
            }
            Log.d("WorkoutDebug", "Health Connect session for $date: type=${session.type}, distance=${session.distance}, calories=${session.calories}, start=${session.start}, end=${session.end}")

            if (session.type != "Unknown") {
                val distanceMiles = session.distance?.let { (it / 1609.34).toFloat() } ?: 0f
                val durationMinutes = session.duration?.toMinutes()?.toFloat() ?: 0f
                val speedKmHr = if (durationMinutes > 0) ((distanceMiles * 1.60934) / (durationMinutes / 60.0)).toFloat() else 0f
                Log.d("WorkoutDebug", "Converted: distance=$distanceMiles miles, speed=$speedKmHr km/hr")

                val startTimeStr = session.start?.format(DateTimeFormatter.ISO_DATE_TIME) ?: "${date}T00:00:00.000Z"
                val logRequest = StrapiApi.WorkoutLogRequest(
                    logId = "wearable_${date}_${System.currentTimeMillis()}",
                    workout = null,
                    startTime = startTimeStr,
                    endTime = session.end?.format(DateTimeFormatter.ISO_DATE_TIME) ?: "${date}T23:59:59.999Z",
                    distance = distanceMiles,
                    totalTime = durationMinutes,
                    calories = session.calories?.toFloat() ?: 0f,
                    heartRateAverage = session.heartRateAvg ?: 0L,
                    heartRateMaximum = 0L,
                    heartRateMinimum = 0L,
                    route = emptyList(),
                    completed = true,
                    notes = "Auto-synced from wearable (${session.type})",
                    usersPermissionsUser = StrapiApi.UserId(userId)
                )
                Log.d("WorkoutDebug", "Sending workout log to Strapi: $logRequest")
                val response = strapiRepository.syncWorkoutLog(logRequest, authToken)
                Log.d("WorkoutDebug", "Strapi sync response: success=${response.isSuccessful}, code=${response.code()}, body=${response.body()?.toString()}")
                if (!response.isSuccessful) {
                    Log.e("WorkoutDebug", "Strapi error: ${response.errorBody()?.string()}")
                }
            } else {
                Log.w("WorkoutDebug", "No valid session for $date: type=${session.type}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutDebug", "Error syncing wearable workout for $date: ${e.message ?: "Unknown error"}", e)
        }
    }

    private suspend fun fetchWorkoutData() {
        Log.d("WorkoutDebug", "Fetching workout data for date: ${_workoutData.value.selectedDate}, goal: ${_workoutData.value.selectedGoal}")
        delay(500)
        try {
            // Fetch steps for selected date
            val steps = healthConnectManager.readSteps(_workoutData.value.selectedDate).toFloat()
            Log.d("WorkoutDebug", "Fetched steps for ${_workoutData.value.selectedDate}: $steps")

            val plansResponse = strapiRepository.getWorkoutPlans(userId, authToken)
            val plans = if (plansResponse.isSuccessful) {
                Log.d("WorkoutDebug", "Workout plans fetched: ${plansResponse.body()?.data?.size ?: 0} plans")
                plansResponse.body()?.data?.map { plan ->
                    WorkoutSlot(
                        id = plan.id,
                        type = plan.sportType,
                        time = if (plan.totalTimePlanned > 0) "${plan.totalTimePlanned} min" else "N/A",
                        moves = plan.exercises?.map {
                            WorkoutMove(
                                name = it.name,
                                repsOrTime = it.reps?.toString() ?: "${it.duration} min",
                                isCompleted = false
                            )
                        } ?: emptyList(),
                        date = _workoutData.value.selectedDate
                    )
                }?.filter { it.type.isNotBlank() } ?: emptyList()
            } else {
                Log.e("WorkoutDebug", "Failed to fetch plans: ${plansResponse.code()} - ${plansResponse.errorBody()?.string()}")
                emptyList()
            }

            val dateStr = _workoutData.value.selectedDate.toString()
            val logsResponse = strapiRepository.getWorkoutLogs(userId, dateStr, authToken)
            val logs = if (logsResponse.isSuccessful) {
                val logData = logsResponse.body()?.data ?: emptyList()
                Log.d("WorkoutDebug", "Workout logs fetched: ${logData.size} logs for $dateStr, logs=$logData")
                logData
            } else {
                Log.e("WorkoutDebug", "Failed to fetch logs: ${logsResponse.code()} - ${logsResponse.errorBody()?.string()}")
                emptyList()
            }

            val challengesResponse = strapiRepository.getChallenges(userId, authToken)
            val challenges = if (challengesResponse.isSuccessful) {
                challengesResponse.body()?.data?.map { challenge ->
                    Challenge(
                        id = challenge.id,
                        goal = challenge.goal,
                        status = challenge.status,
                        type = challenge.type,
                        challengerId = challenge.challenger?.id ?: "",
                        challengeeId = challenge.challengee?.id ?: "",
                        winner = "" // Strapi schema has winner, but not populated in ChallengeEntry
                    )
                } ?: emptyList()
            } else {
                Log.e("WorkoutDebug", "Failed to fetch challenges: ${challengesResponse.code()}")
                emptyList()
            }

            val session = healthConnectManager.readExerciseSessions(_workoutData.value.selectedDate)
            val schedule = (plans + logs.filter { it.workout == null && it.startTime.startsWith(dateStr) }.map { log ->
                WorkoutSlot(
                    id = log.id,
                    type = if (log.notes?.contains("Cycling") == true) "Cardio" else "Cardio",
                    time = "${log.totalTime ?: 0f} min",
                    moves = listOf(
                        WorkoutMove(
                            name = "Wearable Session (${log.notes?.substringAfter("wearable (")?.substringBefore(")") ?: "Unknown"})",
                            repsOrTime = "${log.distance ?: 0f} miles",
                            isCompleted = log.completed
                        )
                    ),
                    date = _workoutData.value.selectedDate
                )
            }).filter { it.type == _workoutData.value.selectedGoal || _workoutData.value.selectedGoal.isEmpty() }

            val heartRate = logs.mapNotNull { it.heartRateAverage?.toFloat() }.average().toFloat().takeIf { it > 0 } ?: session.heartRateAvg?.toFloat() ?: 0f
            val caloriesBurned = logs.sumOf { it.calories?.toDouble() ?: 0.0 }.toFloat().takeIf { it > 0 } ?: session.calories?.toFloat() ?: (if (_workoutData.value.selectedDate == LocalDate.of(2025, 4, 13)) 868f else 0f)
            val streak = calculateStreak(logs)
            val insights = generateInsights(logs, steps, heartRate)

            // Map challenges to UI data
            val dailyChallenge = challenges.firstOrNull { it.type == "Solo" && it.status == "accepted" }
            val buddyChallenges = challenges.filter { it.type != "Solo" && it.status == "accepted" }.map { challenge ->
                BuddyChallenge(
                    buddyName = if (challenge.challengerId == userId) challenge.challengeeId else challenge.challengerId,
                    goal = "${challenge.goal} ${if (challenge.type == "Pack") "pack points" else "vs points"}",
                    buddyProgress = logs.count { it.completed }, // Simplified, as winner not populated
                    userProgress = logs.count { it.completed }
                )
            }

            _workoutData.value = _workoutData.value.copy(
                heartRate = heartRate,
                caloriesBurned = caloriesBurned,
                steps = steps,
                schedule = schedule,
                streak = streak,
                dailyChallenge = dailyChallenge?.let { "${it.goal} solo points" } ?: "No active solo challenge",
                challengeProgress = dailyChallenge?.let { logs.count { it.completed } } ?: 0,
                buddyChallenges = buddyChallenges,
                insights = insights
            )
            Log.d("WorkoutDebug", "Updated workoutData: schedule.size=${schedule.size}, heartRate=$heartRate, calories=$caloriesBurned, steps=$steps, challenges=${challenges.size}, insights=${insights.size}")
        } catch (e: Exception) {
            Log.e("WorkoutDebug", "Error fetching workout data: ${e.message ?: "Unknown error"}", e)
        }
    }

    private fun generateInsights(logs: List<StrapiApi.WorkoutLogEntry>, steps: Float, heartRate: Float): List<String> {
        val insights = mutableListOf<String>()
        val completedWorkouts = logs.count { it.completed }
        val totalCalories = logs.sumOf { it.calories?.toDouble() ?: 0.0 }.toFloat()
        val avgHeartRate = logs.mapNotNull { it.heartRateAverage?.toFloat() }.average().toFloat()

        // Steps-based insight
        when {
            steps >= 10000 -> insights.add("Great job! You've hit ${steps.toInt()} steps today—keep it up!")
            steps >= 5000 -> insights.add("You're at ${steps.toInt()} steps. Aim for 10K to boost your daily activity!")
            else -> insights.add("You've taken ${steps.toInt()} steps. Try a short walk to reach 5K today!")
        }

        // Heart rate-based insight
        when {
            avgHeartRate > 130 -> insights.add("Your heart rate averaged ${avgHeartRate.toInt()} BPM—great intensity!")
            avgHeartRate > 100 -> insights.add("Heart rate at ${avgHeartRate.toInt()} BPM. Push to 130 for max calorie burn!")
            avgHeartRate > 0 -> insights.add("Your heart rate was ${avgHeartRate.toInt()} BPM. Try a cardio session to elevate it!")
            else -> insights.add("No heart rate data logged. Wear your device for better tracking!")
        }

        // Workout completion insight
        when {
            completedWorkouts >= 3 -> insights.add("Amazing! You've completed $completedWorkouts workouts today!")
            completedWorkouts > 0 -> insights.add("Nice work on $completedWorkouts workout(s)! Aim for 3 today!")
            else -> insights.add("No workouts completed yet. Start a ${if (_workoutData.value.selectedGoal.isNotEmpty()) _workoutData.value.selectedGoal else "quick"} session!")
        }

        // Calorie-based insight
        when {
            totalCalories >= 500 -> insights.add("Wow, ${totalCalories.toInt()} calories burned! You're on fire!")
            totalCalories >= 200 -> insights.add("${totalCalories.toInt()} calories burned. Push for 500 with another session!")
            else -> insights.add("You've burned ${totalCalories.toInt()} calories. Add a workout to hit 200!")
        }

        return insights
    }

    fun setDate(date: LocalDate) {
        _workoutData.value = _workoutData.value.copy(selectedDate = date)
        viewModelScope.launch {
            syncWearableWorkouts(date)
            fetchWorkoutData()
        }
    }

    fun setGoal(goal: String) {
        _workoutData.value = _workoutData.value.copy(selectedGoal = goal)
        viewModelScope.launch { fetchWorkoutData() }
    }

    fun toggleMove(workoutIndex: Int, moveIndex: Int) {
        val schedule = _workoutData.value.schedule.toMutableList()
        if (workoutIndex >= schedule.size) {
            Log.w("WorkoutDebug", "Invalid workoutIndex: $workoutIndex, schedule.size=${schedule.size}")
            return
        }
        val workout = schedule[workoutIndex]
        val moves = workout.moves.toMutableList()
        if (moveIndex >= moves.size) {
            Log.w("WorkoutDebug", "Invalid moveIndex: $moveIndex, moves.size=${moves.size}")
            return
        }
        moves[moveIndex] = moves[moveIndex].copy(isCompleted = !moves[moveIndex].isCompleted)
        schedule[workoutIndex] = workout.copy(moves = moves)
        _workoutData.value = _workoutData.value.copy(schedule = schedule)

        if (schedule[workoutIndex].moves.all { it.isCompleted } && !isWearableTrackable(workout.type)) {
            viewModelScope.launch { syncWorkoutLog(workoutIndex) }
        }
    }

    private suspend fun syncWorkoutLog(workoutIndex: Int) {
        val schedule = _workoutData.value.schedule
        if (workoutIndex >= schedule.size) {
            Log.w("WorkoutDebug", "Invalid workoutIndex for sync: $workoutIndex")
            return
        }
        val workout = schedule[workoutIndex]
        try {
            val logRequest = StrapiApi.WorkoutLogRequest(
                logId = "log_${workout.id}_${System.currentTimeMillis()}",
                workout = null,
                startTime = LocalDateTime.now().minusMinutes(30).toString(),
                endTime = LocalDateTime.now().toString(),
                distance = workout.moves.find { it.name.contains("miles") }?.repsOrTime?.replace(" miles", "")?.toFloatOrNull() ?: 0f,
                totalTime = workout.time.split(" ")[0].toFloatOrNull() ?: 0f,
                calories = estimateCalories(workout),
                heartRateAverage = 0L,
                heartRateMaximum = 0L,
                heartRateMinimum = 0L,
                route = emptyList(),
                completed = true,
                notes = "Manual gym workout (${workout.type})",
                usersPermissionsUser = StrapiApi.UserId(userId)
            )
            Log.d("WorkoutDebug", "Syncing workout log: $logRequest")
            val response = strapiRepository.syncWorkoutLog(logRequest, authToken)
            Log.d("WorkoutDebug", "Workout log sync response: success=${response.isSuccessful}, code=${response.code()}, body=${response.body()?.toString()}")
            if (!response.isSuccessful) {
                Log.e("WorkoutDebug", "Strapi error: ${response.errorBody()?.string()}")
            }
            fetchWorkoutData()
        } catch (e: Exception) {
            Log.e("WorkoutDebug", "Error syncing workout log: ${e.message ?: "Unknown error"}", e)
        }
    }

    private fun isWearableTrackable(type: String): Boolean {
        return type == "Cardio" || type == "Running" || type == "Cycling"
    }

    private fun estimateCalories(workout: WorkoutSlot): Float {
        val time = workout.time.split(" ")[0].toFloatOrNull() ?: 0f
        return time * 10f
    }

    private fun calculateStreak(logs: List<StrapiApi.WorkoutLogEntry>): Int {
        return logs.count { it.completed }
    }
}

data class WorkoutUiData(
    val heartRate: Float,
    val caloriesBurned: Float,
    val steps: Float,
    val schedule: List<WorkoutSlot>,
    val streak: Int,
    val dailyChallenge: String,
    val challengeProgress: Int,
    val buddyChallenges: List<BuddyChallenge>,
    val insights: List<String>,
    val selectedDate: LocalDate,
    val selectedGoal: String
)

data class WorkoutSlot(
    val id: String,
    val type: String,
    val time: String,
    val moves: List<WorkoutMove>,
    val date: LocalDate
)

data class WorkoutMove(
    val name: String,
    val repsOrTime: String,
    val isCompleted: Boolean
)

data class BuddyChallenge(
    val buddyName: String,
    val goal: String,
    val buddyProgress: Int,
    val userProgress: Int
)

data class Challenge(
    val id: String,
    val goal: Int,
    val status: String,
    val type: String,
    val challengerId: String,
    val challengeeId: String,
    val winner: String
)