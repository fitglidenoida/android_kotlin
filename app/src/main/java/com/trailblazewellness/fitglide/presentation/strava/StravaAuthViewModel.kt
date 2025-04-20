package com.trailblazewellness.fitglide.presentation.strava

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class StravaAuthViewModel @Inject constructor(
    private val strapiApi: StrapiApi,
    private val commonViewModel: CommonViewModel,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val sharedPreferences = context.getSharedPreferences("strava_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<StravaAuthState>(StravaAuthState.Idle)
    val authState: StateFlow<StravaAuthState> = _authState.asStateFlow()

    private val _isStravaConnected = MutableStateFlow(
        sharedPreferences.getString("strava_access_token", null) != null &&
                sharedPreferences.getLong("strava_expires_at", 0) > System.currentTimeMillis() / 1000
    )
    val isStravaConnected: StateFlow<Boolean> = _isStravaConnected.asStateFlow()

    sealed class StravaAuthState {
        object Idle : StravaAuthState()
        object Loading : StravaAuthState()
        data class AuthUrl(val url: String) : StravaAuthState()
        object Success : StravaAuthState()
        data class Synced(val count: Int) : StravaAuthState()
        data class Error(val message: String) : StravaAuthState()
    }

    fun initiateStravaAuth() {
        if (_isStravaConnected.value) {
            _authState.value = StravaAuthState.Error("Already connected to Strava")
            return
        }
        viewModelScope.launch {
            _authState.value = StravaAuthState.Loading
            try {
                val token = commonViewModel.getAuthRepository().getAuthState().jwt
                    ?: throw IllegalStateException("Please log in to connect Strava")
                val response = strapiApi.initiateStravaAuth("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val redirectUrl = response.body()?.redirectUrl
                    if (redirectUrl != null) {
                        _authState.value = StravaAuthState.AuthUrl(redirectUrl)
                    } else {
                        _authState.value = StravaAuthState.Error("Failed to get Strava auth URL")
                    }
                } else {
                    _authState.value = StravaAuthState.Error("Failed to initiate Strava auth")
                }
            } catch (e: Exception) {
                _authState.value = StravaAuthState.Error("Error initiating Strava auth: ${e.message}")
            }
        }
    }

    fun syncStravaActivities() {
        viewModelScope.launch {
            _authState.value = StravaAuthState.Loading
            try {
                val token = commonViewModel.getAuthRepository().getAuthState().jwt
                    ?: throw IllegalStateException("Please log in to sync Strava activities")
                val response = strapiApi.syncStravaActivities(10, "Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val activities = response.body()!!.data // Get the list of StravaActivity
                    activities.forEach { activity ->
                        // Log the activity using the correct field, which is `activity_id`
                        Log.d("StravaActivity", "Activity ID: ${activity.activity_id}")
                    }
                    _authState.value = StravaAuthState.Synced(activities.size)
                } else {
                    _authState.value = StravaAuthState.Error("Failed to sync Strava activities")
                }
            } catch (e: Exception) {
                _authState.value = StravaAuthState.Error("Error syncing Strava activities: ${e.message}")
            }
        }
    }
    fun disconnectStrava() {
        viewModelScope.launch {
            try {
                // Clear stored Strava authentication tokens
                sharedPreferences.edit().remove("strava_access_token").apply()
                sharedPreferences.edit().remove("strava_expires_at").apply()

                // Update the state to reflect that Strava is disconnected
                _isStravaConnected.value = false
                _authState.value = StravaAuthState.Success // Or another appropriate state
            } catch (e: Exception) {
                _authState.value = StravaAuthState.Error("Error disconnecting from Strava: ${e.message}")
            }
        }

    }}
