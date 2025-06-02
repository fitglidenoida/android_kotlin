package com.trailblazewellness.fitglide.presentation

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.trailblazewellness.fitglide.FitGlideTheme
import com.trailblazewellness.fitglide.R
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.auth.GoogleAuthManager
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel

@Composable
fun SplashScreen(
    navController: NavController,
    homeViewModel: HomeViewModel,
    authRepository: AuthRepository,
    googleAuthManager: GoogleAuthManager
) {
    val homeData by homeViewModel.homeData.collectAsState()
    val authState by authRepository.authStateFlow.collectAsState()
    var isReady by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val startTime = remember { System.currentTimeMillis() }

    // Handle authentication and initialization
    LaunchedEffect(authState, homeData) {
        Log.d("SplashScreen", "AuthState: jwt=${authState.jwt?.take(10)}, userName=${authState.userName}")
        Log.d("SplashScreen", "HomeData: stepGoal=${homeData.stepGoal}, firstName=${homeData.firstName}")

        // Check if authentication is missing
        val account = googleAuthManager.context?.let { GoogleSignIn.getLastSignedInAccount(it) }
        if (authState.jwt == null && account?.idToken == null) {
            Log.d("SplashScreen", "No JWT and no Google account, redirecting to login")
            navController.navigate("login") {
                popUpTo("splash") { inclusive = true }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }

        // Complete Google authentication if idToken is available
        if (authState.jwt == null && account?.idToken != null) {
            Log.d("SplashScreen", "Completing Google authentication with idToken")
            val success = authRepository.loginWithGoogle(account.idToken)
            if (!success) {
                Log.e("SplashScreen", "Google authentication failed")
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                    launchSingleTop = true
                }
                return@LaunchedEffect
            }
        }

        // Initialize HomeViewModel
        homeViewModel.initializeWithContext()

        // Check readiness: JWT present and HomeViewModel data loaded
        if (authState.jwt != null && (homeData.stepGoal > 0 || homeData.firstName.isNotEmpty())) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.d("SplashScreen", "Ready: JWT=${authState.jwt?.take(10)}, homeData=$homeData, elapsed=${elapsed}ms")
            isReady = true
            isVisible = false
            navController.navigate("main_navigation") {
                popUpTo("splash") { inclusive = true }
                launchSingleTop = true
            }
        } else {
            Log.d("SplashScreen", "Not ready: jwt=${authState.jwt}, stepGoal=${homeData.stepGoal}, firstName=${homeData.firstName}")
            // Optionally add a timeout to prevent infinite waiting
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 10000) { // 10-second timeout
                Log.e("SplashScreen", "Timeout waiting for readiness, redirecting to login")
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    FitGlideTheme {
        AnimatedVisibility(
            visible = isVisible,
            exit = fadeOut(animationSpec = tween(500))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.fitglide_logo_tweaked1),
                        contentDescription = "FitGlide Logo",
                        modifier = Modifier.size(250.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "FitGlide",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        color = Color(0xFF4CAF50),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}