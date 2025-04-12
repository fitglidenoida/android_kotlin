package com.trailblazewellness.fitglide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.presentation.navigation.HealthConnectNavigation
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var healthConnectManager: HealthConnectManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val strapiApi = Retrofit.Builder()
            .baseUrl("https://admin.fitglide.in/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StrapiApi::class.java)
        val strapiRepository = StrapiRepository(strapiApi, authRepository)

        setContent {
            FitGlideTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(strapiRepository)
                }
            }
        }
    }

    @Composable
    fun HomeScreen(strapiRepository: StrapiRepository) {
        val authState by authRepository.authStateFlow.collectAsState()
        val commonViewModel = CommonViewModel(this, strapiRepository, healthConnectManager, authRepository)

        HealthConnectNavigation(
            context = this,
            healthConnectManager = healthConnectManager,
            strapiRepository = strapiRepository,
            authRepository = authRepository,
            authToken = authState.jwt ?: "",
            rootNavController = rememberNavController(),
            userName = authState.userName ?: "User",
            commonViewModel = commonViewModel
        )
    }
}