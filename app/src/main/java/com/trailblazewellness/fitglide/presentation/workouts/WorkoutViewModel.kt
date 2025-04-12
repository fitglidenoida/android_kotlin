package com.trailblazewellness.fitglide.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
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
    private val authToken: String,
    private val userId: String
) : ViewModel() {

    private val _workoutData = MutableStateFlow(
        WorkoutData(
            heartRate = 0f,
            caloriesBurned = 0f,
            steps = 0f,
            schedule = emptyList(),
            streak = 0,
            dailyChallenge = "Hit 3 workouts",
            challengeProgress = 0,
            buddyChallenges = emptyList(),
            selectedDate = LocalDate.now(),
            selectedGoal = "Strength"
        )
    )
    val workoutData: StateFlow<WorkoutData> = _workoutData.asStateFlow()

    init {
        viewModelScope.launch {
            syncWearableWorkouts() // Auto-sync wearables first
            fetchWorkoutData()     // Then fetch plans/logs
            homeViewModel.homeData.collect { homeData ->
                _workoutData.value = _workoutData.value.copy(
                    steps = homeData.watchSteps + homeData.manualSteps
                )
            }
        }
    }

    private suspend fun syncWearableWorkouts() {
        val date = workoutData.value.selectedDate
        val session = healthConnectManager.readExerciseSessions(date)
            .takeIf { it.distance != null || it.calories != null }
        if (session != null && isWearableTrackable(session.type)) {
            val logRequest = StrapiApi.WorkoutLogRequest(
                logId = "wearable_${date}_${System.currentTimeMillis()}",
                workout = StrapiApi.UserId("0"), // Placeholder—standalone log
                startTime = session.start?.toString() ?: "${date}T00:00:00.000Z",
                endTime = session.end?.toString() ?: "${date}T23:59:59.000Z",
                distance = session.distance?.toFloat() ?: 0f,
                totalTime = session.duration?.toMinutes()?.toFloat() ?: 0f,
                calories = session.calories?.toFloat() ?: 0f,
                heartRateAverage = session.heartRateAvg ?: 0L,
                heartRateMaximum = 0L,
                heartRateMinimum = 0L,
                route = emptyList(),
                completed = true,
                notes = "Auto-synced from wearable",
                usersPermissionsUser = StrapiApi.UserId(userId)
            )
            strapiRepository.syncWorkoutLog(logRequest, authToken)
        }
    }

    private suspend fun fetchWorkoutData() {
        delay(500)
        val plansResponse = strapiRepository.getWorkoutPlans(userId, authToken)
        val plans = if (plansResponse.isSuccessful) {
            plansResponse.body()?.data?.map { plan ->
                WorkoutSlot(
                    id = plan.id,
                    type = plan.sportType,
                    time = if (plan.totalTimePlanned > 0) "${plan.totalTimePlanned} min" else "N/A", // Show "N/A" for 0
                    moves = plan.exercises?.map {
                        WorkoutMove(
                            name = it.name,
                            repsOrTime = it.reps?.toString() ?: "${it.duration} min",
                            isCompleted = false
                        )
                    } ?: emptyList(),
                    date = workoutData.value.selectedDate
                )
            }?.filter { it.type.isNotBlank() } ?: emptyList() // Keep if type exists
        } else emptyList()

        val dateStr = workoutData.value.selectedDate.toString()
        val logsResponse = strapiRepository.getWorkoutLogs(userId, dateStr, authToken)
        val logs = if (logsResponse.isSuccessful) logsResponse.body()?.data ?: emptyList() else emptyList()

        val schedule = (plans + logs.filter { it.workout == null }.map { log ->
            WorkoutSlot(
                id = log.id,
                type = "Cardio", // Default—adjust based on wearable data later
                time = "${log.totalTime ?: 0f} min",
                moves = listOf(WorkoutMove("Wearable Session", "${log.distance ?: 0f} km", true)),
                date = workoutData.value.selectedDate
            )
        }).map { slot ->
            val log = logs.find { it.workout?.id == slot.id }
            slot.copy(moves = slot.moves.map { move ->
                move.copy(isCompleted = log?.completed ?: move.isCompleted)
            })
        }.filter { it.type == workoutData.value.selectedGoal || workoutData.value.selectedGoal.isEmpty() }

        val heartRate = logs.mapNotNull { it.heartRateAverage?.toFloat() }.average().toFloat().takeIf { it > 0 } ?: 0f
        val caloriesBurned = logs.sumOf { it.calories?.toDouble() ?: 0.0 }.toFloat()
        val streak = calculateStreak(logs)

        _workoutData.value = _workoutData.value.copy(
            heartRate = heartRate,
            caloriesBurned = caloriesBurned,
            schedule = schedule,
            streak = streak,
            challengeProgress = logs.count { it.completed }
        )
    }

    fun setDate(date: LocalDate) {
        _workoutData.value = _workoutData.value.copy(selectedDate = date)
        viewModelScope.launch {
            syncWearableWorkouts()
            fetchWorkoutData()
        }
    }

    fun setGoal(goal: String) {
        _workoutData.value = _workoutData.value.copy(selectedGoal = goal)
        viewModelScope.launch { fetchWorkoutData() }
    }

    fun toggleMove(workoutIndex: Int, moveIndex: Int) {
        val schedule = _workoutData.value.schedule.toMutableList()
        val workout = schedule[workoutIndex]
        val moves = workout.moves.toMutableList()
        moves[moveIndex] = moves[moveIndex].copy(isCompleted = !moves[moveIndex].isCompleted)
        schedule[workoutIndex] = workout.copy(moves = moves)
        _workoutData.value = _workoutData.value.copy(schedule = schedule)

        if (schedule[workoutIndex].moves.all { it.isCompleted } && !isWearableTrackable(workout.type)) {
            viewModelScope.launch { syncWorkoutLog(workoutIndex) }
        }
    }

    private suspend fun syncWorkoutLog(workoutIndex: Int) {
        val workout = _workoutData.value.schedule[workoutIndex]
        val logRequest = StrapiApi.WorkoutLogRequest(
            logId = "log_${workout.id}_${System.currentTimeMillis()}",
            workout = StrapiApi.UserId(workout.id),
            startTime = LocalDateTime.now().minusMinutes(30).toString(),
            endTime = LocalDateTime.now().toString(),
            distance = 0f,
            totalTime = workout.time.split(" ")[0].toFloat(),
            calories = estimateCalories(workout),
            heartRateAverage = 0L,
            heartRateMaximum = 0L,
            heartRateMinimum = 0L,
            route = emptyList(),
            completed = true,
            notes = "Manual gym workout",
            usersPermissionsUser = StrapiApi.UserId(userId)
        )
        strapiRepository.syncWorkoutLog(logRequest, authToken)
        fetchWorkoutData()
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


// Data Classes (unchanged)
data class WorkoutData(
    val heartRate: Float,
    val caloriesBurned: Float,
    val steps: Float,
    val schedule: List<WorkoutSlot>,
    val streak: Int,
    val dailyChallenge: String,
    val challengeProgress: Int,
    val buddyChallenges: List<BuddyChallenge>,
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