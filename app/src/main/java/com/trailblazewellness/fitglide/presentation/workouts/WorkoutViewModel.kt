package com.trailblazewellness.fitglide.presentation.workouts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.healthconnect.WorkoutData
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
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
    private val commonViewModel: CommonViewModel,
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
            challenges = emptyList(),
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
                fetchWorkoutData()
                // Start a background coroutine to periodically fetch steps
                launch {
                    while (true) {
                        val steps = healthConnectManager.readSteps(_workoutData.value.selectedDate).toFloat()
                        Log.d("WorkoutDebug", "Periodically fetched steps for ${_workoutData.value.selectedDate}: $steps")
                        _workoutData.value = _workoutData.value.copy(steps = steps)
                        delay(5000L) // Fetch every 5 seconds
                    }
                }
            }
        }
    }

    private suspend fun fetchWorkoutData() {
        Log.d("WorkoutDebug", "Fetching workout data for date: ${_workoutData.value.selectedDate}, goal: ${_workoutData.value.selectedGoal}")
        delay(500)
        try {
            // Fetch steps for selected date
            val steps = healthConnectManager.readSteps(_workoutData.value.selectedDate).toFloat()
            Log.d("WorkoutDebug", "Fetched steps for ${_workoutData.value.selectedDate}: $steps")

            // Fetch workout plans from Strapi
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

            // Fetch workout logs from Strapi
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

            // Fetch sessions from Health Connect
            val sessions = healthConnectManager.readExerciseSessions(_workoutData.value.selectedDate)
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

            // Aggregate heart rate and calories from all sessions
            val heartRate = logs.mapNotNull { it.heartRateAverage?.toFloat() }.average().toFloat().takeIf { it > 0 }
                ?: sessions.mapNotNull { it.heartRateAvg?.toFloat() }.average().toFloat().takeIf { it > 0 } ?: 0f
            val caloriesBurned = logs.sumOf { it.calories?.toDouble() ?: 0.0 }.toFloat().takeIf { it > 0 }
                ?: sessions.sumOf { it.calories ?: 0.0 }.toFloat()
            val streak = calculateStreak(logs)

            // Fetch challenges from CommonViewModel
            val challenges = commonViewModel.challenges.value.map { challenge ->
                Challenge(
                    goal = challenge.type,
                    progress = logs.count { it.completed && it.startTime.startsWith(dateStr) },
                    target = challenge.goal
                )
            }

            // Generate dynamic insights
            val homeData = homeViewModel.homeData.value
            val insights = mutableListOf<String>()
            val targetHR = (homeData.maxHeartRate * 0.65).toInt()
            if (heartRate < targetHR) {
                insights.add("Push HR to $targetHR for max burn!")
            }
            if (steps < homeData.stepGoal) {
                insights.add("Steps on track—aim for ${homeData.stepGoal.toInt()}!")
            } else {
                insights.add("Steps goal achieved—great job!")
            }
            if (caloriesBurned < 300) {
                insights.add("Burn more calories—aim for at least 300 cal today!")
            }
            if (logs.count { it.completed && it.startTime.startsWith(dateStr) } < 1) {
                insights.add("Consistency is key—try a workout today!")
            }

            _workoutData.value = _workoutData.value.copy(
                heartRate = heartRate,
                caloriesBurned = caloriesBurned,
                steps = steps,
                schedule = schedule,
                streak = streak,
                challenges = challenges,
                insights = insights
            )
            Log.d("WorkoutDebug", "Updated workoutData: schedule.size=${schedule.size}, heartRate=$heartRate, calories=$caloriesBurned, steps=$steps")
        } catch (e: Exception) {
            Log.e("WorkoutDebug", "Error fetching workout data: ${e.message ?: "Unknown error"}", e)
        }
    }

    fun setDate(date: LocalDate) {
        _workoutData.value = _workoutData.value.copy(selectedDate = date)
        viewModelScope.launch {
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
        val HEART_RATE_THRESHOLD = 85f
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
    val challenges: List<Challenge>,
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

data class Challenge(
    val goal: String,
    val progress: Int,
    val target: Int
)