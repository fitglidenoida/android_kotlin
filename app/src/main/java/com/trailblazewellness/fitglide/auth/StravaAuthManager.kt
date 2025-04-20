package com.trailblazewellness.fitglide.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.trailblazewellness.fitglide.data.api.StrapiApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class StravaAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val strapiApi: StrapiApi
) {
    suspend fun initiateStravaAuth(jwt: String): Result<StrapiApi.StravaAuthResponse> {
        return try {
            Log.d("StravaAuthManager", "Initiating Strava auth with JWT")
            val response = strapiApi.initiateStravaAuth("Bearer $jwt")
            if (response.isSuccessful && response.body() != null) {
                Log.d("StravaAuthManager", "Got redirect URL: ${response.body()!!.redirectUrl}")
                Result.success(response.body()!!)
            } else {
                Log.e("StravaAuthManager", "Auth failed: ${response.code()} - ${response.errorBody()?.string()}")
                Result.failure(Exception("Failed to get Strava auth URL (Code: ${response.code()})"))
            }
        } catch (e: Exception) {
            Log.e("StravaAuthManager", "Auth error: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun launchStravaAuthUrl(url: String) {
        try {
            Log.d("StravaAuthManager", "Launching Strava auth URL: $url")
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            intent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            Log.e("StravaAuthManager", "Failed to launch URL: ${e.message}", e)
            throw Exception("Unable to open Strava auth page")
        }
    }

    fun handleCallbackUri(uri: Uri): String? {
        if (uri.toString().startsWith("https://fitglide.in/callback")) {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                Log.d("StravaAuthManager", "Received Strava auth code")
                return code
            } else {
                Log.e("StravaAuthManager", "No code in callback URI: $uri")
            }
        } else {
            Log.e("StravaAuthManager", "Invalid callback URI: $uri")
        }
        return null
    }
}