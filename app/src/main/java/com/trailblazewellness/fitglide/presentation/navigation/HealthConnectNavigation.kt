package com.trailblazewellness.fitglide.presentation.navigation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trailblazewellness.fitglide.FitGlideTheme
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectRepository
import com.trailblazewellness.fitglide.presentation.social.FriendsScreen
import com.trailblazewellness.fitglide.presentation.home.CreateWeightLossStory
import com.trailblazewellness.fitglide.presentation.home.HomeScreen
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
import com.trailblazewellness.fitglide.presentation.home.PostDetailScreen
import com.trailblazewellness.fitglide.presentation.home.WeightLossStories
import com.trailblazewellness.fitglide.presentation.meals.MealsScreen
import com.trailblazewellness.fitglide.presentation.meals.MealsViewModel
import com.trailblazewellness.fitglide.presentation.profile.ProfileScreen
import com.trailblazewellness.fitglide.presentation.profile.ProfileViewModel
import com.trailblazewellness.fitglide.presentation.sleep.SleepScreen
import com.trailblazewellness.fitglide.presentation.sleep.SleepViewModel
import com.trailblazewellness.fitglide.presentation.social.ChallengeDetailScreen
import com.trailblazewellness.fitglide.presentation.strava.StravaAuthViewModel
import com.trailblazewellness.fitglide.presentation.successstory.SuccessStoryViewModel
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import com.trailblazewellness.fitglide.presentation.workouts.WorkoutDetailScreen
import com.trailblazewellness.fitglide.presentation.workouts.WorkoutPlanScreen
import com.trailblazewellness.fitglide.presentation.workouts.WorkoutScreen
import com.trailblazewellness.fitglide.presentation.workouts.WorkoutViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectNavigation(
    context: Context,
    healthConnectManager: HealthConnectManager,
    strapiRepository: StrapiRepository,
    authRepository: AuthRepository,
    authToken: String,
    rootNavController: NavController,
    userName: String,
    commonViewModel: CommonViewModel,
    homeViewModel: HomeViewModel,
    successStoryViewModel: SuccessStoryViewModel,
    stravaAuthViewModel: StravaAuthViewModel,
    strapiApi: StrapiApi
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val formattedAuthToken = "Bearer $authToken"
    val healthConnectRepository = HealthConnectRepository(healthConnectManager)

    val profileViewModel = ProfileViewModel(strapiRepository, authRepository, healthConnectRepository, homeViewModel)
    val sleepViewModel = SleepViewModel(healthConnectManager, strapiRepository, authRepository, context)
    val mealsViewModel = MealsViewModel(strapiRepository, healthConnectRepository, authRepository)
    val workoutViewModel = WorkoutViewModel(
        strapiRepository = strapiRepository,
        healthConnectManager = healthConnectManager,
        homeViewModel = homeViewModel,
        commonViewModel = commonViewModel,
        authToken = formattedAuthToken,
        userId = authRepository.getAuthState().getId().toString()
    )

    FitGlideTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FitGlide") }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Workouts") },
                        label = { Text("Workouts") },
                        selected = currentRoute == "workouts",
                        onClick = { navController.navigate("workouts") }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Bedtime, contentDescription = "Sleep") },
                        label = { Text("Sleep") },
                        selected = currentRoute == "sleep",
                        onClick = { navController.navigate("sleep") }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Restaurant, contentDescription = "Meals") },
                        label = { Text("Meals") },
                        selected = currentRoute == "meals",
                        onClick = { navController.navigate("meals") }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        selected = currentRoute == "profile",
                        onClick = { navController.navigate("profile") }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.weight(1f)
                ) {
                    composable("home") {
                        HomeScreen(
                            navController = navController,
                            context = context,
                            healthConnectManager = healthConnectManager,
                            homeViewModel = homeViewModel,
                            commonViewModel = commonViewModel,
                            successStoryViewModel = successStoryViewModel,
                            userName = userName
                        )
                    }
                    composable("workouts") {
                        WorkoutScreen(
                            viewModel = workoutViewModel,
                            navController = navController,
                            userName = userName,
                            homeViewModel = homeViewModel,
                            commonViewModel = commonViewModel
                        )
                    }
                    composable("workout_plan") {
                        WorkoutPlanScreen(
                            navController = navController,
                            viewModel = workoutViewModel,
                            strapiRepository = strapiRepository,
                            authRepository = authRepository
                        )
                    }
                    composable("workout_detail/{workoutId}") { backStackEntry ->
                        val workoutId = backStackEntry.arguments?.getString("workoutId") ?: ""
                        WorkoutDetailScreen(
                            workoutId = workoutId,
                            strapiApi = strapiApi,
                            commonViewModel = commonViewModel,
                            navController = navController,
                            healthConnectManager = HealthConnectManager(context)
                        )
                    }
                    composable("sleep") {
                        SleepScreen(
                            viewModel = sleepViewModel,
                            navController = navController,
                            userName = userName
                        )
                    }
                    composable("meals") {
                        MealsScreen(
                            viewModel = mealsViewModel,
                            navController = navController,
                            userName = userName
                        )
                    }
                    composable("profile") {
                        ProfileScreen(
                            modifier = Modifier,
                            authRepository = authRepository,
                            navController = navController,
                            rootNavController = rootNavController,
                            profileViewModel = profileViewModel,
                            homeViewModel = homeViewModel,
                            stravaAuthViewModel = stravaAuthViewModel
                        )
                    }
                    composable("settings") {
                        Text("Settings Screen - TBD", modifier = Modifier.padding(16.dp))
                    }
                    composable("friends") {
                        FriendsScreen(navController, commonViewModel, homeViewModel, authRepository)
                    }
                    composable("postDetail/{postId}") { backStackEntry ->
                        val postId = backStackEntry.arguments?.getString("postId") ?: ""
                        PostDetailScreen(postId, commonViewModel, navController)
                    }
                    composable("challengeDetail/{challengeId}") { backStackEntry ->
                        val challengeId = backStackEntry.arguments?.getString("challengeId") ?: ""
                        val userId = authRepository.authStateFlow.collectAsState().value.getId() ?: ""
                        ChallengeDetailScreen(challengeId, commonViewModel, navController, userId)
                    }
                    composable("create_weight_loss_stories") {
                        CreateWeightLossStory(
                            navController = navController,
                            viewModel = successStoryViewModel
                        )
                    }
                    composable("stories_listings") {
                        WeightLossStories(
                            viewModel = successStoryViewModel
                        )
                    }
                }
            }
        }
    }
}