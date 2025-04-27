package com.trailblazewellness.fitglide.presentation.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.max.MaxAiService
import com.trailblazewellness.fitglide.presentation.sleep.SleepViewModel
import com.trailblazewellness.fitglide.presentation.successstory.SuccessStoryViewModel
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

class HomeViewModel(
    private val commonViewModel: CommonViewModel,
    private val context: Context,
    private val healthConnectManager: HealthConnectManager,
    private val successStoryViewModel: SuccessStoryViewModel
) : ViewModel() {
    private val sleepViewModel: SleepViewModel = SleepViewModel(
        healthConnectManager,
        commonViewModel.getStrapiRepository(),
        commonViewModel.getAuthRepository(),
        context
    )

    private val sharedPreferences =
        context.getSharedPreferences("fitglide_prefs", Context.MODE_PRIVATE)

    private val _homeData = MutableStateFlow(HomeData())
    val homeData: StateFlow<HomeData> = _homeData.asStateFlow()

    private val _fetchedBadges = MutableStateFlow<List<StrapiApi.Badge>>(emptyList())
    private val hasFetchedMessages = AtomicBoolean(false)

    // Navigation trigger for FAB
    private val _navigateToCreateStory = MutableStateFlow(false)
    val navigateToCreateStory: StateFlow<Boolean> = _navigateToCreateStory.asStateFlow()

    init {
        Log.d("DesiMaxDebug", "HomeViewModel initialized")
        clearSharedPreferences()
        viewModelScope.launch {
            // Wait for auth state to be initialized
            while (!commonViewModel.getAuthRepository().isLoggedIn()) {
                Log.d("DesiMaxDebug", "Waiting for auth state to be initialized...")
                delay(100L)
            }

            // Wait for the first emission from authStateFlow to get the correct firstName
            val authState = commonViewModel.getAuthRepository().authStateFlow.first()
            val firstName = authState.userName ?: "User"
            Log.d("DesiMaxDebug", "Fetched initial authState: id=${authState.getId()}, userName=${authState.userName}, jwt=${authState.jwt}")
            Log.d("DesiMaxDebug", "Setting initial firstName for HomeData: $firstName")

            val vitals = fetchInitialData()
            val age = vitals?.date_of_birth?.let {
                Period.between(LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE), LocalDate.now()).years
            } ?: 30

            // Initialize HomeData with the correct firstName
            _homeData.value = HomeData(
                firstName = firstName,
                watchSteps = 0f,
                manualSteps = 0f,
                trackedSteps = 0f,
                stepGoal = vitals?.stepGoal?.toFloat() ?: 10000f,
                sleepHours = 0f,
                caloriesBurned = 0f,
                heartRate = 0f,
                maxHeartRate = (220 - age).toFloat(),
                hydration = 0f,
                caloriesLogged = 0f,
                weightLost = 0f,
                bmr = commonViewModel.bmr.value.takeIf { it > 0 } ?: vitals?.calorieGoal?.toInt() ?: 2000,
                stressScore = "Unknown",
                challengeOrAnnouncement = "Weekly Step Challenge!",
                streak = 0,
                showStories = sharedPreferences.getBoolean("show_stories", true),
                storiesOrLeaderboard = listOf("User: 10K steps"),
                maxMessage = MaxMessage("", "", false),
                isTracking = false,
                dateRangeMode = "Day",
                badges = emptyList(),
                healthVitalsUpdated = vitals != null
            )

            // Continue to reactively update firstName if authState changes
            commonViewModel.getAuthRepository().authStateFlow.collect { updatedAuthState ->
                val updatedFirstName = updatedAuthState.userName ?: "User"
                if (updatedFirstName != _homeData.value.firstName) {
                    Log.d("DesiMaxDebug", "AuthState changed, updating firstName to: $updatedFirstName")
                    _homeData.update { it.copy(firstName = updatedFirstName) }
                }
            }

            fetchDesiMessagesAndBadges()
            // Trigger initial refresh to fetch sleep data
            refreshData()
            // Ensure initializeWithContext is called after refreshData to use the latest sleep data
            initializeWithContext()
        }
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

    private suspend fun fetchInitialData(): StrapiApi.HealthVitalsEntry? {
        return try {
            val userId = commonViewModel.getAuthRepository().getAuthState().getId() ?: "1"
            val token = "Bearer ${commonViewModel.getAuthRepository().getAuthState().jwt ?: return null}"
            val response = commonViewModel.getStrapiRepository().getHealthVitals(userId, token)
            if (response.isSuccessful) {
                response.body()?.data?.firstOrNull().also {
                    Log.d("DesiMaxDebug", "Fetched HealthVitals: $it")
                }
            } else {
                Log.e("DesiMaxDebug", "HealthVitals fetch failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("DesiMaxDebug", "Error fetching HealthVitals: ${e.message}", e)
            null
        }
    }

    fun initializeWithContext() {
        Log.d("DesiMaxDebug", "🚀 initializeWithContext triggered")
        Log.d("DesiMaxDebug", "Current sleepHours before combine: ${_homeData.value.sleepHours}")

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
                commonViewModel.hydration,
                commonViewModel.heartRate,
                commonViewModel.caloriesBurned
            ) { steps, hydration, heartRate, calories ->
                HealthMetrics(steps, 0f, hydration, heartRate, calories)
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
                val hrv = try {
                    healthConnectManager.readHRV(state.date)?.sdnn ?: 0f
                } catch (e: Exception) {
                    Log.w("DesiMaxDebug", "HRV not available: ${e.message}")
                    0f
                }
                val stressScore = if (hrv == 0f) {
                    val sleepScore = when (_homeData.value.sleepHours) {
                        in 7.5f..Float.MAX_VALUE -> 0f
                        in 6.0f..7.5f -> 50f
                        else -> 100f
                    }
                    val activityScore = when {
                        (health.steps + state.trackedSteps > 20000 || health.calories > 1000) -> 100f
                        (health.steps + state.trackedSteps > 15000 || health.calories > 500) -> 50f
                        else -> 0f
                    }
                    val stressValue = 0.5f * sleepScore + 0.5f * activityScore
                    when {
                        stressValue <= 33f -> "Low"
                        stressValue <= 66f -> "Medium"
                        else -> "High"
                    }
                } else {
                    when {
                        hrv >= 50 -> "Low"
                        hrv >= 20 -> "Medium"
                        else -> "High"
                    }
                }
                val updated = _homeData.value.copy(
                    watchSteps = health.steps,
                    sleepHours = _homeData.value.sleepHours, // Preserve sleep data from SleepViewModel
                    hydration = health.hydration,
                    heartRate = health.heartRate,
                    caloriesBurned = health.calories,
                    trackedSteps = state.trackedSteps,
                    bmr = state.bmr,
                    isTracking = state.isTracking,
                    stressScore = stressScore
                )
                Log.d("DesiMaxDebug", "Updated _homeData in combine: sleepHours=${updated.sleepHours}")
                updated.copy(badges = assignBadges(updated, badges))
            }.collect { newData ->
                Log.d("DesiMaxDebug", "Collecting new _homeData: sleepHours=${newData.sleepHours}")
                _homeData.value = newData
            }
        }
    }

    suspend fun refreshData() {
        Log.d("DesiMaxDebug", "Refreshing home data")
        try {
            val date = LocalDate.now()
            // Fetch sleep data from SleepViewModel
            sleepViewModel.fetchSleepData(date)
            val sleepDataUi = sleepViewModel.sleepData.value
            val sleep = sleepDataUi?.actualSleepTime ?: 0f
            Log.d("DesiMaxDebug", "Fetched sleep hours from SleepViewModel: $sleep")

            // Use CommonViewModel to refresh other data (steps, hydration, etc.), but not sleep
            val token = "Bearer ${commonViewModel.getAuthRepository().getAuthState().jwt ?: return}"
            commonViewModel.syncHealthData(token)

            // Update _homeData with the sleep data from SleepViewModel
            _homeData.update {
                it.copy(
                    sleepHours = sleep
                )
            }
            Log.d("DesiMaxDebug", "Updated _homeData with sleepHours: $sleep")

            // Fetch badges
            if (!token.isNullOrBlank()) {
                val authToken = token
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
                    Log.d("DesiMaxDebug", "Fetched badges: ${mappedBadges.size}")
                } else {
                    Log.e("DesiMaxDebug", "Failed to fetch badges: ${badgesResponse?.code()}")
                }
            } else {
                Log.w("DesiMaxDebug", "No token available for fetching badges")
            }

            Log.d("DesiMaxDebug", "Refresh complete: sleep=$sleep")
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
        sleepViewModel.fetchSleepData(newDate) // Refresh sleep data for the new date
        viewModelScope.launch {
            refreshData() // Re-run refreshData to update _homeData with the new sleep data
        }
    }

    fun toggleStoriesOrLeaderboard() {
        val newShowStories = !_homeData.value.showStories
        _homeData.update { it.copy(showStories = newShowStories) }
        sharedPreferences.edit().putBoolean("show_stories", newShowStories).apply()
    }

    fun setDateRangeMode(mode: String) {
        _homeData.update { it.copy(dateRangeMode = mode) }
    }

    // Method to trigger navigation to create a new story
    fun onCreateStoryClicked() {
        _navigateToCreateStory.value = true
    }

    // Method to reset navigation trigger after navigation is handled
    fun onNavigationHandled() {
        _navigateToCreateStory.value = false
    }
}

data class HomeData(
    val firstName: String = "User",
    val watchSteps: Float = 0f,
    val manualSteps: Float = 0f,
    val trackedSteps: Float = 0f,
    val stepGoal: Float = 11000f,
    val sleepHours: Float = 0f,
    val caloriesBurned: Float = 0f,
    val heartRate: Float = 0f,
    val maxHeartRate: Float = 200f,
    val hydration: Float = 0f,
    val caloriesLogged: Float = 0f,
    val weightLost: Float = 0f,
    val bmr: Int = 2000,
    val stressScore: String = "Unknown",
    val challengeOrAnnouncement: String = "Weekly Step Challenge!",
    val streak: Int = 0,
    val showStories: Boolean = true,
    val storiesOrLeaderboard: List<String> = listOf("User: 10K steps"),
    val maxMessage: MaxMessage = MaxMessage("", "", false),
    val isTracking: Boolean = false,
    val dateRangeMode: String = "Day",
    val badges: List<StrapiApi.Badge> = emptyList(),
    val healthVitalsUpdated: Boolean = false
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