package com.trailblazewellness.fitglide.presentation.strava

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class StravaAuthViewModel(
    private val strapiApi: StrapiApi,
    private val commonViewModel: CommonViewModel,
    private val context: Context,
    private val strapiRepository: StrapiRepository
) : ViewModel() {
    private val sharedPreferences =
        context.getSharedPreferences("strava_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<StravaAuthState>(StravaAuthState.Idle)
    val authState: StateFlow<StravaAuthState> = _authState.asStateFlow()

    private val _isStravaConnected = MutableStateFlow(
        sharedPreferences.getString("strava_access_token", null) != null &&
                sharedPreferences.getLong("strava_expires_at", 0) > System.currentTimeMillis() / 1000
    )
    val isStravaConnected: StateFlow<Boolean> = _isStravaConnected.asStateFlow()

    private var csrfState: String? = null
    private var athleteId: Long? = null

    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 60_000L // 60 seconds, initialized to fix compilation error

    sealed class StravaAuthState {
        object Idle : StravaAuthState()
        object Loading : StravaAuthState()
        data class AuthUrl(val url: String) : StravaAuthState()
        object Success : StravaAuthState()
        data class Synced(val count: Int) : StravaAuthState()
        data class Error(val message: String) : StravaAuthState()
    }

    // Define the StravaTokenResponse locally within StravaAuthViewModel.kt
    data class StravaTokenResponse(
        val access_token: String,
        val refresh_token: String,
        val expires_at: Long
    )

    fun initiateStravaAuth() {
        if (_isStravaConnected.value) {
            _authState.value = StravaAuthState.Error("Already connected to Strava")
            return
        }
        viewModelScope.launch {
            _authState.value = StravaAuthState.Loading
            try {
                // Fetch the user's ID to include in the state
                val token = commonViewModel.getAuthRepository().getAuthState().jwt
                    ?: throw IllegalStateException("Please log in to connect Strava")
                val userResponse = strapiApi.getUserProfile("Bearer $token")
                if (!userResponse.isSuccessful || userResponse.body() == null) {
                    Log.e("StravaAuthViewModel", "Failed to fetch user profile: Code ${userResponse.code()}")
                    _authState.value = StravaAuthState.Error("Failed to fetch user profile: Code ${userResponse.code()}")
                    return@launch
                }
                val userId = userResponse.body()!!.id

                // Generate a random state for CSRF protection and include the user ID
                val randomState = UUID.randomUUID().toString()
                csrfState = "${userId}:${randomState}" // Format: userId:randomState
                Log.d("StravaAuthViewModel", "Generated csrfState: $csrfState")

                // Fetch the Strava OAuth authorization URL from Strapi with state parameter
                val response = strapiRepository.initiateStravaAuth(state = csrfState!!)
                if (response.isSuccessful && response.body() != null) {
                    val authUrl = response.body()!!.redirectUrl
                    Log.d("StravaAuthViewModel", "Fetched Strava auth URL from Strapi: $authUrl")
                    _authState.value = StravaAuthState.AuthUrl(authUrl)
                } else {
                    Log.e("StravaAuthViewModel", "Failed to get Strava auth URL: Code ${response.code()}")
                    _authState.value = StravaAuthState.Error("Failed to get Strava auth URL (Code: ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("StravaAuthViewModel", "Auth error: ${e.message}", e)
                _authState.value = StravaAuthState.Error("Unable to connect to Strava: ${e.message}")
            }
        }
    }

    fun handleStravaCallback(accessToken: String, refreshToken: String, expiresAt: Long, athleteId: Long) {
        Log.d("StravaAuthViewModel", "Received tokens: accessToken=$accessToken, refreshToken=$refreshToken, expiresAt=$expiresAt, athleteId=$athleteId")

        viewModelScope.launch {
            _authState.value = StravaAuthState.Loading
            Log.d("StravaAuthViewModel", "State set to Loading")
            try {
                // Save the token details locally using the local StravaTokenResponse class
                val tokenResponse = StravaTokenResponse(
                    access_token = accessToken,
                    refresh_token = refreshToken,
                    expires_at = expiresAt
                )
                saveTokens(tokenResponse)

                // Store the athlete ID
                this@StravaAuthViewModel.athleteId = athleteId

                // Update the user's profile in Strapi with the athlete ID
                val token = commonViewModel.getAuthRepository().getAuthState().jwt
                    ?: throw IllegalStateException("Please log in to connect Strava")
                Log.d("StravaAuthViewModel", "JWT Token for update: $token")

                // Fetch the user's ID using GET /api/users/me
                val userResponse = strapiApi.getUserProfile("Bearer $token")
                if (userResponse.isSuccessful && userResponse.body() != null) {
                    val userId = userResponse.body()!!.id
                    Log.d("StravaAuthViewModel", "Fetched user profile: userId=$userId, response: ${userResponse.body()}")

                    // Use UserProfileRequest directly to update the profile
                    val updateRequest = StrapiApi.UserProfileRequest(
                        athlete_id = athleteId.toString(),
                        strava_connected = true
                    )
                    Log.d("StravaAuthViewModel", "Update request payload: athlete_id=${updateRequest.athlete_id}, strava_connected=${updateRequest.strava_connected}")

                    val updateResponse = strapiApi.updateUserProfile(
                        id = userId,
                        body = updateRequest,
                        token = "Bearer $token"
                    )
                    Log.d("StravaAuthViewModel", "Update response status: ${updateResponse.code()}, body: ${updateResponse.body()}")

                    if (updateResponse.isSuccessful) {
                        Log.d("StravaAuthViewModel", "Updated user profile with athlete_id: $athleteId, response: ${updateResponse.body()}")
                    } else {
                        Log.e("StravaAuthViewModel", "Failed to update user profile: Code ${updateResponse.code()}, message: ${updateResponse.message()}")
                        _authState.value = StravaAuthState.Error("Failed to update user profile: Code ${updateResponse.code()}")
                        Log.d("StravaAuthViewModel", "State set to Error: Failed to update user profile")
                        return@launch
                    }
                } else {
                    Log.e("StravaAuthViewModel", "Failed to fetch user profile: Code ${userResponse.code()}, message: ${userResponse.message()}")
                    _authState.value = StravaAuthState.Error("Failed to fetch user profile: Code ${userResponse.code()}")
                    Log.d("StravaAuthViewModel", "State set to Error: Failed to fetch user profile")
                    return@launch
                }

                _authState.value = StravaAuthState.Success
                _isStravaConnected.value = true
                Log.d("StravaAuthViewModel", "Set state to Success, isStravaConnected=true")
                syncActivities() // Auto-sync after connect
            } catch (e: Exception) {
                Log.e("StravaAuthViewModel", "Token processing error: ${e.message}, stacktrace: ${e.stackTraceToString()}")
                _authState.value = StravaAuthState.Error("Unable to process tokens: ${e.message}")
                Log.d("StravaAuthViewModel", "State set to Error: Unable to process tokens")
            }
        }
    }

    fun syncActivities() {
        viewModelScope.launch {
            _authState.value = StravaAuthState.Loading
            Log.d("StravaAuthViewModel", "State set to Loading for sync")
            try {
                val token = commonViewModel.getAuthRepository().getAuthState().jwt
                    ?: throw IllegalStateException("Please log in to sync Strava")
                if (!isTokenValid()) {
                    Log.w("StravaAuthViewModel", "Token invalid, prompting user to reconnect")
                    _authState.value = StravaAuthState.Error("Strava session expired. Please reconnect.")
                    Log.d("StravaAuthViewModel", "State set to Error: Strava session expired")
                    disconnectStrava()
                    return@launch
                }
                Log.d("StravaAuthViewModel", "Starting Strava activity sync")

                // Fetch Strava activities (retry logic for rate limiting)
                val activities = fetchActivitiesWithRetry(0)
                Log.d("StravaAuthViewModel", "Fetched ${activities.size} Strava activities")

                // Process each activity
                val userId = commonViewModel.getAuthRepository().getAuthState().getId()
                    ?: throw IllegalStateException("User ID not found")
                var syncedCount = 0

                for (activity in activities) {
                    Log.d("StravaAuthViewModel", "Processing activity with ID: ${activity.id}")

                    // Fetch detailed activity data
                    val detailedActivity = fetchDetailedActivity(activity.id)
                    if (detailedActivity == null) {
                        Log.w("StravaAuthViewModel", "Failed to fetch detailed data for activity: ${activity.id}, skipping")
                        continue
                    }

                    // Verify the activity belongs to the user
                    if (athleteId != detailedActivity.athlete?.id) {
                        Log.w("StravaAuthViewModel", "Activity ${activity.id} does not belong to athlete $athleteId, skipping")
                        continue
                    }

                    // Deduplicate by checking if the activity already exists in workout-logs
                    val logId = "strava_${activity.id}"
                    val existingLogs = strapiRepository.getWorkoutLogs(
                        userId = userId,
                        date = detailedActivity.start_date.split("T")[0], // Use the date part of start_date
                        token = "Bearer $token"
                    )
                    if (existingLogs.isSuccessful && existingLogs.body()?.data?.any { it.logId == logId } == true) {
                        Log.d("StravaAuthViewModel", "Activity with ID $logId already exists, skipping")
                        continue
                    }

                    // Handle missing coordinates more robustly
                    val startLatLng = if (detailedActivity.start_latlng?.size == 2) detailedActivity.start_latlng else listOf(0.0f, 0.0f)
                    val endLatLng = if (detailedActivity.end_latlng?.size == 2) detailedActivity.end_latlng else listOf(0.0f, 0.0f)

                    // Map route coordinates (simplified: using start and end points)
                    val route = listOf(
                        mapOf("lat" to startLatLng[0], "lng" to startLatLng[1]),
                        mapOf("lat" to endLatLng[0], "lng" to endLatLng[1])
                    )

                    // Map to WorkoutLogRequest using detailedActivity for all fields
                    val workoutLog = StrapiApi.WorkoutLogRequest(
                        logId = logId,
                        workout = null,
                        startTime = detailedActivity.start_date,
                        endTime = detailedActivity.start_date_local,
                        distance = detailedActivity.distance / 1000f, // Convert meters to kilometers
                        totalTime = detailedActivity.moving_time / 3600f, // Convert seconds to hours
                        calories = detailedActivity.calories ?: 0f, // Already Float? in DetailedStravaActivity
                        heartRateAverage = detailedActivity.average_heartrate?.toLong() ?: 0L,
                        heartRateMaximum = detailedActivity.max_heartrate?.toLong() ?: 0L,
                        heartRateMinimum = 0L,
                        route = route,
                        completed = true,
                        notes = "Synced from Strava: ${detailedActivity.name}",
                        usersPermissionsUser = StrapiApi.UserId(userId)
                    )
                    Log.d("StravaAuthViewModel", "Syncing workout log with source=strava: $workoutLog")

                    // Sync the activity
                    val logResponse = strapiRepository.syncWorkoutLog(workoutLog, "Bearer $token")
                    if (logResponse.isSuccessful) {
                        syncedCount++
                        Log.d("StravaAuthViewModel", "Saved workout log for activity: ${activity.id}")
                    } else {
                        Log.w("StravaAuthViewModel", "Failed to save workout log for activity: ${activity.id}, code: ${logResponse.code()}")
                    }
                }

                Log.d("StravaAuthViewModel", "Synced $syncedCount Strava activities")
                _authState.value = StravaAuthState.Synced(syncedCount)
                Log.d("StravaAuthViewModel", "State set to Synced: $syncedCount activities")
            } catch (e: TimeoutCancellationException) {
                Log.e("StravaAuthViewModel", "Sync timed out after 30 seconds", e)
                _authState.value = StravaAuthState.Error("Sync timed out. Please try again.")
                Log.d("StravaAuthViewModel", "State set to Error: Sync timed out")
            } catch (e: Exception) {
                Log.e("StravaAuthViewModel", "Sync error: ${e.message}", e)
                _authState.value = StravaAuthState.Error("Unable to sync activities: ${e.message}")
                Log.d("StravaAuthViewModel", "State set to Error: Unable to sync activities")
            }
        }
    }

    private suspend fun fetchActivitiesWithRetry(retryCount: Int): List<StravaActivity> {
        val accessToken = sharedPreferences.getString("strava_access_token", "")
        Log.d("StravaAuthViewModel", "Using access token: $accessToken")

        try {
            return withTimeout(30000) { // 30 seconds timeout
                withContext(Dispatchers.IO) { // Run network call on IO thread
                    val client = OkHttpClient()
                    val currentTime = System.currentTimeMillis() / 1000 // Current Unix timestamp in seconds
                    val request = Request.Builder()
                        .url("https://www.strava.com/api/v3/athlete/activities?before=$currentTime&per_page=100")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        if (response.code == 401) {
                            Log.w("StravaAuthViewModel", "Received 401 Unauthorized, prompting user to reconnect")
                            throw Exception("Strava session expired. Please reconnect (Ask Gemini).")
                        } else if (response.code == 429 && retryCount < MAX_RETRIES) {
                            Log.w("StravaAuthViewModel", "Rate limit exceeded (429), retrying after 60 seconds (attempt ${retryCount + 1}/$MAX_RETRIES)")
                            delay(RETRY_DELAY_MS)
                            return@withContext fetchActivitiesWithRetry(retryCount + 1)
                        }
                        val errorBody = response.body?.string() ?: "No error body"
                        Log.e("StravaAuthViewModel", "Strava API error response: $errorBody")
                        throw Exception("Failed to fetch Strava activities: Code ${response.code}, message: $errorBody")
                    }

                    val activitiesJson = response.body?.string() ?: "[]"
                    Gson().fromJson(activitiesJson, Array<StravaActivity>::class.java).toList()
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("Strava session expired") == true) {
                disconnectStrava()
            }
            throw e
        }
    }

    private suspend fun fetchDetailedActivity(activityId: Long): DetailedStravaActivity? {
        val accessToken = sharedPreferences.getString("strava_access_token", "")
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://www.strava.com/api/v3/activities/$activityId")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    Log.w("StravaAuthViewModel", "Received 401 Unauthorized while fetching detailed activity, prompting user to reconnect")
                    throw Exception("Strava session expired. Please reconnect (Ask Gemini).")
                }
                val errorBody = response.body?.string() ?: "No error body"
                Log.e("StravaAuthViewModel", "Failed to fetch detailed activity $activityId: Code ${response.code}, message: $errorBody")
                return@withContext null
            }

            val activityJson = response.body?.string() ?: return@withContext null
            Gson().fromJson(activityJson, DetailedStravaActivity::class.java)
        }
    }

    private fun saveTokens(tokenResponse: StravaTokenResponse) {
        sharedPreferences.edit().apply {
            putString("strava_access_token", tokenResponse.access_token)
            putString("strava_refresh_token", tokenResponse.refresh_token)
            putLong("strava_expires_at", tokenResponse.expires_at)
            apply()
        }
        Log.d("StravaAuthViewModel", "Saved tokens: expires_at=${tokenResponse.expires_at}")
    }

    private fun isTokenValid(): Boolean {
        val expiresAt = sharedPreferences.getLong("strava_expires_at", 0)
        val isValid = expiresAt > System.currentTimeMillis() / 1000 + 300 // 5-min buffer
        Log.d("StravaAuthViewModel", "Token valid: $isValid, expires_at=$expiresAt")
        return isValid
    }

    fun disconnectStrava() {
        sharedPreferences.edit().apply {
            remove("strava_access_token")
            remove("strava_refresh_token")
            remove("strava_expires_at")
            apply()
        }
        athleteId = null
        _isStravaConnected.value = false
        _authState.value = StravaAuthState.Idle
        Log.d("StravaAuthViewModel", "Disconnected Strava")
    }
}

