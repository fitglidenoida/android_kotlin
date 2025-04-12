package com.trailblazewellness.fitglide.presentation.sleep

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.healthconnect.SleepData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _sleepData = MutableStateFlow(SleepDataUi())
    val sleepData: StateFlow<SleepDataUi> = _sleepData.asStateFlow()

    init {
        fetchSleepData(LocalDate.now())
    }

    fun fetchSleepData(date: LocalDate) {
        viewModelScope.launch {
            try {
                val sleepDataHc = healthConnectManager.readSleepSessions(date)
                val workoutData = healthConnectManager.readExerciseSessions(date)
                val workoutIntensity = workoutData.calories ?: 0.0
                val recoveryBonus = if (workoutIntensity > 500.0) 0.5f else 0f
                val baseRestTime = 8f
                val totalRestTime = baseRestTime + recoveryBonus

                val stages = listOf(
                    SleepStage(sleepDataHc.light.toMinutes().toInt(), "Light"),
                    SleepStage(sleepDataHc.deep.toMinutes().toInt(), "Deep"),
                    SleepStage(sleepDataHc.rem.toMinutes().toInt(), "REM")
                )
                val totalSleepMinutes = sleepDataHc.total.toMinutes().toFloat()
                val actualSleepHours = totalSleepMinutes / 60f
                val debtMinutes = ((totalRestTime * 60) - totalSleepMinutes).toInt().coerceAtLeast(0)
                val debt = formatDebt(debtMinutes)
                val formatter = DateTimeFormatter.ofPattern("h:mm a")
                val bedtime = sleepDataHc.start.format(formatter).uppercase()
                val alarm = sleepDataHc.end.format(formatter).uppercase()

                val insights = mutableListOf<String>()
                if (debtMinutes > 0) insights.add("Nap ${debtMinutes / 60}h${debtMinutes % 60}m to cut debt")
                if (workoutIntensity > 500.0) insights.add("Shift bedtime earlier for recovery")

                _sleepData.value = SleepDataUi(
                    score = calculateSleepScore(sleepDataHc, totalRestTime),
                    debt = debt,
                    injuryRisk = if (debtMinutes > 120) 25f else 0f,
                    bedtime = bedtime,
                    alarm = alarm,
                    stages = stages,
                    insights = insights,
                    streak = calculateStreak(),
                    challengeActive = debtMinutes <= 0,
                    restTime = totalRestTime,
                    actualSleepTime = actualSleepHours
                )
                Log.d("SleepViewModel", "Fetched sleep data: ${_sleepData.value}")

                strapiRepository.syncSleepLog(date, sleepDataHc)
            } catch (e: Exception) {
                Log.e("SleepViewModel", "Error fetching sleep data: ${e.message}", e)
            }
        }
    }

    private fun calculateSleepScore(sleepData: SleepData, targetHours: Float): Float {
        val totalSleepHours = sleepData.total.toMinutes() / 60f
        return ((totalSleepHours / targetHours) * 100).coerceIn(0f, 100f)
    }

    private fun calculateStreak(): Int {
        return 3 // Placeholder
    }

    fun updateSettings(syncEnabled: Boolean, sleepGoal: Float) {
        val current = _sleepData.value
        val newRestTime = if (syncEnabled) current.restTime else sleepGoal
        _sleepData.value = current.copy(restTime = newRestTime)
        fetchSleepData(LocalDate.now())
    }

    private fun formatDebt(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h${m}m"
    }
}