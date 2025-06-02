package com.trailblazewellness.fitglide.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiApi.UserProfileRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    @ApplicationContext private val context: Context
) {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://admin.fitglide.in/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val authApi = retrofit.create(AuthApi::class.java)
    val strapiApi = retrofit.create(StrapiApi::class.java)

    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val _authStateFlow = MutableStateFlow(loadAuthStateFromPrefs())
    val authStateFlow: StateFlow<AuthState> get() = _authStateFlow

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        Log.d("AuthRepository", "Initialized with authState: ${_authStateFlow.value}")
        if (_authStateFlow.value.jwt != null) {
            Log.d("AuthRepository", "JWT exists, refreshing login on init")
            scope.launch {
                refreshLogin()
            }
        }
    }

    fun getAuthState(): AuthState = _authStateFlow.value

    private fun loadAuthStateFromPrefs(): AuthState {
        val jwt = prefs.getString("jwt", null)
        val userId = prefs.getString("userId", null)
        val userName = prefs.getString("userName", null)
        return AuthState(jwt, userId, userName).also {
            Log.d("AuthRepository", "Loaded from prefs: jwt=$jwt, userId=$userId, userName=$userName")
        }
    }

    private fun saveAuthStateToPrefs(jwt: String?, userId: String?, userName: String?) {
        with(prefs.edit()) {
            putString("jwt", jwt)
            putString("userId", userId)
            putString("userName", userName)
            apply()
        }
        Log.d("AuthRepository", "Saved to prefs: jwt=$jwt, userId=$userId, userName=$userName")
    }

    suspend fun loginWithGoogle(idToken: String?): Boolean {
        if (idToken == null) {
            Log.w("AuthRepository", "No idToken provided")
            return false
        }

        val googleAccount = googleAuthManager.context?.let { GoogleSignIn.getLastSignedInAccount(it) }
        Log.d("AuthRepository", "Google account details: email=${googleAccount?.email}, givenName=${googleAccount?.givenName}, familyName=${googleAccount?.familyName}, displayName=${googleAccount?.displayName}, idToken=$idToken")

        if (getAuthState().jwt == null) {
            Log.d("AuthRepository", "JWT missing, attempting Google login")
            val request = mapOf("id_token" to idToken)
            Log.d("AuthRepository", "Sending to Strapi: $request")
            try {
                val response = authApi.loginWithGoogle(request)
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    val jwt = loginResponse?.jwt
                    val userId = loginResponse?.user?.id?.toString()

                    // Now ensure the first name (from `givenName`) is set
                    val firstName = googleAccount?.givenName ?: "User" // Ensure we use `givenName`
                    val lastName = googleAccount?.familyName ?: ""
                    Log.d("AuthRepository", "Login success: JWT=$jwt, id=$userId, firstName=$firstName, lastName=$lastName")

                    _authStateFlow.value = AuthState(jwt, userId, firstName) // Store firstName in AuthState
                    saveAuthStateToPrefs(jwt, userId, firstName) // Store in SharedPreferences

                    val updateRequest = UserProfileRequest(
                        firstName = googleAccount?.givenName,
                        lastName = lastName,
                        email = googleAccount?.email,
                        mobile = null
                    )

                    val updateResponse = strapiApi.updateUserProfile(
                        userId.toString(),
                        updateRequest, // Use UserProfileRequest directly, no UserProfileBody
                        "Bearer $jwt"
                    )
                    if (updateResponse.isSuccessful) {
                        Log.d("AuthRepository", "Updated Strapi profile: firstName=${googleAccount?.givenName}, lastName=$lastName, email=${googleAccount?.email}")
                    } else {
                        Log.e("AuthRepository", "Failed to update Strapi profile: ${updateResponse.code()} - ${updateResponse.errorBody()?.string()}")
                    }
                    return true
                } else {
                    Log.e("AuthRepository", "Google login failed: ${response.code()}, ${response.errorBody()?.string()}")
                    return false
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Google login exception: ${e.message}", e)
                return false
            }
        }
        Log.d("AuthRepository", "Already logged in: ${_authStateFlow.value}")
        return true
    }

    suspend fun loginWithCredentials(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            Log.w("AuthRepository", "Email or password empty")
            return
        }

        Log.d("AuthRepository", "Attempting Strapi login with email: $email")
        val request = mapOf("identifier" to email, "password" to password)
        try {
            val response = authApi.loginWithCredentials(request)
            if (response.isSuccessful) {
                val loginResponse = response.body()
                val jwt = loginResponse?.jwt
                val userId = loginResponse?.user?.id?.toString()
                val userName = loginResponse?.user?.email ?: "User"
                Log.d("AuthRepository", "Strapi login success: JWT=$jwt, id=$userId, name=$userName")
                _authStateFlow.value = AuthState(jwt, userId, userName)
                saveAuthStateToPrefs(jwt, userId, userName)
            } else {
                Log.e("AuthRepository", "Strapi login failed: ${response.code()}, ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Strapi login exception: ${e.message}", e)
        }
    }

    suspend fun refreshLogin() {
        val account = googleAuthManager.context?.let { GoogleSignIn.getLastSignedInAccount(it) }
        val refreshedToken = account?.idToken
        if (refreshedToken != null) {
            loginWithGoogle(refreshedToken)
        } else {
            Log.w("AuthRepository", "Failed to refresh token, no signed-in account")
        }
    }

    fun isLoggedIn(): Boolean = _authStateFlow.value.jwt != null

    fun logout() {
        googleAuthManager.signOut()
        _authStateFlow.value = AuthState(null, null, null)
        with(prefs.edit()) {
            clear() // Clear all preferences to ensure no stale data remains
            apply()
        }
        Log.d("AuthRepository", "Logged out, cleared auth state and preferences")
    }

    fun updateUserName(newName: String) {
        val currentState = _authStateFlow.value
        _authStateFlow.value = AuthState(currentState.jwt, currentState.getId(), newName)
        saveAuthStateToPrefs(currentState.jwt, currentState.getId(), newName)
        Log.d("AuthRepository", "Updated userName to $newName, new authState: ${_authStateFlow.value}")
    }
}

interface AuthApi {
    @POST("google-login")
    suspend fun loginWithGoogle(@Body request: Map<String, String>): Response<LoginResponse>

    @POST("auth/local")
    suspend fun loginWithCredentials(@Body request: Map<String, String>): Response<LoginResponse>
}

data class LoginResponse(val jwt: String, val user: User)
data class User(val id: Int, val email: String)