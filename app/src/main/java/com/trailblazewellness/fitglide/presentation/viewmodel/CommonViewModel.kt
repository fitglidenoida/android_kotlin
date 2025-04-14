package com.trailblazewellness.fitglide.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class CommonViewModel(
    private val context: Context,
    private val strapiRepository: StrapiRepository,
    private val healthConnectManager: HealthConnectManager,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val userId = authRepository.getAuthState().getId() ?: "4"
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("fitglide_prefs", Context.MODE_PRIVATE)

    // ... existing StateFlow declarations (steps, sleepHours, etc.)
    private val _steps = MutableStateFlow(sharedPreferences.getFloat("steps", 0f))
    val steps: StateFlow<Float> = _steps.asStateFlow()

    private val _sleepHours = MutableStateFlow(sharedPreferences.getFloat("sleepHours", 0f))
    val sleepHours: StateFlow<Float> = _sleepHours.asStateFlow()

    private val _hydration = MutableStateFlow(sharedPreferences.getFloat("hydration", 0f))
    val hydration: StateFlow<Float> = _hydration.asStateFlow()

    private val _heartRate = MutableStateFlow(sharedPreferences.getFloat("heartRate", 0f))
    val heartRate: StateFlow<Float> = _heartRate.asStateFlow()

    private val _caloriesBurned = MutableStateFlow(sharedPreferences.getFloat("caloriesBurned", 0f))
    val caloriesBurned: StateFlow<Float> = _caloriesBurned.asStateFlow()

    private val _posts = MutableStateFlow<List<StrapiApi.PostEntry>>(emptyList())
    val posts: StateFlow<List<StrapiApi.PostEntry>> = _posts.asStateFlow()

    private val _friends = MutableStateFlow<List<StrapiApi.FriendEntry>>(emptyList())
    val friends: StateFlow<List<StrapiApi.FriendEntry>> = _friends.asStateFlow()

    private val _cheers = MutableStateFlow<List<StrapiApi.CheerEntry>>(emptyList())
    val cheers: StateFlow<List<StrapiApi.CheerEntry>> = _cheers.asStateFlow()

    private val _packs = MutableStateFlow<List<StrapiApi.PackEntry>>(emptyList())
    val packs: StateFlow<List<StrapiApi.PackEntry>> = _packs.asStateFlow()

    private val _challenges = MutableStateFlow<List<StrapiApi.ChallengeEntry>>(emptyList())
    val challenges: StateFlow<List<StrapiApi.ChallengeEntry>> = _challenges.asStateFlow()

    private val _comments = MutableStateFlow<Map<String, List<StrapiApi.CommentEntry>>>(emptyMap())
    val comments: StateFlow<Map<String, List<StrapiApi.CommentEntry>>> = _comments.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _trackedStepsFlow = MutableStateFlow(sharedPreferences.getFloat("trackedSteps", 0f))
    val trackedStepsFlow: StateFlow<Float> = _trackedStepsFlow.asStateFlow()

    private val _bmr = MutableStateFlow(sharedPreferences.getInt("bmr", 2000))
    val bmr: StateFlow<Int> = _bmr.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    fun getAuthRepository(): AuthRepository = authRepository
    fun getStrapiRepository(): StrapiRepository = strapiRepository

    // New function to post UI messages
    fun postUiMessage(message: String) {
        viewModelScope.launch {
            _uiMessage.value = message
            delay(3000)
            _uiMessage.value = null
        }
    }

    init {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            Log.d("CommonViewModel", "Init started at $startTime")

            val token = waitForAuthToken()
            Log.d("CommonViewModel", "Token fetched in ${System.currentTimeMillis() - startTime}ms")

            syncHealthData(token)
            Log.d("CommonViewModel", "Health data synced in ${System.currentTimeMillis() - startTime}ms")

            syncSleepData(token)
            Log.d("CommonViewModel", "Sleep data synced in ${System.currentTimeMillis() - startTime}ms")

            _isLoading.value = false
            Log.d("CommonViewModel", "Loading complete in ${System.currentTimeMillis() - startTime}ms")

            launch {
                fetchSocialData(token)
                Log.d("CommonViewModel", "Social data fetched in ${System.currentTimeMillis() - startTime}ms")
            }
            launch {
                resyncPastSleepData(token, 8)
                Log.d("CommonViewModel", "Past sleep resynced in ${System.currentTimeMillis() - startTime}ms")
            }
            launch {
                while (true) {
                    _isTracking.value = healthConnectManager.isTracking()
                    delay(5000L)
                }
            }
        }
    }

    private suspend fun waitForAuthToken(): String {
        var jwt = authRepository.getAuthState().jwt
        if (!jwt.isNullOrBlank()) {
            Log.d("CommonViewModel", "JWT already available: $jwt")
            return "Bearer $jwt"
        }
        while (jwt.isNullOrBlank()) {
            Log.d("CommonViewModel", "Waiting for JWT...")
            delay(500L)
            jwt = authRepository.getAuthState().jwt
        }
        Log.d("CommonViewModel", "JWT obtained: $jwt")
        return "Bearer $jwt"
    }

    suspend fun syncHealthData(token: String, userId: String = this.userId) {
        val dateStr = _date.value.atStartOfDay().format(isoFormatter)
        Log.d("CommonViewModel", "Attempting health sync: date=$dateStr, userId=$userId")

        try {
            val existingLogsResponse = strapiRepository.getHealthLog(dateStr, token)
            val existingLogs = if (existingLogsResponse.isSuccessful) {
                existingLogsResponse.body()?.data ?: emptyList()
            } else {
                Log.e("CommonViewModel", "Failed to fetch health logs: ${existingLogsResponse.code()}")
                emptyList()
            }

            existingLogs.firstOrNull()?.let { log ->
                _steps.value = log.steps?.toFloat() ?: 0f
                _hydration.value = log.waterIntake ?: 0f
                _heartRate.value = log.heartRate?.toFloat() ?: 0f
                _caloriesBurned.value = log.caloriesBurned ?: 0f
                sharedPreferences.edit().apply {
                    putFloat("steps", _steps.value)
                    putFloat("hydration", _hydration.value)
                    putFloat("heartRate", _heartRate.value)
                    putFloat("caloriesBurned", _caloriesBurned.value)
                }.apply()
            }

            val vitalsResponse = strapiRepository.getHealthVitals(userId, token)
            val waterGoal = vitalsResponse.body()?.data?.firstOrNull()?.waterGoal ?: 2.5f
            val bmrGoal = vitalsResponse.body()?.data?.firstOrNull()?.calorieGoal ?: 2000
            if (_hydration.value == 0f) _hydration.value = waterGoal
            _bmr.value = bmrGoal
            sharedPreferences.edit().apply {
                putFloat("hydration", _hydration.value)
                putInt("bmr", _bmr.value)
            }.apply()

            val watchSteps = healthConnectManager.readSteps(_date.value).toFloat()
            val trackedSteps = _trackedStepsFlow.value
            val workoutData = healthConnectManager.readExerciseSessions(_date.value)
            val heartRate = workoutData.heartRateAvg ?: healthConnectManager.readDailyHeartRate(_date.value)
            val caloriesBurned = (workoutData.calories ?: 0.0).toFloat()

            if (watchSteps > 0) {
                val response = strapiRepository.syncHealthLog(
                    date = dateStr,
                    steps = watchSteps.toLong(),
                    hydration = _hydration.value,
                    heartRate = heartRate,
                    caloriesBurned = caloriesBurned,
                    source = "HealthConnect",
                    token = token
                )
                if (response.isSuccessful) {
                    _steps.value = watchSteps
                    _heartRate.value = heartRate?.toFloat() ?: 0f
                    _caloriesBurned.value = caloriesBurned
                    sharedPreferences.edit().apply {
                        putFloat("steps", _steps.value)
                        putFloat("heartRate", _heartRate.value)
                        putFloat("caloriesBurned", _caloriesBurned.value)
                    }.apply()
                } else {
                    Log.e("CommonViewModel", "HealthConnect sync failed: ${response.code()}")
                    postUiMessage("Failed to sync health data.")
                }
            }

            if (trackedSteps > 0 && (watchSteps == 0f || trackedSteps > watchSteps)) {
                val manualSteps = if (watchSteps > 0) trackedSteps - watchSteps else trackedSteps
                val response = strapiRepository.syncHealthLog(
                    date = dateStr,
                    steps = manualSteps.toLong(),
                    hydration = 0f,
                    heartRate = null,
                    caloriesBurned = null,
                    source = "Manual",
                    token = token
                )
                if (response.isSuccessful) {
                    _steps.value = watchSteps + manualSteps
                    sharedPreferences.edit().putFloat("steps", _steps.value).apply()
                } else {
                    Log.e("CommonViewModel", "Manual tracking sync failed: ${response.code()}")
                    postUiMessage("Failed to sync manual steps.")
                }
            }
        } catch (e: Exception) {
            Log.e("CommonViewModel", "Error syncing health data: ${e.message}", e)
            postUiMessage("Failed to load data. Please try again.")
        }
    }

    fun isTtsEnabled(): Boolean {
        return sharedPreferences.getBoolean("tts_enabled", true)
    }

    fun updateTrackedSteps(steps: Float) {
        _trackedStepsFlow.value = steps
        sharedPreferences.edit().putFloat("trackedSteps", steps).apply()
        viewModelScope.launch {
            val token = waitForAuthToken()
            syncHealthData(token)
        }
    }

    suspend fun syncSleepData(token: String) {
        val sleepDate = _date.value.minusDays(1)
        val sleepData = healthConnectManager.readSleepSessions(sleepDate)
        val sleepHours = sleepData.total.toMinutes() / 60f

        try {
            val existingLogsResponse = strapiRepository.fetchSleepLog(sleepDate)
            if (existingLogsResponse.isSuccessful && existingLogsResponse.body()?.data?.isNotEmpty() == true) {
                val documentId = existingLogsResponse.body()!!.data.first().documentId
                val response = strapiRepository.updateSleepLog(documentId, sleepData)
                if (response.isSuccessful) {
                    _sleepHours.value = sleepHours
                    sharedPreferences.edit().putFloat("sleepHours", sleepHours).apply()
                }
            } else if (sleepHours > 0) {
                val response = strapiRepository.syncSleepLog(sleepDate, sleepData)
                if (response.isSuccessful) {
                    _sleepHours.value = sleepHours
                    sharedPreferences.edit().putFloat("sleepHours", sleepHours).apply()
                }
            } else {
                _sleepHours.value = 0f
                sharedPreferences.edit().putFloat("sleepHours", 0f).apply()
            }
        } catch (e: Exception) {
            Log.e("CommonViewModel", "Sleep sync error: ${e.message}", e)
            postUiMessage("Failed to sync sleep data.")
        }
    }

    suspend fun fetchSocialData(token: String) {
        try {
            val packsResponse = strapiRepository.getPacks(userId, token)
            if (packsResponse.isSuccessful) _packs.value = packsResponse.body()?.data ?: emptyList()

            val friendsResponse = strapiRepository.getFriends(
                mapOf(
                    "filters[\$or][0][sender][id][\$eq]" to userId,
                    "filters[\$or][1][receiver][id][\$eq]" to userId
                ),
                token
            )
            if (friendsResponse.isSuccessful) _friends.value = friendsResponse.body()?.data ?: emptyList()

            val packId = _packs.value.firstOrNull()?.id
            val postsResponse = strapiRepository.getPosts(packId, token)
            if (postsResponse.isSuccessful) _posts.value = postsResponse.body()?.data ?: emptyList()

            val cheersResponse = strapiRepository.getCheers(userId, token)
            if (cheersResponse.isSuccessful) _cheers.value = cheersResponse.body()?.data ?: emptyList()

            val challengesResponse = strapiRepository.getChallenges(userId, token)
            if (challengesResponse.isSuccessful) _challenges.value = challengesResponse.body()?.data ?: emptyList()

            _posts.value.forEach { post ->
                val commentsResponse = strapiRepository.getComments(post.id, token)
                if (commentsResponse.isSuccessful) {
                    _comments.value = _comments.value + (post.id to (commentsResponse.body()?.data ?: emptyList()))
                }
            }
        } catch (e: Exception) {
            Log.e("CommonViewModel", "Social fetch error: ${e.message}", e)
            postUiMessage("Failed to load social data.")
        }
    }

    fun updateDate(newDate: LocalDate) {
        _date.value = newDate
        viewModelScope.launch {
            _isLoading.value = true
            val token = waitForAuthToken()
            syncHealthData(token)
            syncSleepData(token)
            _isLoading.value = false
        }
    }

    fun inviteFriend(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = waitForAuthToken()
            val existingInvite = _friends.value.find { it.friendEmail == email && it.friendsStatus == "Pending" }
            if (existingInvite != null) {
                postUiMessage("Invite already pending for $email")
                return@launch
            }
            val request = StrapiApi.FriendRequest(
                sender = StrapiApi.UserId(userId),
                receiver = null,
                friendEmail = email,
                friendsStatus = "Pending",
                inviteToken = UUID.randomUUID().toString()
            )
            val response = strapiRepository.postFriend(request, token)
            if (response.isSuccessful) {
                _friends.value = _friends.value + (response.body()?.data ?: return@launch)
                postUiMessage("Invite sent to $email!")
            } else {
                postUiMessage("Failed to send invite: ${response.errorBody()?.string()}")
            }
        }
    }

    fun updateFriend(friendId: String, newStatus: String) {
        viewModelScope.launch {
            val friend = friends.value.find { it.id == friendId } ?: return@launch
            val token = waitForAuthToken()
            val request = StrapiApi.FriendRequest(
                sender = friend.sender?.data ?: StrapiApi.UserId("1"),
                receiver = friend.receiver?.data,
                friendEmail = friend.friendEmail,
                friendsStatus = newStatus,
                inviteToken = friend.inviteToken
            )
            val response = strapiRepository.updateFriend(friendId, request, token)
            if (response.isSuccessful) {
                fetchSocialData(token)
            } else {
                postUiMessage("Failed to update friend status.")
            }
        }
    }

    suspend fun resyncPastSleepData(token: String, daysBack: Int = 7) {
        val today = LocalDate.now()
        for (i in 1..daysBack) {
            val pastDate = today.minusDays(i.toLong())
            val sleepData = healthConnectManager.readSleepSessions(pastDate)
            val existingLogsResponse = strapiRepository.fetchSleepLog(pastDate)
            if (existingLogsResponse.isSuccessful && existingLogsResponse.body()?.data?.isNotEmpty() == true) {
                val documentId = existingLogsResponse.body()!!.data.first().documentId
                val response = strapiRepository.updateSleepLog(documentId, sleepData)
                if (response.isSuccessful) {
                    Log.d("CommonViewModel", "Updated sleep for $pastDate")
                }
            } else if (sleepData.total > Duration.ZERO) {
                val response = strapiRepository.syncSleepLog(pastDate, sleepData)
                if (response.isSuccessful) {
                    Log.d("CommonViewModel", "Synced new sleep for $pastDate")
                }
            }
        }
    }

    fun postComment(postId: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = waitForAuthToken()
            val request = StrapiApi.CommentEntry(
                id = "",
                post = StrapiApi.UserId(postId),
                user = StrapiApi.UserId(userId),
                text = text,
                createdAt = ""
            )
            val response = strapiRepository.postComment(request, token)
            if (response.isSuccessful) {
                val newComments = response.body()?.data ?: emptyList()
                _comments.value = _comments.value + (postId to (_comments.value[postId]?.plus(newComments) ?: newComments))
                postUiMessage("Comment posted!")
            } else {
                postUiMessage("Failed to post comment: ${response.errorBody()?.string()}")
            }
        }
    }

    fun postCheer(receiverId: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = waitForAuthToken()
            val request = StrapiApi.CheerRequest(
                sender = StrapiApi.UserId(userId),
                receiver = StrapiApi.UserId(receiverId),
                message = message
            )
            val response = strapiRepository.postCheer(request, token)
            if (response.isSuccessful) {
                _cheers.value = _cheers.value + (response.body()?.data ?: return@launch)
                postUiMessage("Cheer sent!")
            } else {
                postUiMessage("Failed to send cheer: ${response.errorBody()?.string()}")
            }
        }
    }

    fun logWaterIntake(volume: Float = 0.25f) {
        viewModelScope.launch {
            healthConnectManager.logHydration(_date.value, volume.toDouble())
            _hydration.value += volume
            sharedPreferences.edit().putFloat("hydration", _hydration.value).apply()
            syncHealthData(waitForAuthToken())
        }
    }
}