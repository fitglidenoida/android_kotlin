package com.trailblazewellness.fitglide

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.auth.GoogleAuthManager
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.max.MaxAiService
import com.trailblazewellness.fitglide.data.workers.WorkoutTrackingService
import com.trailblazewellness.fitglide.data.workers.scheduleHydrationReminder
import com.trailblazewellness.fitglide.presentation.LoginScreen
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
import com.trailblazewellness.fitglide.presentation.navigation.HealthConnectNavigation
import com.trailblazewellness.fitglide.presentation.onboarding.SignupScreen
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    // Core managers & repositories
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var authRepository: AuthRepository
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var strapiRepository: StrapiRepository

    // ViewModels
    private lateinit var commonViewModel: CommonViewModel
    private lateinit var homeViewModel: HomeViewModel

    // Permissions
    private val healthPermissions = setOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.FOREGROUND_SERVICE_LOCATION,
        "android.permission.health.READ_STEPS",
        "android.permission.health.READ_SLEEP",
        "android.permission.health.READ_EXERCISE",
        "android.permission.health.WRITE_EXERCISE",
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_DISTANCE",
        "android.permission.health.READ_TOTAL_CALORIES_BURNED",
        "android.permission.health.WRITE_STEPS",
        "android.permission.health.READ_NUTRITION"
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { Log.d("MainActivity", "${it.key} = ${it.value}") }
    }

    companion object {
        lateinit var commonViewModel: CommonViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Init dependencies
        initializeDependencies()

        // Step 2: Setup Compose UI
        setContent {
            FitGlideTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF5F5F5)),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }

        // Step 3: Background Setup (Defer heavy stuff)
        lifecycleScope.launch {
            delay(3000) // Let UI settle
            setupBackgroundTasks()
        }
    }

    private fun initializeDependencies() {
        googleAuthManager = GoogleAuthManager(this)
        authRepository = AuthRepository(googleAuthManager, this)
        healthConnectManager = HealthConnectManager(this)
        strapiRepository = StrapiRepository(authRepository.strapiApi, authRepository)

        commonViewModel = CommonViewModel(this, strapiRepository, healthConnectManager, authRepository)
        homeViewModel = HomeViewModel(commonViewModel)
        MainActivity.commonViewModel = commonViewModel

        MaxAiService.initTTS(this) // Safe to init early, just don’t speak here
    }

    private fun setupBackgroundTasks() {
        requestPermissionsIfNeeded()
        scheduleHydrationReminder(this)
        scheduleMaxGreetingWorker(this)
        startWorkoutService()
    }

    private fun startWorkoutService() {
        val intent = Intent(this, WorkoutTrackingService::class.java).apply {
            putExtra("userId", authRepository.getAuthState().getId() ?: "4")
            putExtra("workoutType", "Walking")
            putExtra("token", "Bearer ${authRepository.getAuthState().jwt ?: ""}")
        }
        ContextCompat.startForegroundService(this, intent)
        Log.d("MainActivity", "Started WorkoutTrackingService on launch")
    }

    private fun scheduleMaxGreetingWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<com.trailblazewellness.fitglide.data.workers.MaxGreetingWorker>(
            1, TimeUnit.DAYS
        ).setInitialDelay(6, TimeUnit.HOURS).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "max_greeting_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun requestPermissionsIfNeeded() {
        val missing = healthPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    @Composable
    fun MainNavigation() {
        val navController = rememberNavController()
        val authState by authRepository.authStateFlow.collectAsState()

        NavHost(navController = navController, startDestination = "login") {
            composable("login") {
                LoginScreen(navController, googleAuthManager, authRepository) {
                    navController.navigate("main_navigation") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable("onboarding") {
                SignupScreen(navController, googleAuthManager, authRepository)
            }
            composable("main_navigation") {
                HealthConnectNavigation(
                    context = this@MainActivity,
                    healthConnectManager = healthConnectManager,
                    strapiRepository = strapiRepository,
                    authRepository = authRepository,
                    authToken = authState.jwt ?: "",
                    rootNavController = navController,
                    userName = authState.userName ?: "User",
                    commonViewModel = commonViewModel
                )
            }
        }
    }
}