// Temporary data class to parse Strava activities (summary)
data class StravaActivity(
    val id: Long,
    val name: String,
    val start_date: String,
    val end_date_local: String?,
    val distance: Float,
    val moving_time: Long,
    val calories: Int?,
    val average_heartrate: Float?,
    val max_heartrate: Float?
)

// Temporary data class to parse detailed Strava activities
data class DetailedStravaActivity(
    val id: Long,
    val name: String,
    val start_date: String,
    val start_date_local: String,
    val distance: Float,
    val moving_time: Long,
    val elapsed_time: Long,
    val total_elevation_gain: Float,
    val type: String,
    val elev_high: Float?,
    val timezone: String,
    val gear_id: String?,
    val gear: Any?, // Can be null or an object, depending on the gear
    val kilojoules: Float?,
    val max_watts: Int?,
    val weighted_average_watts: Int?,
    val calories: Float?, // Changed from Int? to Float? to handle decimal values
    val average_speed: Float,
    val max_speed: Float,
    val description: String?,
    val achievement_count: Int,
    val kudos_count: Int,
    val upload_id_str: String?,
    val photos: Any?, // Can be null or an object
    val workout_type: Int?,
    val device_watts: Boolean?,
    val athlete: Athlete,
    val start_latlng: List<Float>?, // [lat, lng]
    val end_latlng: List<Float>?, // [lat, lng]
    val average_heartrate: Float?,
    val max_heartrate: Float?
)

data class Athlete(
    val id: Long
)