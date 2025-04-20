package com.trailblazewellness.fitglide.presentation.sleep

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.healthconnect.SleepData
import com.trailblazewellness.fitglide.data.healthconnect.WorkoutData
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

data class SleepDataUi(
    val score: Float = 85f,
    val debt: String = "0h0m",
    val injuryRisk: Float = 0f,
    val bedtime: String = "10:30 PM",
    val alarm: String = "7:00 AM",
    val stages: List<SleepStage> = emptyList(),
    val insights: List<String> = emptyList(),
    val streak: Int = 0,
    val challengeActive: Boolean = false,
    val restTime: Float = 8f,
    val actualSleepTime: Float = 0f
)

data class SleepStage(val duration: Int, val type: String)

class SleepViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val strapiRepository: StrapiRepository,
    private val authRepository: AuthRepository,
    private val commonViewModel: CommonViewModel
) : ViewModel() {
    private val _sleepData = MutableStateFlow(SleepDataUi())
    val sleepData: StateFlow<SleepDataUi> = _sleepData.asStateFlow()

    private val userId = authRepository.getAuthState().getId() ?: "4"
    private val token = "Bearer ${authRepository.getAuthState().jwt}"
    private val syncedDates = ConcurrentHashMap<LocalDate, Boolean>()

    init {
        viewModelScope.launch {
            commonViewModel.sleepData.debounce(1000).collectLatest { sleepData ->
                fetchAndSyncSleepData(LocalDate.now(), sleepData)
            }
        }
    }

    fun fetchAndSyncSleepData(date: LocalDate, healthConnectData: SleepData?) {
        viewModelScope.launch {
            try {
                // Skip if already synced for this date
                if (syncedDates[date] == true) {
                    Log.d("SleepViewModel", "Skipping sync for $date: already synced")
                    return@launch
                }

                // Fetch Strapi sleep log
                val existingLogsResponse = strapiRepository.fetchSleepLog(date)
                var totalSleepHours = 0f
                var stages = emptyList<SleepStage>()
                var bedtime = "10:30 PM"
                var alarm = "7:00 AM"
                var sleepDataHc = healthConnectData
                var isStrapiDataValid = false
                var documentId: String? = null

                if (existingLogsResponse.isSuccessful && existingLogsResponse.body()?.data?.isNotEmpty() == true) {
                    val log = existingLogsResponse.body()!!.data.first()
                    documentId = log.documentId
                    val attrs = log.attributes
                    if (attrs != null && attrs["sleep_duration"] != null) {
                        totalSleepHours = (attrs["sleep_duration"] as? Double)?.toFloat() ?: 0f
                        stages = listOf(
                            SleepStage(((attrs["light_sleep_duration"] as? Double)?.toFloat() ?: 0f).toInt(), "Light"),
                            SleepStage(((attrs["deep_sleep_duration"] as? Double)?.toFloat() ?: 0f).toInt(), "Deep"),
                            SleepStage(((attrs["rem_sleep_duration"] as? Double)?.toFloat() ?: 0f).toInt(), "REM")
                        )
                        bedtime = (attrs["startTime"] as? String)?.let {
                            LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).format(DateTimeFormatter.ofPattern("h:mm a")).uppercase()
                        } ?: bedtime
                        alarm = (attrs["endTime"] as? String)?.let {
                            LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).format(DateTimeFormatter.ofPattern("h:mm a")).uppercase()
                        } ?: alarm
                        isStrapiDataValid = totalSleepHours > 0f
                        Log.d("SleepViewModel", "Fetched valid Strapi sleep data for $date: totalSleepHours=$totalSleepHours, documentId=$documentId")
                    } else {
                        Log.w("SleepViewModel", "Invalid Strapi data for $date: attributes=$attrs")
                    }
                } else {
                    Log.d("SleepViewModel", "No Strapi sleep log for $date")
                }

                // Fetch Health Connect data if Strapi data is invalid or missing
                if (!isStrapiDataValid) {
                    sleepDataHc = healthConnectData ?: try {
                        healthConnectManager.readSleepSessions(date).also {
                            Log.d("SleepViewModel", "Fetched Health Connect sleep data for $date: total=${it.total.toMinutes()} min")
                        }
                    } catch (e: Exception) {
                        Log.e("SleepViewModel", "Failed to fetch Health Connect sleep data for $date: ${e.message}", e)
                        null
                    }

                    if (sleepDataHc != null && sleepDataHc.total.toMinutes() > 0) {
                        totalSleepHours = sleepDataHc.total.toMinutes() / 60f
                        stages = listOf(
                            SleepStage(sleepDataHc.light.toMinutes().toInt(), "Light"),
                            SleepStage(sleepDataHc.deep.toMinutes().toInt(), "Deep"),
                            SleepStage(sleepDataHc.rem.toMinutes().toInt(), "REM")
                        )
                        val formatter = DateTimeFormatter.ofPattern("h:mm a")
                        bedtime = sleepDataHc.start?.format(formatter)?.uppercase() ?: bedtime
                        alarm = sleepDataHc.end?.format(formatter)?.uppercase() ?: alarm
                        Log.d("SleepViewModel", "Using Health Connect sleep data for $date: totalSleepHours=$totalSleepHours")
                    } else {
                        Log.w("SleepViewModel", "No valid Health Connect sleep data for $date")
                    }
                }

                // Fetch workout data for insights
                val workoutData = try {
                    healthConnectManager.readExerciseSessions(date)
                } catch (e: Exception) {
                    Log.w("SleepViewModel", "Failed to fetch workout data for $date: ${e.message}", e)
                    WorkoutData(null, null, null, null, null, null, "Unknown")
                }
                val workoutIntensity = workoutData.calories ?: 0.0
                val recoveryBonus = if (workoutIntensity > 500.0) 0.5f else 0f
                val baseRestTime = 8f
                val totalRestTime = baseRestTime + recoveryBonus

                val debtMinutes = ((totalRestTime * 60) - (totalSleepHours * 60)).toInt().coerceAtLeast(0)
                val debt = formatDebt(debtMinutes)

                val insights = mutableListOf<String>()
                if (debtMinutes > 0) insights.add("Nap ${debtMinutes / 60}h${debtMinutes % 60}m to cut debt")
                if (workoutIntensity > 500.0) insights.add("Shift bedtime earlier for recovery")

                _sleepData.value = SleepDataUi(
                    score = calculateSleepScore(totalSleepHours, totalRestTime),
                    debt = debt,
                    injuryRisk = if (debtMinutes > 120) 25f else 0f,
                    bedtime = bedtime,
                    alarm = alarm,
                    stages = stages,
                    insights = insights,
                    streak = calculateStreak(),
                    challengeActive = debtMinutes <= 0,
                    restTime = totalRestTime,
                    actualSleepTime = totalSleepHours
                )
                Log.d("SleepViewModel", "Updated sleepData for $date: ${_sleepData.value}")

                // Sync with Strapi (POST or PUT) if Health Connect data is available
                if (sleepDataHc != null && sleepDataHc.total.toMinutes() > 0) {
                    Log.d("SleepViewModel", "Attempting to sync sleep data for $date")
                    if (documentId != null && isStrapiDataValid) {
                        val response = strapiRepository.updateSleepLog(documentId, sleepDataHc)
                        if (response.isSuccessful) {
                            Log.d("SleepViewModel", "Updated sleep log for $date: documentId=$documentId")
                            syncedDates[date] = true
                        } else {
                            Log.e("SleepViewModel", "Failed to update sleep log for $date: ${response.code()}, ${response.errorBody()?.string()}")
                            postUiMessage("Failed to update sleep data.")
                        }
                    } else {
                        val response = strapiRepository.syncSleepLog(date, sleepDataHc)
                        if (response.isSuccessful) {
                            Log.d("SleepViewModel", "Posted new sleep log for $date")
                            syncedDates[date] = true
                        } else {
                            Log.e("SleepViewModel", "Failed to post sleep log for $date: ${response.code()}, ${response.errorBody()?.string()}")
                            postUiMessage("Failed to sync sleep data.")
                        }
                    }
                } else {
                    Log.w("SleepViewModel", "No valid Health Connect data to sync for $date")
                }
            } catch (e: Exception) {
                Log.e("SleepViewModel", "Error fetching or syncing sleep data for $date: ${e.message}", e)
                postUiMessage("Failed to load sleep data.")
            }
        }
    }

    private fun calculateSleepScore(totalSleepHours: Float, targetHours: Float): Float {
        return ((totalSleepHours / targetHours) * 100).coerceIn(0f, 100f)
    }

    private fun calculateStreak(): Int {
        return 3 // Placeholder, to be implemented later
    }

    fun updateSettings(syncEnabled: Boolean, sleepGoal: Float) {
        val current = _sleepData.value
        val newRestTime = if (syncEnabled) current.restTime else sleepGoal
        _sleepData.value = current.copy(restTime = newRestTime)
        fetchAndSyncSleepData(LocalDate.now(), commonViewModel.sleepData.value)
    }

    private fun formatDebt(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h${m}m"
    }

    private fun postUiMessage(message: String) {
        commonViewModel.postUiMessage(message)
    }
}