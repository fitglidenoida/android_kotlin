package com.trailblazewellness.fitglide.presentation.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.max.MaxAiService
import com.trailblazewellness.fitglide.data.max.MaxPromptBuilder
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            maxMessage = MaxMessage("", "", false),
            isTracking = false, trackedSteps = 0f, dateRangeMode = "Day",
            badges = emptyList()
        )
    )
    val homeData: StateFlow<HomeData> = _homeData.asStateFlow()

    fun initializeWithContext(context: Context) {
        Log.d("DesiMaxDebug", "🚀 initializeWithContext triggered")

        val savedMax = loadSavedMaxMessage(context)
        Log.d("DesiMaxDebug", "📤 Loaded from SharedPrefs: $savedMax")
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

                Log.d("DesiMaxDebug", "⏳ Building Max prompt...")

                val response = withContext(Dispatchers.IO) {
                    MaxAiService.fetchMaxGreeting(prompt)
                }

                val lines = response.split("\n").filter { it.isNotBlank() }

                Log.d("DesiMaxDebug", "📩 Raw Max Response: $response")
                Log.d("DesiMaxDebug", "✂️ Extracted Lines: $lines")

                val yesterdayMsg = lines.getOrNull(0)
                    ?: "Arre kal thoda slow tha yaar 😅 Aaj full josh mein jaa! 💪"
                val todayMsg = lines.getOrNull(1)
                    ?: "Naya din, naya chance — thoda sweat bahao Boss! 🔥"

                val updatedMax = MaxMessage(yesterdayMsg, todayMsg, hasPlayed = false)

                if (commonViewModel.isTtsEnabled()) {
                    MaxAiService.speak("$yesterdayMsg $todayMsg")
                }

                val updated = _homeData.value.copy(
                    watchSteps = steps,
                    sleepHours = sleep,
                    hydration = hydration,
                    heartRate = hr,
                    caloriesBurned = calories,
                    maxMessage = updatedMax
                )

                val newBadges = assignBadges(updated)

                _homeData.update {
                    updated.copy(badges = newBadges)
                }
            }.collect()
        }
    }

    private fun assignBadges(data: HomeData): List<StrapiApi.Badge> {
        val earnedBadges = mutableListOf<StrapiApi.Badge>()

        val totalSteps = data.watchSteps + data.manualSteps + data.trackedSteps

        if (totalSteps >= 10000) earnedBadges.add(StrapiApi.Badge(1, "Step Sultan", "10K+ Steps in a day!", "https://cdn.example.com/step_sultan.png"))
        if (data.hydration >= 2.5f) earnedBadges.add(StrapiApi.Badge(2, "Hydration Hero", "Drank 2.5L of water", "https://cdn.example.com/hydration_hero.png"))
        if (data.sleepHours >= 7.5f) earnedBadges.add(StrapiApi.Badge(3, "Sleep Maharaja", "Slept well last night", "https://cdn.example.com/sleep_maharaja.png"))
        if (data.caloriesBurned >= 500) earnedBadges.add(StrapiApi.Badge(4, "Dumbbell Daaku", "Burned 500+ cals", "https://cdn.example.com/dumbbell_daaku.png"))
        if (totalSteps >= 5000 && data.heartRate <= 85) earnedBadges.add(StrapiApi.Badge(5, "Yoga Yodha", "Balanced effort", "https://cdn.example.com/yoga_yodha.png"))
        if (totalSteps > 8000 && data.caloriesBurned > 450 && data.sleepHours > 7) earnedBadges.add(StrapiApi.Badge(6, "Josh Machine", "All-round performer", "https://cdn.example.com/josh_machine.png"))

        return earnedBadges
    }


    private fun loadSavedMaxMessage(context: Context): MaxMessage {
        val prefs = context.getSharedPreferences("max_prefs", Context.MODE_PRIVATE)
        val yesterday = prefs.getString("max_yesterday", "") ?: ""
        val today = prefs.getString("max_today", "") ?: ""
        val hasPlayed = prefs.getBoolean("max_hasPlayed", false)
        Log.d("DesiMaxDebug", "📤 Loaded from SharedPrefs: yesterday='$yesterday', today='$today'")
        return MaxMessage(yesterday, today, hasPlayed)
    }

    private fun saveMaxMessage(context: Context, maxMessage: MaxMessage) {
        val prefs = context.getSharedPreferences("max_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("max_yesterday", maxMessage.yesterday)
            putString("max_today", maxMessage.today)
            putBoolean("max_hasPlayed", maxMessage.hasPlayed)
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
    val dateRangeMode: String,
    val badges: List<StrapiApi.Badge> = emptyList()

)

data class MaxMessage(val yesterday: String, val today: String, val hasPlayed: Boolean)
