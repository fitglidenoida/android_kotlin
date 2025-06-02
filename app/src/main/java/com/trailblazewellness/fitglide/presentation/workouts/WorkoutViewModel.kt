package com.trailblazewellness.fitglide.presentation.workouts

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
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

class WorkoutViewModel(
    private val strapiRepository: StrapiRepository,
    private val healthConnectManager: HealthConnectManager,
    private val homeViewModel: HomeViewModel,
    private val commonViewModel: CommonViewModel,
    private val authToken: String,
    private val userId: String
) : ViewModel() {
    fun getAuthToken(): String = authToken
    fun getUserId(): String = userId
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
            selectedGoal = "Cardio",
            plans = emptyList()
        )
    )
    val workoutData: StateFlow<WorkoutUiData> = _workoutData.asStateFlow()

    private val _workoutLog = MutableStateFlow<WorkoutLog?>(null)
    val workoutLog: StateFlow<WorkoutLog?> = _workoutLog.asStateFlow()

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
                val planData = plansResponse.body()?.data ?: emptyList()
                Log.d("WorkoutDebug", "Workout plans fetched: ${planData.size} plans")
                planData.map { plan ->
                    val exercises = plan.exercises ?: emptyList()
                    Log.d("WorkoutDebug", "Plan ${plan.id}: ${plan.title}, exercises=${exercises.size}, exercise_order=${plan.exerciseOrder}, exercise_details=${exercises.map { it.name }}")
                    val moves = exercises.mapNotNull { exercise ->
                        if (exercise.name.isNullOrBlank()) {
                            Log.w("WorkoutDebug", "Skipping exercise with null/blank name for plan ${plan.id}: $exercise")
                            null
                        } else {
                            WorkoutMove(
                                name = exercise.name,
                                repsOrTime = buildRepsOrTime(exercise),
                                sets = exercise.sets ?: 0,
                                isCompleted = false
                            )
                        }
                    }
                    WorkoutSlot(
                        id = plan.id,
                        type = plan.sportType,
                        time = if (plan.totalTimePlanned > 0) "${plan.totalTimePlanned} min" else "N/A",
                        moves = moves,
                        date = _workoutData.value.selectedDate,
                        isCompleted = plan.completed
                    )
                }.filter { it.type.isNotBlank() }
            } else {
                Log.e("WorkoutDebug", "Failed to fetch plans: ${plansResponse.code()} - ${plansResponse.errorBody()?.string()}")
                emptyList()
            }

            // Fetch workout logs from Strapi
            val dateStr = _workoutData.value.selectedDate.toString()
            val logsResponse = strapiRepository.getWorkoutLogs(userId, dateStr, authToken)
            val logs = if (logsResponse.isSuccessful) {
                val logData = logsResponse.body()?.data?.filter { it.completed } ?: emptyList() // Only completed logs
                Log.d("WorkoutDebug", "Workout logs fetched: ${logData.size} logs for $dateStr, logs=$logData")
                logData
            } else {
                Log.e("WorkoutDebug", "Failed to fetch logs: ${logsResponse.code()} - ${logsResponse.errorBody()?.string()}")
                emptyList()
            }

            // Fetch sessions from Health Connect
            val sessions = healthConnectManager.readExerciseSessions(_workoutData.value.selectedDate)
            val schedule = (plans.filter { it.isCompleted } + logs.filter { it.workout == null && it.startTime.startsWith(dateStr) }.map { log ->
                WorkoutSlot(
                    id = log.id,
                    type = if (log.notes?.contains("Cycling") == true) "Cardio" else "Cardio",
                    time = "${log.totalTime ?: 0f} min",
                    moves = listOf(
                        WorkoutMove(
                            name = "Wearable Session (${log.notes?.substringAfter("wearable (")?.substringBefore(")") ?: "Unknown"})",
                            repsOrTime = "${log.distance ?: 0f} miles",
                            sets = 0,
                            isCompleted = log.completed
                        )
                    ),
                    date = _workoutData.value.selectedDate,
                    isCompleted = log.completed
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
                insights = insights,
                plans = plans
            )
            Log.d("WorkoutDebug", "Updated workoutData: plans.size=${plans.size}, schedule.size=${schedule.size}, heartRate=$heartRate, calories=$caloriesBurned, steps=$steps, moves=${plans.map { "${it.id}: ${it.moves.map { move -> move.name }}" }}")
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

    fun toggleMove(workoutId: String, moveIndex: Int) {
        viewModelScope.launch {
            val plans = _workoutData.value.plans.toMutableList()
            val planIndex = plans.indexOfFirst { it.id == workoutId }
            if (planIndex == -1) {
                Log.w("WorkoutDebug", "Plan not found: $workoutId")
                return@launch
            }
            val plan = plans[planIndex]
            val moves = plan.moves.toMutableList()
            if (moveIndex >= moves.size) {
                Log.w("WorkoutDebug", "Invalid moveIndex: $moveIndex, moves.size=${moves.size}")
                return@launch
            }
            moves[moveIndex] = moves[moveIndex].copy(isCompleted = !moves[moveIndex].isCompleted)
            plans[planIndex] = plan.copy(moves = moves)

            // Check if all moves are completed
            val isWorkoutCompleted = moves.all { it.isCompleted }
            plans[planIndex] = plans[planIndex].copy(isCompleted = isWorkoutCompleted)

            _workoutData.value = _workoutData.value.copy(plans = plans)

            if (isWorkoutCompleted) {
                syncWorkoutLog(plans[planIndex])
                updateWorkoutCompletion(plans[planIndex])
            }
        }
    }

    fun startWorkout(workoutId: String, friendIds: List<String>, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val plans = _workoutData.value.plans
                val plan = plans.find { it.id == workoutId } ?: run {
                    onResult(false, "Workout not found")
                    return@launch
                }
                Log.d("WorkoutDebug", "Starting workout: $workoutId, sharing with friends: $friendIds")

                // Validate friend IDs
                val validFriendIds = mutableListOf<String>()
                val friendsResponse = strapiRepository.getFriends(mapOf("filters[receiver][id][\$in]" to friendIds.joinToString(",")), authToken)
                if (friendsResponse.isSuccessful) {
                    val friends = friendsResponse.body()?.data ?: emptyList()
                    validFriendIds.addAll(friendIds.filter { id ->
                        friends.any { it.receiver?.data?.id == id && it.friendsStatus == "Accepted" }
                    })
                    Log.d("WorkoutDebug", "Valid friend IDs: $validFriendIds")
                } else {
                    Log.e("WorkoutDebug", "Failed to fetch friends: ${friendsResponse.errorBody()?.string()}")
                    onResult(false, "Failed to validate friends")
                    return@launch
                }

                // Create a post to share the workout start
                val postRequest = StrapiApi.PostRequest(
                    user = StrapiApi.UserId(userId),
                    pack = null,
                    type = "live",
                    data = mapOf(
                        "workoutId" to workoutId,
                        "title" to plan.type,
                        "timestamp" to LocalDateTime.now().toString()
                    )
                )
                val postResponse = strapiRepository.postPost(postRequest, authToken)
                if (postResponse.isSuccessful) {
                    Log.d("WorkoutDebug", "Workout start shared: ${postResponse.body()}")
                    // Send cheers to valid friends
                    validFriendIds.forEach { friendId ->
                        val cheerRequest = StrapiApi.CheerRequest(
                            sender = StrapiApi.UserId(userId),
                            receiver = StrapiApi.UserId(friendId),
                            message = "Started workout: ${plan.type}!"
                        )
                        val cheerResponse = strapiRepository.postCheer(cheerRequest, authToken)
                        Log.d("WorkoutDebug", "Cheer sent to $friendId: success=${cheerResponse.isSuccessful}")
                    }
                    onResult(true, "Workout started!")
                } else {
                    val errorMessage = postResponse.errorBody()?.string() ?: "Unknown error"
                    Log.e("WorkoutDebug", "Failed to share workout start: $errorMessage")
                    onResult(false, "Failed to start workout: $errorMessage")
                }
            } catch (e: Exception) {
                Log.e("WorkoutDebug", "Error starting workout: ${e.message}", e)
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    private suspend fun syncWorkoutLog(workout: WorkoutSlot) {
        try {
            val logRequest = StrapiApi.WorkoutLogRequest(
                logId = "log_${workout.id}_${System.currentTimeMillis()}",
                workout = StrapiApi.UserId(workout.id),
                startTime = LocalDateTime.now().minusMinutes(30).toString(),
                endTime = LocalDateTime.now().toString(),
                distance = 0f,
                totalTime = workout.time.split(" ")[0].toFloatOrNull() ?: 0f,
                calories = estimateCalories(workout),
                heartRateAverage = 0L,
                heartRateMaximum = 0L,
                heartRateMinimum = 0L,
                route = emptyList(),
                completed = true,
                notes = "Completed gym workout (${workout.type})",
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

    private suspend fun updateWorkoutCompletion(workout: WorkoutSlot) {
        try {
            val workoutRequest = StrapiApi.WorkoutRequest(
                workoutId = workout.id,
                title = "", // Not updating title
                description = null,
                distancePlanned = 0f,
                totalTimePlanned = workout.time.split(" ")[0].toFloatOrNull() ?: 0f,
                caloriesPlanned = 0f,
                sportType = workout.type,
                exercises = workout.moves.map { StrapiApi.ExerciseId(it.name) }, // Simplified; adjust if exercise IDs are stored differently
                exerciseOrder = workout.moves.map { it.name },
                isTemplate = false,
                usersPermissionsUser = StrapiApi.UserId(userId),
                completed = true
            )
            Log.d("WorkoutDebug", "Syncing workout completion: $workoutRequest")
            val response = strapiRepository.syncWorkoutPlan(
                workoutId = workout.id,
                title = "", // Not updating title
                description = null,
                distancePlanned = 0f,
                totalTimePlanned = workout.time.split(" ")[0].toFloatOrNull() ?: 0f,
                caloriesPlanned = 0f,
                sportType = workout.type,
                weekNumber = 0, // Assuming weekNumber is not critical for completion
                exercises = workout.moves.map { StrapiApi.ExerciseId(it.name) }, // Simplified
                exerciseOrder = workout.moves.map { it.name },
                isTemplate = false,
                token = authToken
            )
            Log.d("WorkoutDebug", "Workout completion sync response: success=${response.isSuccessful}, code=${response.code()}, body=${response.body()?.toString()}")
            if (!response.isSuccessful) {
                Log.e("WorkoutDebug", "Failed to sync workout completion: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutDebug", "Error syncing workout completion: ${e.message}", e)
        }
    }

    private fun buildRepsOrTime(exercise: StrapiApi.ExerciseEntry): String {
        val parts = mutableListOf<String>()
        exercise.reps?.let { parts.add("$it reps") }
        exercise.sets?.let { parts.add("$it sets") }
        return parts.joinToString(", ").ifEmpty { exercise.duration?.let { "$it min" } ?: "N/A" }
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