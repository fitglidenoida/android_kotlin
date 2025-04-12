package com.trailblazewellness.fitglide.presentation.onboarding

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.auth.GoogleAuthManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    navController: NavController,
    googleAuthManager: GoogleAuthManager,
    authRepository: AuthRepository
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("SignupScreen", "Sign-in result received: resultCode=${result.resultCode}, data=${result.data?.extras}")
        val account = googleAuthManager.handleSignInResult(result.data)
        coroutineScope.launch {
            authRepository.loginWithGoogle(account?.idToken) // Pass idToken
            if (authRepository.isLoggedIn()) { // Check login status
                Log.d("SignupScreen", "Google sign-in successful, navigating to main_navigation")
                snackbarHostState.showSnackbar("Sign-up successful!")
                navController.navigate("main_navigation") {
                    popUpTo("onboarding") { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                Log.e("SignupScreen", "Google sign-in failed")
                snackbarHostState.showSnackbar("Sign-up failed.")
            }
        }
    }

    // Add your UI here (simplified example)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { launcher.launch(googleAuthManager.startSignIn()) }) {
            Text("Sign up with Google")
        }
    }
}