
package com.trailblazewellness.fitglide.presentation.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.max.MaxAiService
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    private val commonViewModel: CommonViewModel,
    @ApplicationContext private val context: Context,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {
    private val sharedPreferences =
        context.getSharedPreferences("fitglide_prefs", Context.MODE_PRIVATE)

    private val _homeData = MutableStateFlow(
        HomeData(
            watchSteps = 0f,
            manualSteps = 0f,
            trackedSteps = 0f,
            stepGoal = 11000f,
            sleepHours = 0f,
            caloriesBurned = 0f,
            heartRate = 0f,
            maxHeartRate = 200f,
            hydration = 0f,
            caloriesLogged = 0f,
            weightLost = 0f,
            bmr = 2000,
            stressScore = "Low",
            challengeOrAnnouncement = "Weekly Step Challenge!",
            streak = 0,
            showStories = true,
            storiesOrLeaderboard = listOf("User: 10K steps"),
            maxMessage = MaxMessage("", "", false),
            isTracking = false,
            dateRangeMode = "Day",
            badges = emptyList()
        )
    )
    val homeData: StateFlow<HomeData> = _homeData.asStateFlow()

    private val _fetchedBadges = MutableStateFlow<List<StrapiApi.Badge>>(emptyList())
    private val hasFetchedMessages = AtomicBoolean(false)

    init {
        Log.d("DesiMaxDebug", "HomeViewModel initialized")
        clearSharedPreferences()
        fetchDesiMessagesAndBadges()
    }

    private fun clearSharedPreferences() {
        sharedPreferences.edit().apply {
            remove("steps")
            remove("trackedSteps")
            remove("sleepHours")
            remove("caloriesBurned")
            remove("heartRate")
            remove("hydration")
            remove("bmr")
            apply()
        }
        Log.d("DesiMaxDebug", "Cleared SharedPreferences for metrics")
    }

    fun initializeWithContext() {
        Log.d("DesiMaxDebug", "🚀 initializeWithContext triggered")

        viewModelScope.launch {
            sharedPreferences.edit().apply {
                remove("max_hasPlayed")
                remove("max_yesterday")
                remove("max_today")
                apply()
            }
            Log.d("DesiMaxDebug", "Cleared max message SharedPreferences")

            val healthFlow = combine(
                commonViewModel.steps,
                commonViewModel.sleepHours,
                commonViewModel.hydration,
                commonViewModel.heartRate,
                commonViewModel.caloriesBurned
            ) { steps, sleep, hydration, heartRate, calories ->
                HealthMetrics(steps, sleep, hydration, heartRate, calories)
            }

            val stateFlow = combine(
                commonViewModel.trackedStepsFlow,
                commonViewModel.bmr,
                commonViewModel.isTracking,
                commonViewModel.date
            ) { trackedSteps, bmr, isTracking, date ->
                StateMetrics(trackedSteps, bmr, isTracking, date)
            }

            combine(healthFlow, stateFlow, _fetchedBadges) { health, state, badges ->
                val updated = _homeData.value.copy(
                    watchSteps = health.steps,
                    sleepHours = health.sleep,
                    hydration = health.hydration,
                    heartRate = health.heartRate,
                    caloriesBurned = health.calories,
                    trackedSteps = state.trackedSteps,
                    bmr = state.bmr,
                    isTracking = state.isTracking
                )
                updated.copy(badges = assignBadges(updated, badges))
            }.collect { updatedHomeData ->
                _homeData.value = updatedHomeData
            }
        }
    }

    suspend fun refreshData() {
        Log.d("DesiMaxDebug", "Refreshing home data")
        try {
            val date = LocalDate.now()
            val steps = healthConnectManager.readSteps(date).toFloat()
            val sleep = healthConnectManager.readSleepSessions(date).total.toHours().toFloat()
            val hydrationRecords = healthConnectManager.readHydrationRecords(
                date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                date.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC)
            )
            val hydration = hydrationRecords.sumOf { record -> record.volume.inLiters }.toFloat()
            val heartRate = healthConnectManager.readDailyHeartRate(date)?.toFloat() ?: 0f
            val calories = healthConnectManager.readExerciseSessions(date).calories?.toFloat() ?: 0f

            _homeData.update {
                it.copy(
                    watchSteps = steps,
                    sleepHours = sleep,
                    hydration = hydration,
                    heartRate = heartRate,
                    caloriesBurned = calories
                )
            }

            val token = commonViewModel.getAuthRepository().getAuthState().jwt
            if (!token.isNullOrBlank()) {
                val authToken = "Bearer $token"
                val badgesResponse = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        commonViewModel.getStrapiRepository().getBadges(authToken)
                    }
                }
                if (badgesResponse?.isSuccessful == true) {
                    val fetchedBadges = badgesResponse.body()?.data ?: emptyList()
                    val mappedBadges = fetchedBadges.mapNotNull { badge ->
                        val iconUrl = badge.icon?.url?.let { url ->
                            if (url.startsWith("http")) url else "https://admin.fitglide.in$url"
                        }
                        iconUrl?.let {
                            StrapiApi.Badge(
                                id = badge.id,
                                title = badge.name,
                                description = badge.description,
                                iconUrl = it
                            )
                        }
                    }
                    _fetchedBadges.value = mappedBadges
                }
            }

            Log.d("DesiMaxDebug", "Refresh complete: steps=$steps, sleep=$sleep, hydration=$hydration")
        } catch (e: Exception) {
            Log.e("DesiMaxDebug", "Refresh failed: ${e.message}", e)
            commonViewModel.postUiMessage("Failed to refresh data: ${e.message}")
        }
    }

    private fun fetchDesiMessagesAndBadges() {
        if (hasFetchedMessages.getAndSet(true)) {
            Log.d("DesiMaxDebug", "Skipping duplicate desi messages fetch")
            return
        }

        viewModelScope.launch {
            Log.d("DesiMaxDebug", "Entering desi messages coroutine")
            delay(1000L)
            Log.d("DesiMaxDebug", "Starting desi messages fetch after delay")

            val token = commonViewModel.getAuthRepository().getAuthState().jwt
            Log.d("DesiMaxDebug", "JWT token from authRepository: ${token ?: "null"}")
            if (token.isNullOrBlank()) {
                Log.e("DesiMaxDebug", "JWT token is null or blank, cannot fetch desi messages")
                commonViewModel.postUiMessage("Please log in to see Max's messages!")
                val updatedMax = MaxMessage(
                    "Arre kal thoda slow tha yaar 😅 Aaj full josh mein jaa! 💪",
                    "Naya din, naya chance — thoda sweat bahao Boss! 🔥",
                    false
                )
                _homeData.update { it.copy(maxMessage = updatedMax) }
                saveMaxMessage(updatedMax)
                Log.d("DesiMaxDebug", "Exiting desi messages coroutine due to null token")
                return@launch
            }

            val authToken = "Bearer $token"
            Log.d("DesiMaxDebug", "Fetching desi messages with token: $authToken")

            var messages: List<StrapiApi.DesiMessage> = emptyList()
            for (attempt in 1..2) {
                try {
                    Log.d("DesiMaxDebug", "Attempt $attempt to fetch desi messages")
                    val messagesResponse = withTimeoutOrNull(10000L) {
                        withContext(Dispatchers.IO) {
                            commonViewModel.getStrapiRepository().getDesiMessages(authToken)
                        }
                    }
                    if (messagesResponse == null) {
                        Log.e("DesiMaxDebug", "Desi messages fetch timed out after 10 seconds on attempt $attempt")
                        continue
                    }

                    if (messagesResponse.isSuccessful) {
                        val rawMessages = messagesResponse.body()?.data ?: emptyList()
                        Log.d("DesiMaxDebug", "Raw desi messages response: $rawMessages")
                        messages = rawMessages.filter {
                            it.yesterdayLine != null && it.todayLine != null &&
                                    it.yesterdayLine.isNotBlank() && it.todayLine.isNotBlank()
                        }
                        Log.d("DesiMaxDebug", "Fetched ${messages.size} valid desi messages: ${messages.map { it.todayLine }}")
                        break
                    } else {
                        Log.e("DesiMaxDebug", "Failed to fetch desi messages on attempt $attempt: ${messagesResponse.code()} - ${messagesResponse.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("DesiMaxDebug", "Exception fetching desi messages on attempt $attempt: ${e.message}", e)
                }
                if (attempt < 2) delay(2000L)
            }

            if (messages.isNotEmpty()) {
                val randomMsg = messages.random()
                val yesterdayMsg = randomMsg.yesterdayLine
                val todayMsg = randomMsg.todayLine
                val updatedMax = MaxMessage(yesterdayMsg, todayMsg, false)
                Log.d("DesiMaxDebug", "Selected message - yesterday: $yesterdayMsg, today: $todayMsg")
                if (commonViewModel.isTtsEnabled()) {
                    withContext(Dispatchers.IO) {
                        MaxAiService.speak("$yesterdayMsg $todayMsg")
                    }
                }
                _homeData.update { it.copy(maxMessage = updatedMax) }
                saveMaxMessage(updatedMax)
            } else {
                Log.w("DesiMaxDebug", "No valid desi messages fetched, using fallback")
                commonViewModel.postUiMessage("Couldn't load Max's message. Showing a default one!")
                val updatedMax = MaxMessage(
                    "Arre kal thoda slow tha yaar 😅 Aaj full josh mein jaa! 💪",
                    "Naya din, naya chance — thoda sweat bahao Boss! 🔥",
                    false
                )
                _homeData.update { it.copy(maxMessage = updatedMax) }
                saveMaxMessage(updatedMax)
            }

            try {
                Log.d("DesiMaxDebug", "Fetching badges")
                val badgesResponse = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        commonViewModel.getStrapiRepository().getBadges(authToken)
                    }
                }
                if (badgesResponse == null) {
                    Log.e("DesiMaxDebug", "Badges fetch timed out after 10 seconds")
                    commonViewModel.postUiMessage("Badges took too long to load. Try again!")
                    return@launch
                }
                if (badgesResponse.isSuccessful) {
                    val fetchedBadges = badgesResponse.body()?.data ?: emptyList()
                    Log.d("DesiMaxDebug", "Fetched ${fetchedBadges.size} badges: ${fetchedBadges.map { it.name }}")
                    val mappedBadges = fetchedBadges.mapNotNull { badge ->
                        val iconUrl = badge.icon?.url?.let { url ->
                            if (url.startsWith("http")) url else "https://admin.fitglide.in$url"
                        }
                        iconUrl?.let {
                            StrapiApi.Badge(
                                id = badge.id,
                                title = badge.name,
                                description = badge.description,
                                iconUrl = it
                            )
                        }
                    }
                    _fetchedBadges.value = mappedBadges
                } else {
                    Log.e("DesiMaxDebug", "Failed to fetch badges: ${badgesResponse.code()} - ${badgesResponse.errorBody()?.string()}")
                    commonViewModel.postUiMessage("Failed to load badges. Please try again.")
                }
            } catch (e: Exception) {
                Log.e("DesiMaxDebug", "Exception fetching badges: ${e.message}", e)
                commonViewModel.postUiMessage("Error loading badges. Please check your connection.")
            }
            Log.d("DesiMaxDebug", "Exiting desi messages coroutine")
        }
    }

    private fun assignBadges(data: HomeData, fetchedBadges: List<StrapiApi.Badge>): List<StrapiApi.Badge> {
        val earnedBadges = mutableListOf<StrapiApi.Badge>()
        val totalSteps = data.watchSteps + data.manualSteps + data.trackedSteps

        fetchedBadges.forEach { badge ->
            when (badge.title) {
                "Step Sultan" -> if (totalSteps >= 10000) earnedBadges.add(badge)
                "Hydration Hero" -> if (data.hydration >= 2.5f) earnedBadges.add(badge)
                "Sleep Maharaja" -> if (data.sleepHours >= 7.5f) earnedBadges.add(badge)
                "Dumbbell Daaku" -> if (data.caloriesBurned >= 500) earnedBadges.add(badge)
                "Yoga Yodha" -> if (totalSteps >= 5000 && data.heartRate <= 85) earnedBadges.add(badge)
                "Josh Machine" -> if (totalSteps > 8000 && data.caloriesBurned > 450 && data.sleepHours > 7) {
                    earnedBadges.add(badge)
                }
                "Cycle Rani" -> if (totalSteps >= 5000) earnedBadges.add(badge)
                "Max Ka Dost" -> if (data.streak >= 7) earnedBadges.add(badge)
            }
        }

        return earnedBadges
    }

    private fun saveMaxMessage(maxMessage: MaxMessage) {
        val prefs = context.getSharedPreferences("max_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("max_yesterday", maxMessage.yesterday)
            putString("max_today", maxMessage.today)
            putBoolean("max_hasPlayed", maxMessage.hasPlayed)
            apply()
        }
        Log.d("DesiMaxDebug", "Saved max message: yesterday=${maxMessage.yesterday}, today=${maxMessage.today}, hasPlayed=${maxMessage.hasPlayed}")
    }

    fun markMaxMessagePlayed() {
        val updated = _homeData.value.maxMessage.copy(hasPlayed = true)
        _homeData.update { it.copy(maxMessage = updated) }
        saveMaxMessage(updated)
        Log.d("DesiMaxDebug", "Marked max message as played")
    }

    fun startTracking() {
        if (!_homeData.value.isTracking) {
            Log.d("DesiMaxDebug", "Starting tracking")
            _homeData.update { it.copy(isTracking = true) }
        }
    }

    fun stopTracking() {
        if (_homeData.value.isTracking) {
            Log.d("DesiMaxDebug", "Stopping tracking")
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
    val trackedSteps: Float,
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
    val dateRangeMode: String,
    val badges: List<StrapiApi.Badge>
)

data class MaxMessage(val yesterday: String, val today: String, val hasPlayed: Boolean)

data class HealthMetrics(
    val steps: Float,
    val sleep: Float,
    val hydration: Float,
    val heartRate: Float,
    val calories: Float
)

data class StateMetrics(
    val trackedSteps: Float,
    val bmr: Int,
    val isTracking: Boolean,
    val date: LocalDate
)
