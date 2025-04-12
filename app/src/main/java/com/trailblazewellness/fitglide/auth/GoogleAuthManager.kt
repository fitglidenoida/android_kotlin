package com.trailblazewellness.fitglide.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Suppress("DEPRECATION")
class GoogleAuthManager(val context: Context?) {
    private val googleSignInClient: GoogleSignInClient

    init {
        val webClientId = "535964172976-d1568oi9ve460cf3bml8v4gfqq2hhf31.apps.googleusercontent.com"
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(webClientId)
            .requestServerAuthCode(webClientId)
            .build()

        googleSignInClient = context?.let { GoogleSignIn.getClient(it, gso) }!!
        Log.d("GoogleAuthManager", "Initialized with Web ID: $webClientId")
        checkCurrentAccount("init")
    }

    fun startSignIn(): Intent {
        Log.d("GoogleAuthManager", "Starting Google Sign-In intent")
        checkCurrentAccount("before sign-in")
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        Log.d("GoogleAuthManager", "Handling sign-in result, data: ${data?.extras}")
        Log.d("GoogleAuthManager", "Raw intent data: ${data?.extras?.toString()}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            Log.d("GoogleAuthManager", "Sign-in successful, " +
                    "email: ${account?.email}, " +
                    "idToken: ${account?.idToken}, " +
                    "serverAuthCode: ${account?.serverAuthCode}, " +
                    "id: ${account?.id}, " +
                    "displayName: ${account?.displayName}, " +
                    "givenName: ${account?.givenName}, " +
                    "familyName: ${account?.familyName}")
            checkCurrentAccount("post sign-in")
            account
        } catch (e: ApiException) {
            Log.e("GoogleAuthManager", "Sign-in failed: ${e.statusCode}, message=${e.message}, intent data: ${data?.extras}", e)
            checkCurrentAccount("post sign-in failure")
            null
        }
    }

    suspend fun refreshToken(): String? = suspendCoroutine { continuation ->
        val account = context?.let { GoogleSignIn.getLastSignedInAccount(it) }
        if (account != null) {
            googleSignInClient.silentSignIn().addOnCompleteListener { task ->
                try {
                    val refreshedAccount = task.getResult(ApiException::class.java)
                    Log.d("GoogleAuthManager", "Token refreshed: idToken=${refreshedAccount.idToken}")
                    continuation.resume(refreshedAccount.idToken)
                } catch (e: ApiException) {
                    Log.e("GoogleAuthManager", "Refresh failed: ${e.statusCode}", e)
                    continuation.resume(null)
                }
            }
        } else {
            Log.w("GoogleAuthManager", "No account to refresh")
            continuation.resume(null)
        }
    }

    fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            Log.d("GoogleAuthManager", "Sign-out completed, success: ${it.isSuccessful}")
            checkCurrentAccount("post sign-out")
        }
        Log.d("GoogleAuthManager", "Signed out from Google")
    }

    private fun checkCurrentAccount(stage: String) {
        val lastAccount = context?.let { GoogleSignIn.getLastSignedInAccount(it) }
        Log.d("GoogleAuthManager", "Current account at $stage: ${lastAccount?.email}, idToken: ${lastAccount?.idToken}, serverAuthCode: ${lastAccount?.serverAuthCode}")
    }
}