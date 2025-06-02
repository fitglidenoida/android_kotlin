package com.trailblazewellness.fitglide

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.trailblazewellness.fitglide.data.workers.MaxGreetingWorker
import com.trailblazewellness.fitglide.data.workers.scheduleHydrationReminder
import com.trailblazewellness.fitglide.presentation.LoginScreen
import com.trailblazewellness.fitglide.presentation.SplashScreen
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
import com.trailblazewellness.fitglide.presentation.navigation.HealthConnectNavigation
import com.trailblazewellness.fitglide.presentation.onboarding.SignupScreen
import com.trailblazewellness.fitglide.presentation.strava.StravaAuthViewModel
import com.trailblazewellness.fitglide.presentation.successstory.SuccessStoryViewModel
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var authRepository: AuthRepository
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var strapiRepository: StrapiRepository
    private lateinit var commonViewModel: CommonViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var successStoryViewModel: SuccessStoryViewModel
    private lateinit var stravaAuthViewModel: StravaAuthViewModel

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
        "android.permission.health.READ_NUTRITION",
        "android.permission.health.READ_HYDRATION"
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
        initializeCriticalDependencies()
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
        Log.d("DesiMaxDebug", "📱 FitGlide app launched. Logcat is working!")
        lifecycleScope.launch(Dispatchers.IO) {
            initializeNonCriticalDependencies()
            setupBackgroundTasks()
        }
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called with intent: ${intent.toString()}")
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.let {
            Log.d("MainActivity", "Deep link Intent received: ${it.toString()}")
            Log.d("MainActivity", "Deep link URI: ${it.data}")

            if (it.action == Intent.ACTION_VIEW) {
                val uri: Uri? = it.data
                uri?.let { uriData ->
                    Log.d("MainActivity", "URI components - Scheme: ${uriData.scheme}, Host: ${uriData.host}, Query: ${uriData.query}, Path: ${uriData.path}")
                    Log.d("MainActivity", "Is custom scheme: ${uriData.scheme == "fitglide"}, Is App Link: ${it.hasCategory(Intent.CATEGORY_BROWSABLE)}")

                    if (uriData.scheme == "fitglide" && uriData.host == "callback") {
                        val accessToken = uriData.getQueryParameter("access_token") ?: return
                        val refreshToken = uriData.getQueryParameter("refresh_token") ?: return
                        val expiresAt = uriData.getQueryParameter("expires_at")?.toLongOrNull() ?: return
                        val athleteId = uriData.getQueryParameter("athlete_id")?.toLongOrNull() ?: return

                        Log.d("MainActivity", "Parsed deep link - access_token: $accessToken, refresh_token: $refreshToken, expires_at: $expiresAt, athlete_id: $athleteId")

                        if (::stravaAuthViewModel.isInitialized) {
                            stravaAuthViewModel.handleStravaCallback(accessToken, refreshToken, expiresAt, athleteId)
                            Log.d("MainActivity", "Deep link handled by StravaAuthViewModel")
                        } else {
                            Log.e("MainActivity", "stravaAuthViewModel not initialized yet. Cannot handle deep link.")
                        }
                    }
                }
            }
        }
    }

    private fun initializeCriticalDependencies() {
        googleAuthManager = GoogleAuthManager(this)
        authRepository = AuthRepository(googleAuthManager, this)
        healthConnectManager = HealthConnectManager(this)
        strapiRepository = StrapiRepository(
            authRepository.strapiApi,
            authRepository = authRepository
        )
        commonViewModel = CommonViewModel(this, strapiRepository, healthConnectManager, authRepository)
        stravaAuthViewModel = StravaAuthViewModel(
            strapiApi = authRepository.strapiApi,
            commonViewModel = commonViewModel,
            context = this,
            strapiRepository = strapiRepository
        )
    }

    private fun initializeNonCriticalDependencies() {
        successStoryViewModel = SuccessStoryViewModel(
            strapiRepository = strapiRepository,
            authRepository = authRepository,
            currentUserId = authRepository.getAuthState().getId() ?: "unknown",
            authToken = "Bearer ${authRepository.getAuthState().jwt ?: ""}",
            context = this@MainActivity // Add context
        )
        homeViewModel = HomeViewModel(commonViewModel, this, healthConnectManager, successStoryViewModel)
        MainActivity.commonViewModel = commonViewModel
    }

    private fun setupBackgroundTasks() {
        requestPermissionsIfNeeded()
        scheduleHydrationReminder(this)
        scheduleMaxGreetingWorker(this)
    }

    private fun scheduleMaxGreetingWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaxGreetingWorker>(
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
        val startDestination = if (authState.jwt != null) "splash" else "login"
        Log.d("MainActivity", "Start destination: $startDestination, jwt=${authState.jwt?.take(10)}")

        NavHost(navController = navController, startDestination = startDestination) {
            composable("login") {
                LoginScreen(
                    navController = navController,
                    googleAuthManager = googleAuthManager,
                    authRepository = authRepository
                )
            }
            composable("splash") {
                SplashScreen(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    authRepository = authRepository,
                    googleAuthManager = googleAuthManager
                )
            }
            composable("onboarding") {
                SignupScreen(
                    navController = navController,
                    googleAuthManager = googleAuthManager,
                    authRepository = authRepository
                )
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
                    commonViewModel = commonViewModel,
                    homeViewModel = homeViewModel,
                    successStoryViewModel = successStoryViewModel,
                    stravaAuthViewModel = stravaAuthViewModel,
                    strapiApi = authRepository.strapiApi
                )
            }
        }
    }
}