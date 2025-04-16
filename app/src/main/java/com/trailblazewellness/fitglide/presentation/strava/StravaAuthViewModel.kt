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
    private val sharedPreferences =
        context.getSharedPreferences("strava_prefs", Context.MODE_PRIVATE)

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
                    _authState.value = StravaAuthState.AuthUrl(response.body()!!.redirectUrl)
                } else {
                    _authState.value = StravaAuthState.Error("Failed to get Strava auth URL (Code: ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("StravaAuthViewModel", "Auth error: ${e.message}", e)
                _authState.value = StravaAuthState.Error("Unable to connect to Strava: ${e.message}")
            }
        }
    }

    fun handleStravaCallback(code: String) {
        viewModelScope.launch {
            _authState.value = StravaAuthState.Loading
            try {
                val token = commonViewModel.getAuthRepository().getAuthState().jwt
                    ?: throw IllegalStateException("Please log in to connect Strava")
                val response = strapiApi.exchangeStravaCode(
                    StrapiApi.StravaTokenRequest(code),
                    "Bearer $token"
                )
                if (response.isSuccessful && response.body() != null) {
                    val tokenResponse = response.body()!!
                    saveTokens(tokenResponse)
                    _authState.value = StravaAuthState.Success
                    _isStravaConnected.value = true
                    syncActivities() // Auto-sync after connect
                } else {
                    _authState.value = StravaAuthState.Error("Token exchange failed (Code: ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("StravaAuthViewModel", "Token error: ${e.message}", e)
                _authState.value = StravaAuthState.Error("Unable to exchange token: ${e.message}")
            }
        }
    }

    fun syncActivities() {
        viewModelScope.launch {
            _authState.value = StravaAuthState.Loading
            try {
                val token = commonViewModel.getAuthRepository().getAuthState().jwt
                    ?: throw IllegalStateException("Please log in to sync Strava")
                if (!isTokenValid()) {
                    _authState.value = StravaAuthState.Error("Strava session expired. Please reconnect.")
                    disconnectStrava()
                    return@launch
                }
                val response = strapiApi.syncStravaActivities(perPage = 10, token = "Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val count = response.body()!!.data.size
                    _authState.value = StravaAuthState.Synced(count)
                } else {
                    _authState.value = StravaAuthState.Error("Sync failed (Code: ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("StravaAuthViewModel", "Sync error: ${e.message}", e)
                _authState.value = StravaAuthState.Error("Unable to sync activities: ${e.message}")
            }
        }
    }

    private fun saveTokens(tokenResponse: StrapiApi.StravaTokenResponse) {
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
        _isStravaConnected.value = false
        _authState.value = StravaAuthState.Idle
        Log.d("StravaAuthViewModel", "Disconnected Strava")
    }
}