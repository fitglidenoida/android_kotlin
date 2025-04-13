package com.trailblazewellness.fitglide.presentation.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.max.MaxAiService
import com.trailblazewellness.fitglide.data.max.MaxPromptBuilder
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(
    private val commonViewModel: CommonViewModel
) : ViewModel() {

    private val _homeData = MutableStateFlow(
        HomeData(
            watchSteps = 0f, manualSteps = 0f, stepGoal = 11000f, sleepHours = 0f, caloriesBurned = 0f,
            heartRate = 0f, maxHeartRate = 200f, hydration = 0f, caloriesLogged = 0f, weightLost = 0f,
            bmr = 2000, stressScore = "Low", challengeOrAnnouncement = "Weekly Step Challenge!",
            streak = 0, showStories = true, storiesOrLeaderboard = listOf("User: 10K steps"),
            maxMessage = MaxMessage("You crushed it yesterday!", "Let’s win today too!", false),
            isTracking = false, trackedSteps = 0f, dateRangeMode = "Day"
        )
    )
    val homeData: StateFlow<HomeData> = _homeData.asStateFlow()

    fun initializeWithContext(context: Context) {
        val savedMax = loadSavedMaxMessage(context)
        _homeData.update { it.copy(maxMessage = savedMax) }

        viewModelScope.launch {
            combine(
                commonViewModel.steps,
                commonViewModel.sleepHours,
                commonViewModel.hydration,
                commonViewModel.heartRate,
                commonViewModel.caloriesBurned
            ) { steps, sleep, hydration, hr, calories ->

                val prompt = MaxPromptBuilder.getPrompt(
                    userName = commonViewModel.getAuthRepository().getAuthState().userName,
                    steps = steps,
                    sleep = sleep,
                    hydration = hydration
                )

                val response = MaxAiService.fetchMaxGreeting(prompt)
                val lines = response.split("\n").filter { it.isNotBlank() }

                Log.d("MaxAiService", "Prompt sent:\n$prompt")
                Log.d("MaxAiService", "Response:\n$response")

                val yesterdayMsg = lines.getOrNull(0)
                    ?: "Arre kal thoda slow tha yaar 😅 Aaj full josh mein jaa! 💪"
                val todayMsg = lines.getOrNull(1)
                    ?: "Naya din, naya chance — thoda sweat bahao Boss! 🔥"

                val updatedMax = MaxMessage(yesterdayMsg, todayMsg, hasPlayed = false)

                if (commonViewModel.isTtsEnabled()) {
                    MaxAiService.speak("$yesterdayMsg $todayMsg")
                }

                saveMaxMessage(context, updatedMax)

                _homeData.update {
                    it.copy(
                        watchSteps = steps,
                        sleepHours = sleep,
                        hydration = hydration,
                        heartRate = hr,
                        caloriesBurned = calories,
                        maxMessage = updatedMax
                    )
                }
            }.collect()
        }
    }

    private fun loadSavedMaxMessage(context: Context): MaxMessage {
        val prefs = context.getSharedPreferences("max_prefs", Context.MODE_PRIVATE)
        val yesterday = prefs.getString("max_yesterday", "You crushed it yesterday!") ?: ""
        val today = prefs.getString("max_today", "Let’s win today too!") ?: ""
        val hasPlayed = prefs.getBoolean("max_hasPlayed", false)
        return MaxMessage(yesterday, today, hasPlayed)
    }

    private fun saveMaxMessage(context: Context, maxMessage: MaxMessage) {
        val prefs = context.getSharedPreferences("max_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("max_yesterday", maxMessage.yesterday)
            putString("max_today", maxMessage.today)
            putBoolean("max_hasPlayed", false)
            apply()
        }
    }

    fun getMaxMessage(): MaxMessage? = _homeData.value.maxMessage.takeIf { !it.hasPlayed }

    fun markMaxMessagePlayed(context: Context) {
        val updated = _homeData.value.maxMessage.copy(hasPlayed = true)
        _homeData.update { it.copy(maxMessage = updated) }
        saveMaxMessage(context, updated)
    }

    fun startTracking() {
        if (!_homeData.value.isTracking) {
            Log.d("HomeViewModel", "Starting tracking")
            _homeData.update { it.copy(isTracking = true) }
        }
    }

    fun stopTracking() {
        if (_homeData.value.isTracking) {
            Log.d("HomeViewModel", "Stopping tracking")
            _homeData.update { it.copy(isTracking = false) }
        }
    }

    fun updateDate(newDate: LocalDate) {
        commonViewModel.updateDate(newDate)
    }

    fun toggleStoriesOrLeaderboard() {
        _homeData.update { it.copy(showStories = !it.showStories) }
    }

    fun setDateRangeMode(mode: String) {
        _homeData.update { it.copy(dateRangeMode = mode) }
    }
}

data class HomeData(
    val watchSteps: Float,
    val manualSteps: Float,
    val stepGoal: Float,
    val sleepHours: Float,
    val caloriesBurned: Float,
    val heartRate: Float,
    val maxHeartRate: Float,
    val hydration: Float,
    val caloriesLogged: Float,
    val weightLost: Float,
    val bmr: Int,
    val stressScore: String,
    val challengeOrAnnouncement: String,
    val streak: Int,
    val showStories: Boolean,
    val storiesOrLeaderboard: List<String>,
    val maxMessage: MaxMessage,
    val isTracking: Boolean,
    val trackedSteps: Float,
    val dateRangeMode: String
)

data class MaxMessage(val yesterday: String, val today: String, val hasPlayed: Boolean)
