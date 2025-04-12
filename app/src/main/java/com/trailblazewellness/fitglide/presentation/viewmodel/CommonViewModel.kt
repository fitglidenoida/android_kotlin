package com.trailblazewellness.fitglide.presentation.viewmodel

import android.content.Context
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

    private val _steps = MutableStateFlow(0f)
    val steps: StateFlow<Float> = _steps.asStateFlow()

    private val _sleepHours = MutableStateFlow(0f)
    val sleepHours: StateFlow<Float> = _sleepHours.asStateFlow()

    private val _hydration = MutableStateFlow(0f)
    val hydration: StateFlow<Float> = _hydration.asStateFlow()

    private val _heartRate = MutableStateFlow(0f)
    val heartRate: StateFlow<Float> = _heartRate.asStateFlow()

    private val _caloriesBurned = MutableStateFlow(0f)
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

    private val _trackedStepsFlow = MutableStateFlow(0f)
    val trackedStepsFlow: StateFlow<Float> = _trackedStepsFlow.asStateFlow()

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    fun getAuthRepository(): AuthRepository = authRepository
    fun getStrapiRepository(): StrapiRepository = strapiRepository

    init {
        viewModelScope.launch {
            val token = waitForAuthToken()
            syncHealthData(token)
            syncSleepData(token)
            fetchSocialData(token)
            resyncPastSleepData(token, 8)
            launch {
                while (true) {
                    _isTracking.value = healthConnectManager.isTracking()
                    delay(5000L)
                }
            }
        }
    }

    private suspend fun waitForAuthToken(): String {
        while (authRepository.getAuthState().jwt.isNullOrBlank()) {
            Log.d("CommonViewModel", "Waiting for JWT...")
            delay(500L)
        }
        val jwt = authRepository.getAuthState().jwt!!
        Log.d("CommonViewModel", "JWT obtained: $jwt")
        return "Bearer $jwt"
    }

    suspend fun syncHealthData(token: String, userId: String = this.userId) {
        val dateStr = _date.value.atStartOfDay().format(isoFormatter)
        val watchSteps = healthConnectManager.readSteps(_date.value).toFloat()
        val trackedSteps = _trackedStepsFlow.value
        val workoutData = healthConnectManager.readExerciseSessions(_date.value)
        val heartRate = workoutData.heartRateAvg ?: healthConnectManager.readDailyHeartRate(_date.value)
        val caloriesBurned = (workoutData.calories ?: 0.0).toFloat()

        try {
            // Watch data takes precedence if both are present and simultaneous
            if (watchSteps > 0) {
                val watchResponse = strapiRepository.syncHealthLog(
                    date = dateStr,
                    steps = watchSteps.toLong(),
                    hydration = _hydration.value,
                    heartRate = heartRate,
                    source = "HealthConnect",
                    token = token
                )
                if (watchResponse.isSuccessful) {
                    _steps.value = watchSteps
                    _hydration.value = watchResponse.body()?.data?.waterIntake?.toFloat() ?: _hydration.value
                    _heartRate.value = heartRate?.toFloat() ?: 0f
                    _caloriesBurned.value = caloriesBurned
                    Log.d("CommonViewModel", "Watch sync successful: ${watchResponse.body()}")
                } else {
                    Log.e("CommonViewModel", "Watch sync failed: ${watchResponse.code()} - ${watchResponse.errorBody()?.string()}")
                }
            }

            // Add manual steps if no watch data or if trackedSteps is additional
            if (trackedSteps > 0 && (watchSteps == 0f || trackedSteps > watchSteps)) {
                val stepsToAdd = if (watchSteps > 0) trackedSteps - watchSteps else trackedSteps
                val trackedResponse = strapiRepository.syncHealthLog(
                    date = dateStr,
                    steps = stepsToAdd.toLong(),
                    hydration = 0f,
                    heartRate = null,
                    source = "Manual",
                    token = token
                )
                if (trackedResponse.isSuccessful) {
                    _steps.value = watchSteps + stepsToAdd
                    Log.d("CommonViewModel", "Tracked sync successful: ${trackedResponse.body()}")
                } else {
                    Log.e("CommonViewModel", "Tracked sync failed: ${trackedResponse.code()} - ${trackedResponse.errorBody()?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e("CommonViewModel", "Health sync error: ${e.message}", e)
        }
    }

    fun isTtsEnabled(): Boolean {
        val prefs = context.getSharedPreferences("fitglide_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("tts_enabled", true) // default true
    }

    fun updateTrackedSteps(steps: Float) {
        _trackedStepsFlow.value = steps
        viewModelScope.launch {
            val token = waitForAuthToken()
            syncHealthData(token)
        }
        Log.d("CommonViewModel", "Updated tracked steps: $steps")
    }

    suspend fun syncSleepData(token: String) {
        val sleepDate = _date.value.minusDays(1)
        val sleepData = healthConnectManager.readSleepSessions(sleepDate)
        val sleepHours = sleepData.total.toMinutes() / 60f
        Log.d("CommonViewModel", "Raw sleep data for $sleepDate: total=${sleepData.total}, start=${sleepData.start}, end=${sleepData.end}")

        try {
            val existingLogsResponse = strapiRepository.fetchSleepLog(sleepDate)
            if (existingLogsResponse.isSuccessful && existingLogsResponse.body()?.data?.isNotEmpty() == true) {
                val documentId = existingLogsResponse.body()!!.data.first().documentId
                val response = strapiRepository.updateSleepLog(documentId, sleepData)
                if (response.isSuccessful) {
                    _sleepHours.value = sleepHours
                    Log.d("CommonViewModel", "Updated sleep for $sleepDate: $sleepHours hours")
                } else {
                    Log.e("CommonViewModel", "Sleep update failed: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } else if (sleepHours > 0) {
                val response = strapiRepository.syncSleepLog(sleepDate, sleepData)
                if (response.isSuccessful) {
                    _sleepHours.value = sleepHours
                    Log.d("CommonViewModel", "Synced new sleep for $sleepDate: $sleepHours hours")
                } else {
                    Log.e("CommonViewModel", "Sleep sync failed: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } else {
                _sleepHours.value = 0f
                Log.d("CommonViewModel", "No sleep data for $sleepDate (hours: $sleepHours)")
            }
        } catch (e: Exception) {
            Log.e("CommonViewModel", "Sleep sync error: ${e.message}", e)
        }
    }

    suspend fun fetchSocialData(token: String) {
        try {
            Log.d("CommonViewModel", "Fetching social data for userId: $userId with token: $token")

            val packsResponse = strapiRepository.getPacks(userId, token)
            if (packsResponse.isSuccessful) {
                _packs.value = packsResponse.body()?.data ?: emptyList()
            }

            val friendsResponse = strapiRepository.getFriends(
                mapOf(
                    "filters[\$or][0][sender][id][\$eq]" to userId,
                    "filters[\$or][1][receiver][id][\$eq]" to userId
                ),
                token
            )
            if (friendsResponse.isSuccessful) {
                _friends.value = friendsResponse.body()?.data ?: emptyList()
            }

            val packId = _packs.value.firstOrNull()?.id
            val postsResponse = strapiRepository.getPosts(packId, token)
            if (postsResponse.isSuccessful) {
                _posts.value = postsResponse.body()?.data ?: emptyList()
            }

            val cheersResponse = strapiRepository.getCheers(userId, token)
            if (cheersResponse.isSuccessful) {
                _cheers.value = cheersResponse.body()?.data ?: emptyList()
            }

            val challengesResponse = strapiRepository.getChallenges(userId, token)
            if (challengesResponse.isSuccessful) {
                _challenges.value = challengesResponse.body()?.data ?: emptyList()
            }

            _posts.value.forEach { post ->
                val commentsResponse = strapiRepository.getComments(post.id, token)
                if (commentsResponse.isSuccessful) {
                    _comments.value = _comments.value + (post.id to (commentsResponse.body()?.data ?: emptyList()))
                }
            }
        } catch (e: Exception) {
            Log.e("CommonViewModel", "Social fetch error: ${e.message}", e)
        }
    }

    fun updateDate(newDate: LocalDate) {
        _date.value = newDate
        viewModelScope.launch {
            val token = waitForAuthToken()
            syncHealthData(token)
            syncSleepData(token)
            fetchSocialData(token)
        }
    }

    fun inviteFriend(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = waitForAuthToken()
            val existingInvite = _friends.value.find { it.friendEmail == email && it.friendsStatus == "Pending" }
            if (existingInvite != null) {
                _uiMessage.value = "Invite already pending for $email"
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
                _uiMessage.value = "Invite sent to $email!"
            } else {
                _uiMessage.value = "Failed: ${response.errorBody()?.string()}"
            }
            delay(3000)
            _uiMessage.value = null
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
                Log.d("CommonViewModel", "Friend updated: $newStatus")
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
                    Log.d("CommonViewModel", "Updated sleep for $pastDate: ${sleepData.total.toMinutes() / 60f} hours")
                }
            } else if (sleepData.total > Duration.ZERO) {
                val response = strapiRepository.syncSleepLog(pastDate, sleepData)
                if (response.isSuccessful) {
                    Log.d("CommonViewModel", "Synced new sleep for $pastDate: ${sleepData.total.toMinutes() / 60f} hours")
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
                _uiMessage.value = "Comment posted!"
            } else {
                _uiMessage.value = "Comment failed: ${response.errorBody()?.string()}"
            }
            delay(3000)
            _uiMessage.value = null
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
                _uiMessage.value = "Cheer sent!"
            } else {
                _uiMessage.value = "Cheer failed: ${response.errorBody()?.string()}"
            }
            delay(3000)
            _uiMessage.value = null
        }
    }

    fun logWaterIntake(volume: Float = 0.25f) {
        viewModelScope.launch {
            healthConnectManager.logHydration(_date.value, volume.toDouble())
            _hydration.value += volume
            syncHealthData(waitForAuthToken())
        }
    }
}