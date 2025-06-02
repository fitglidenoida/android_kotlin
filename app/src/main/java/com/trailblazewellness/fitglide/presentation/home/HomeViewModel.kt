package com.trailblazewellness.fitglide.presentation.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
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
    private val maxPrefs =
        context.getSharedPreferences("max_prefs", Context.MODE_PRIVATE)

    val homeData = MutableStateFlow(HomeData()) // Made public, renamed from _homeData
    val homeDataFlow: StateFlow<HomeData> = homeData.asStateFlow()

    private val _fetchedBadges = MutableStateFlow<List<StrapiApi.Badge>>(emptyList())
    private val hasFetchedMessages = AtomicBoolean(false)

    private val _navigateToCreateStory = MutableStateFlow(false)
    val navigateToCreateStory: StateFlow<Boolean> = _navigateToCreateStory.asStateFlow()

    init {
        Log.d("DesiMaxDebug", "HomeViewModel initialized")
        clearSharedPreferences()
        viewModelScope.launch {
            maxPrefs.edit().clear().apply()
            Log.d("DesiMaxDebug", "Cleared max_prefs")

            while (!commonViewModel.getAuthRepository().isLoggedIn()) {
                Log.d("DesiMaxDebug", "Waiting for auth state to be initialized...")
                delay(100L)
            }

            val authState = commonViewModel.getAuthRepository().authStateFlow.first()
            val firstName = authState.userName ?: "User"
            Log.d("DesiMaxDebug", "Fetched initial authState: id=${authState.getId()}, userName=${authState.userName}")

            val vitals = fetchInitialData()
            val age = vitals?.date_of_birth?.let {
                Period.between(LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE), LocalDate.now()).years
            } ?: 30

            homeData.value = HomeData(
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
                stressScore = 0,
                challengeOrAnnouncement = "Weekly Step Challenge!",
                streak = 0,
                showStories = sharedPreferences.getBoolean("show_stories", true),
                storiesOrLeaderboard = listOf("User: 10K steps"),
                maxMessage = MaxMessage("", "", false),
                isTracking = false,
                paused = false,
                dateRangeMode = "Day",
                badges = emptyList(),
                healthVitalsUpdated = vitals != null,
                customStartDate = null, // Initialize new fields
                customEndDate = null
            )

            commonViewModel.getAuthRepository().authStateFlow.collect { updatedAuthState ->
                val updatedFirstName = updatedAuthState.userName ?: "User"
                if (updatedFirstName != homeData.value.firstName) {
                    Log.d("DesiMaxDebug", "AuthState changed, updating firstName to: $updatedFirstName")
                    homeData.update { it.copy(firstName = updatedFirstName) }
                }
            }

            fetchDesiMessagesAndBadges()
            refreshData()
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
            remove("isPaused")
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
                commonViewModel.date
            ) { trackedSteps, bmr, date ->
                StateMetrics(trackedSteps, bmr, homeData.value.isTracking, date)
            }

            combine(healthFlow, stateFlow, _fetchedBadges) { health, state, badges ->
                val hrv = try {
                    healthConnectManager.readHRV(state.date)?.sdnn ?: 0f
                } catch (e: Exception) {
                    Log.w("DesiMaxDebug", "HRV not available: ${e.message}")
                    0f
                }
                val stressScore = if (hrv == 0f) {
                    val sleepScore = when {
                        homeData.value.sleepHours >= 7.5f -> 0f
                        homeData.value.sleepHours >= 6.0f -> 50f
                        else -> 100f
                    }
                    val activityScore = when {
                        (health.steps + state.trackedSteps > 20000 || health.calories > 1000) -> 100f
                        (health.steps + state.trackedSteps > 15000 || health.calories > 500) -> 50f
                        else -> 0f
                    }
                    (0.5f * sleepScore + 0.5f * activityScore).toInt().coerceIn(0, 100)
                } else {
                    when {
                        hrv >= 50 -> 0 // Low
                        hrv >= 20 -> 50 // Medium
                        else -> 100 // High
                    }
                }
                val updated = homeData.value.copy(
                    watchSteps = health.steps,
                    trackedSteps = if (state.isTracking) state.trackedSteps else 0f,
                    hydration = health.hydration,
                    heartRate = health.heartRate,
                    caloriesBurned = health.calories,
                    bmr = state.bmr,
                    stressScore = stressScore
                )
                Log.d("DesiMaxDebug", "Updated homeData: steps=${updated.watchSteps}, trackedSteps=${updated.trackedSteps}, stressScore=${updated.stressScore}, isTracking=${updated.isTracking}")
                updated.copy(badges = assignBadges(updated, badges))
            }.collect { newData ->
                homeData.value = newData
            }
        }
    }

    suspend fun refreshData() {
        Log.d("DesiMaxDebug", "Refreshing home data")
        try {
            val date = commonViewModel.date.value // Use the current date from CommonViewModel
            sleepViewModel.fetchSleepData(date)
            val sleepDataUi = sleepViewModel.sleepData.value
            val sleep = sleepDataUi?.actualSleepTime ?: 0f
            Log.d("DesiMaxDebug", "Fetched sleep hours: $sleep")

            val token = "Bearer ${commonViewModel.getAuthRepository().getAuthState().jwt ?: return}"
            commonViewModel.syncHealthData(token)

            homeData.update {
                it.copy(
                    sleepHours = sleep
                )
            }
            Log.d("DesiMaxDebug", "Updated homeData with sleepHours: $sleep")

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
            }
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
            Log.d("DesiMaxDebug", "Fetching desi messages")
            delay(1000L)

            val token = commonViewModel.getAuthRepository().getAuthState().jwt
            if (token.isNullOrBlank()) {
                Log.e("DesiMaxDebug", "JWT token is null, using fallback message")
                val updatedMax = MaxMessage(
                    "Kal thoda slow tha, aaj full josh! 💪",
                    "Naya din, naya chance — let's go Boss! 🔥",
                    false
                )
                homeData.update { it.copy(maxMessage = updatedMax) }
                saveMaxMessage(updatedMax)
                return@launch
            }

            val authToken = "Bearer $token"
            var messages: List<StrapiApi.DesiMessage> = emptyList()
            for (attempt in 1..2) {
                try {
                    val messagesResponse = withTimeoutOrNull(10000L) {
                        withContext(Dispatchers.IO) {
                            commonViewModel.getStrapiRepository().getDesiMessages(authToken)
                        }
                    }
                    if (messagesResponse == null) {
                        Log.e("DesiMaxDebug", "Desi messages fetch timed out on attempt $attempt")
                        continue
                    }

                    if (messagesResponse.isSuccessful) {
                        val rawMessages = messagesResponse.body()?.data ?: emptyList()
                        messages = rawMessages.filter {
                            it.yesterdayLine != null && it.todayLine != null &&
                                    it.yesterdayLine.isNotBlank() && it.todayLine.isNotBlank()
                        }
                        Log.d("DesiMaxDebug", "Fetched ${messages.size} desi messages")
                        break
                    } else {
                        Log.e("DesiMaxDebug", "Failed to fetch desi messages: ${messagesResponse.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("DesiMaxDebug", "Exception fetching desi messages: ${e.message}", e)
                }
                if (attempt < 2) delay(2000L)
            }

            if (messages.isNotEmpty()) {
                val randomMsg = messages.random()
                val updatedMax = MaxMessage(randomMsg.yesterdayLine, randomMsg.todayLine, false)
                homeData.update { it.copy(maxMessage = updatedMax) }
                saveMaxMessage(updatedMax)
            } else {
                val updatedMax = MaxMessage(
                    "Kal thoda slow tha, aaj full josh! 💪",
                    "Naya din, naya chance — let's go Boss! 🔥",
                    false
                )
                homeData.update { it.copy(maxMessage = updatedMax) }
                saveMaxMessage(updatedMax)
            }

            try {
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
                } else {
                    Log.e("DesiMaxDebug", "Failed to fetch badges: ${badgesResponse?.code()}")
                }
            } catch (e: Exception) {
                Log.e("DesiMaxDebug", "Exception fetching badges: ${e.message}", e)
            }
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
        maxPrefs.edit().apply {
            putString("max_yesterday", maxMessage.yesterday)
            putString("max_today", maxMessage.today)
            putBoolean("max_hasPlayed", maxMessage.hasPlayed)
            apply()
        }
        Log.d("DesiMaxDebug", "Saved max message: yesterday=${maxMessage.yesterday}, today=${maxMessage.today}, hasPlayed=${maxMessage.hasPlayed}")
    }

    fun markMaxMessagePlayed() {
        val updated = homeData.value.maxMessage.copy(hasPlayed = true)
        homeData.update { it.copy(maxMessage = updated) }
        saveMaxMessage(updated)
        Log.d("DesiMaxDebug", "Marked max message as played")
    }

    fun startTracking() {
        if (!homeData.value.isTracking) {
            Log.d("DesiMaxDebug", "Starting tracking")
            // Load session state or start fresh
            val initialSteps = sharedPreferences.getFloat("trackedSteps", 0f)
            val isPaused = sharedPreferences.getBoolean("isPaused", false)
            homeData.update {
                it.copy(
                    isTracking = true,
                    trackedSteps = initialSteps,
                    paused = isPaused
                )
            }
            sharedPreferences.edit().apply {
                putBoolean("isTracking", true)
                apply()
            }
        }
    }

    fun stopTracking() {
        if (homeData.value.isTracking) {
            Log.d("DesiMaxDebug", "Stopping tracking")
            val finalSteps = homeData.value.trackedSteps
            homeData.update { it.copy(isTracking = false, trackedSteps = 0f, paused = false) }
            if (finalSteps > 0) {
                viewModelScope.launch {
                    commonViewModel.syncSteps(finalSteps, LocalDate.now(), "Manual")
                }
            }
            // Clear session state
            sharedPreferences.edit().apply {
                remove("trackedSteps")
                remove("isTracking")
                remove("isPaused")
                apply()
            }
            commonViewModel.updateTrackedSteps(0f)
        }
    }

    fun updateTrackedSteps(steps: Float) {
        if (homeData.value.isTracking) {
            // Accumulate steps to prevent reset
            val currentSteps = homeData.value.trackedSteps
            if (steps >= currentSteps) {
                homeData.update { it.copy(trackedSteps = steps) }
                commonViewModel.updateTrackedSteps(steps)
                sharedPreferences.edit().putFloat("trackedSteps", steps).apply()
                Log.d("DesiMaxDebug", "Updated tracked steps: $steps")
            }
        }
    }

    fun updateDate(newDate: LocalDate) {
        commonViewModel.updateDate(newDate)
        sleepViewModel.fetchSleepData(newDate)
        viewModelScope.launch {
            refreshData()
        }
    }

    fun toggleStoriesOrLeaderboard() {
        val newShowStories = !homeData.value.showStories
        homeData.update { it.copy(showStories = newShowStories) }
        sharedPreferences.edit().putBoolean("show_stories", newShowStories).apply()
    }

    fun setDateRangeMode(mode: String) {
        homeData.update {
            it.copy(
                dateRangeMode = mode,
                customStartDate = if (mode == "Custom") it.customStartDate else null,
                customEndDate = if (mode == "Custom") it.customEndDate else null
            )
        }
    }

    fun setCustomDateRange(start: LocalDate?, end: LocalDate?) {
        if (start != null && end != null && end.isBefore(start)) {
            // Ensure end date is not before start date
            homeData.update { it.copy(customStartDate = start, customEndDate = start) }
            Log.d("DesiMaxDebug", "End date before start, setting end to start: $start")
        } else {
            homeData.update { it.copy(customStartDate = start, customEndDate = end) }
            Log.d("DesiMaxDebug", "Set custom date range: start=$start, end=$end")
        }
        if (start != null && end != null) {
            // Trigger data refresh for the selected range
            viewModelScope.launch {
                refreshData()
            }
        }
    }

    fun onCreateStoryClicked() {
        _navigateToCreateStory.value = true
    }

    fun onNavigationHandled() {
        _navigateToCreateStory.value = false
    }
}

data class HomeData(
    val firstName: String = "User",
    val watchSteps: Float = 0f,
    val manualSteps: Float = 0f,
    val trackedSteps: Float = 0f,
    val stepGoal: Float = 10000f,
    val sleepHours: Float = 0f,
    val caloriesBurned: Float = 0f,
    val heartRate: Float = 0f,
    val maxHeartRate: Float = 200f,
    val hydration: Float = 0f,
    val caloriesLogged: Float = 0f,
    val weightLost: Float = 0f,
    val bmr: Int = 2000,
    val stressScore: Int = 0,
    val challengeOrAnnouncement: String = "Weekly Step Challenge!",
    val streak: Int = 0,
    val showStories: Boolean = true,
    val storiesOrLeaderboard: List<String> = listOf("User: 10K steps"),
    val maxMessage: MaxMessage = MaxMessage("", "", false),
    val isTracking: Boolean = false,
    val paused: Boolean = false,
    val dateRangeMode: String = "Day",
    val badges: List<StrapiApi.Badge> = emptyList(),
    val healthVitalsUpdated: Boolean = false,
    val customStartDate: LocalDate? = null, // New field
    val customEndDate: LocalDate? = null   // New field
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