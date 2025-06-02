package com.trailblazewellness.fitglide.presentation.home

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.trailblazewellness.fitglide.FitGlideTheme
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.workers.WorkoutTrackingService
import com.trailblazewellness.fitglide.presentation.successstory.SuccessStoryViewModel
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PLACEHOLDER_BADGE_URL = "https://admin.fitglide.in/uploads/placeholder_badge.png"
private const val INACTIVITY_THRESHOLD_MS = 60_000L // 1 minute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    context: android.content.Context,
    healthConnectManager: HealthConnectManager,
    homeViewModel: HomeViewModel,
    commonViewModel: CommonViewModel,
    successStoryViewModel: SuccessStoryViewModel,
    userName: String
) {
    val isLoading by commonViewModel.isLoading.collectAsState()
    val uiMessage by commonViewModel.uiMessage.collectAsState()
    val homeData by homeViewModel.homeDataFlow.collectAsState()
    val date by commonViewModel.date.collectAsState()
    val posts by commonViewModel.posts.collectAsState()
    val trackedSteps by commonViewModel.trackedStepsFlow.collectAsState()
    val challenges by commonViewModel.challenges.collectAsState(initial = emptyList())
    val navigateToCreateStory by homeViewModel.navigateToCreateStory.collectAsState()
    val scrollState = rememberScrollState()
    var showTrackingPopup by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showCustomDatePicker by remember { mutableStateOf(false) } // For custom date picker
    var selectingStartDate by remember { mutableStateOf(true) } // Track start/end date selection
    var workoutType by remember { mutableStateOf("Walking") }
    var showWorkoutPicker by remember { mutableStateOf(false) }
    val workoutTypes = listOf(
        "Walking" to Icons.AutoMirrored.Filled.DirectionsWalk,
        "Running" to Icons.Default.DirectionsRun,
        "Cycling" to Icons.Default.DirectionsBike,
        "Hiking" to Icons.Default.Terrain,
        "Swimming" to Icons.Default.Pool,
        "Other" to Icons.Default.FitnessCenter
    )
    val snackbarHostState = remember { SnackbarHostState() }
    var showMaxPopup by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)
    val sharedPreferences = LocalContext.current.getSharedPreferences("fitglide_prefs", Context.MODE_PRIVATE)

    // Date picker state
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // Limit to past and current dates
                return utcTimeMillis <= System.currentTimeMillis()
            }
        }
    )

    // Track last step update time
    var lastStepUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastTrackedSteps by remember { mutableStateOf(trackedSteps) }

    // Automatic Pause Detection
    LaunchedEffect(trackedSteps, homeData.isTracking) {
        if (homeData.isTracking && !homeData.paused) {
            if (trackedSteps != lastTrackedSteps) {
                lastStepUpdateTime = System.currentTimeMillis()
                lastTrackedSteps = trackedSteps
            } else if (System.currentTimeMillis() - lastStepUpdateTime >= INACTIVITY_THRESHOLD_MS) {
                homeViewModel.homeData.update { it.copy(paused = true) }
                sharedPreferences.edit().putBoolean("isPaused", true).apply()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Tracking Paused Automatically",
                        duration = SnackbarDuration.Short
                    )
                }
                Log.d("HomeScreen", "Auto-paused due to inactivity")
            }
        }
    }

    // Automatic Resume Detection
    LaunchedEffect(trackedSteps, homeData.paused, homeData.isTracking) {
        if (homeData.isTracking && homeData.paused && trackedSteps > lastTrackedSteps) {
            homeViewModel.homeData.update { it.copy(paused = false) }
            sharedPreferences.edit().putBoolean("isPaused", false).apply()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Tracking Resumed",
                    duration = SnackbarDuration.Short
                )
            }
            lastStepUpdateTime = System.currentTimeMillis()
            lastTrackedSteps = trackedSteps
            Log.d("HomeScreen", "Auto-resumed due to step activity")
        }
    }

    // Manual Pause/Resume Feedback
    LaunchedEffect(homeData.paused) {
        if (homeData.paused && homeData.isTracking) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Tracking Paused",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // FAB Navigation Fix
    LaunchedEffect(navigateToCreateStory) {
        if (navigateToCreateStory) {
            navController.navigate("create_weight_loss_stories")
            homeViewModel.onNavigationHandled()
        }
    }

    LaunchedEffect(isLoading) {
        swipeRefreshState.isRefreshing = isLoading
    }

    LaunchedEffect(trackedSteps, homeData.isTracking) {
        Log.d("HomeScreen", "trackedSteps=$trackedSteps, isConsumed=${homeData.isTracking}")
        if (trackedSteps > 5 && !homeData.isTracking && !showTrackingPopup) {
            delay(10_000L)
            if (trackedSteps > 5 && !homeData.isTracking) {
                showTrackingPopup = true
                Log.d("HomeScreen", "Showing tracking popup after 10s: trackedSteps=$trackedSteps")
            }
        }
    }

    LaunchedEffect(homeData.maxMessage) {
        val msg = homeData.maxMessage
        Log.d("DesiMaxDebug", "Max message checked: yesterday=${msg.yesterday}, today=${msg.today}, hasPlayed=${msg.hasPlayed}")
        if (msg.yesterday.isNotBlank() && msg.today.isNotBlank() && !msg.hasPlayed) {
            showMaxPopup = true
            Log.d("DesiMaxDebug", "Triggering Max popup")
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    val stepChallenge = challenges.firstOrNull { it.type.contains("Step", ignoreCase = true) && it.goal > 0 }

    FitGlideTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        )
                        .padding(vertical = 16.dp, horizontal = 24.dp)
                ) {
                    Text(
                        text = "Hey ${homeData.firstName}!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            },
            floatingActionButton = {
                IconButton(
                    onClick = { homeViewModel.onCreateStoryClicked() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                        .size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Story",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        ) { padding ->
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = {
                    coroutineScope.launch {
                        homeViewModel.refreshData()
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Date Navigation
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = {
                                    homeViewModel.updateDate(
                                        if (homeData.dateRangeMode == "Day") date.minusDays(1)
                                        else date.minusDays(7)
                                    )
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Previous",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = when (homeData.dateRangeMode) {
                                        "Day" -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                        "Week" -> "Week of ${
                                            date.minusDays(6).format(
                                                DateTimeFormatter.ofPattern("MMM d")
                                            )
                                        }-${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
                                        "Custom" -> homeData.customStartDate?.let { start ->
                                            homeData.customEndDate?.let { end ->
                                                "${start.format(DateTimeFormatter.ofPattern("MMM d"))} - ${
                                                    end.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                                }"
                                            }
                                        } ?: "Select Custom Range"
                                        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row {
                                    IconButton(onClick = { showRangePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Range Picker",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = {
                                        homeViewModel.updateDate(
                                            if (homeData.dateRangeMode == "Day") date.plusDays(1)
                                            else date.plusDays(7)
                                        )
                                    }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Next",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Steps Section
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            val arcColor = MaterialTheme.colorScheme.primary
                            Canvas(modifier = Modifier.size(160.dp)) {
                                val totalStepsDisplay = homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps
                                val radius = size.width / 2 - 10.dp.toPx()
                                drawArc(
                                    color = arcColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * (totalStepsDisplay / homeData.stepGoal).coerceIn(0f, 1f),
                                    useCenter = false,
                                    topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(
                                        width = 10.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                            Text(
                                text = "${(homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps).toInt()}",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Steps (${homeData.dateRangeMode})",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Health Metrics Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val arcColor = MaterialTheme.colorScheme.secondary
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    val radius = size.width / 2 - 10.dp.toPx()
                                    drawArc(
                                        color = arcColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * (homeData.heartRate / homeData.maxHeartRate).coerceIn(0f, 1f),
                                        useCenter = false,
                                        topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = Stroke(
                                            width = 10.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }
                                Text(
                                    text = "Avg HR: ${homeData.heartRate.toInt()}/${homeData.maxHeartRate.toInt()}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "BPM",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val arcColor = MaterialTheme.colorScheme.tertiary
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    val radius = size.width / 2 - 10.dp.toPx()
                                    drawArc(
                                        color = arcColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * (homeData.caloriesBurned / homeData.bmr.toFloat()).coerceIn(0f, 1f),
                                        useCenter = false,
                                        topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = Stroke(
                                            width = 10.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }
                                Text(
                                    text = "${homeData.caloriesBurned.toInt()}/${homeData.bmr}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Cal",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val arcColor = MaterialTheme.colorScheme.primary
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    val radius = size.width / 2 - 10.dp.toPx()
                                    val stressProgress = when (homeData.stressScore) {
                                        in 0..33 -> 0.3f // Low
                                        in 34..66 -> 0.6f // Medium
                                        else -> 1.0f // High
                                    }
                                    drawArc(
                                        color = arcColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * stressProgress,
                                        useCenter = false,
                                        topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = Stroke(
                                            width = 10.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }
                                Text(
                                    text = when (homeData.stressScore) {
                                        in 0..33 -> "Low"
                                        in 34..66 -> "Medium"
                                        else -> "High"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Stress",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Tracking Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .shadow(6.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(
                                        onClick = { showWorkoutPicker = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = workoutTypes.find { it.first == workoutType }?.second
                                                ?: Icons.Default.FitnessCenter,
                                            contentDescription = workoutType,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val primaryColor = MaterialTheme.colorScheme.primary
                                    val secondaryColor = MaterialTheme.colorScheme.secondary
                                    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    Canvas(modifier = Modifier.size(50.dp)) {
                                        val totalStepsDisplay =
                                            homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps
                                        val progress =
                                            (totalStepsDisplay / homeData.stepGoal).coerceIn(0f, 1f)
                                        val color = when {
                                            progress >= 1f -> primaryColor
                                            progress >= 0.5f -> secondaryColor
                                            else -> onSurfaceVariantColor.copy(alpha = 0.5f)
                                        }
                                        drawArc(
                                            color = color,
                                            startAngle = -90f,
                                            sweepAngle = 360f * progress,
                                            useCenter = false,
                                            topLeft = Offset(
                                                size.width / 2 - 20.dp.toPx(),
                                                size.height / 2 - 20.dp.toPx()
                                            ),
                                            size = Size(40.dp.toPx(), 40.dp.toPx()),
                                            style = Stroke(
                                                width = 6.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "${(homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps).toInt()}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Goal: ${homeData.stepGoal.toInt()}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    if (homeData.isTracking) {
                                        IconButton(
                                            onClick = {
                                                homeViewModel.homeData.update { it.copy(paused = !it.paused) }
                                                sharedPreferences.edit().putBoolean("isPaused", !homeData.paused).apply()
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (homeData.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                contentDescription = if (homeData.paused) "Resume Tracking" else "Pause Tracking",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Button(
                                        onClick = {
                                            val userId = commonViewModel.getAuthRepository().getAuthState().getId() ?: "1"
                                            val token = commonViewModel.getAuthRepository().getAuthState().jwt ?: ""
                                            val intent = Intent(
                                                context,
                                                WorkoutTrackingService::class.java
                                            ).apply {
                                                putExtra("userId", userId)
                                                putExtra("workoutType", workoutType)
                                                putExtra("manualStart", true)
                                                putExtra("token", token)
                                            }
                                            if (homeData.isTracking) {
                                                Log.d("HomeScreen", "Stopping WorkoutTrackingService")
                                                context.stopService(intent)
                                                homeViewModel.stopTracking()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Tracking stopped")
                                                }
                                            } else {
                                                Log.d(
                                                    "HomeScreen",
                                                    "Starting WorkoutTrackingService with type: $workoutType"
                                                )
                                                try {
                                                    ContextCompat.startForegroundService(context, intent)
                                                    homeViewModel.startTracking()
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Tracking started: $workoutType")
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("HomeScreen", "Failed to start service: ${e.message}", e)
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Failed to start tracking: ${e.message}")
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (homeData.isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .width(80.dp)
                                    ) {
                                        Text(
                                            text = if (homeData.isTracking) "Stop" else "Start",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Navigation Cards
                        NavigationCard(
                            icon = Icons.Default.FitnessCenter,
                            label = "Workout - ${homeData.caloriesBurned.toInt()} cal",
                            onClick = { navController.navigate("workouts") }
                        )
                        NavigationCard(
                            icon = Icons.Default.NightlightRound,
                            label = "${String.format("%.1f", homeData.sleepHours)}h slept",
                            onClick = { navController.navigate("sleep") }
                        )
                        NavigationCard(
                            icon = Icons.Default.Restaurant,
                            label = "${homeData.caloriesLogged.toInt()}/${homeData.bmr} cal",
                            onClick = { navController.navigate("meals") }
                        )
                        NavigationCard(
                            icon = Icons.Default.WaterDrop,
                            label = "${homeData.hydration}L today",
                            onClick = { commonViewModel.logWaterIntake() }
                        )
                        NavigationCard(
                            icon = Icons.Default.Group,
                            label = "Friends & Community (${posts.size} posts)",
                            onClick = { navController.navigate("friends") }
                        )
                        NavigationCard(
                            icon = Icons.Default.Star,
                            label = "View Weight Loss Stories",
                            onClick = { navController.navigate("stories_listings") }
                        )

                        // Max Insights
                        if (homeData.maxMessage.hasPlayed) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .shadow(6.dp, RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = "Insights",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Max says: ${homeData.maxMessage.today.takeIf { it.isNotBlank() } ?: "Push HR to 130!"}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = {
                                        showMaxPopup = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Replay Max",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Dynamic Challenges Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .shadow(6.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Challenge",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stepChallenge?.let {
                                        "${it.type}: ${homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps}/${it.goal} steps"
                                    } ?: "No active step challenge",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Achievements
                        Text(
                            text = "Achievements",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(homeData.badges) { badge ->
                                AsyncImage(
                                    model = badge.iconUrl ?: PLACEHOLDER_BADGE_URL,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(100.dp)
                                )
                            }
                        }

                        // Leaderboard
                        Text(
                            text = "Leaderboard",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .shadow(6.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = homeData.storiesOrLeaderboard.firstOrNull() ?: "No data yet",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { homeViewModel.toggleStoriesOrLeaderboard() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Refresh Leaderboard",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Max Popup
            if (showMaxPopup) {
                Log.d("DesiMaxDebug", "Rendering Max popup: yesterday=${homeData.maxMessage.yesterday}, today=${homeData.maxMessage.today}")
                AlertDialog(
                    onDismissRequest = {
                        showMaxPopup = false
                        homeViewModel.markMaxMessagePlayed()
                    },
                    title = {
                        Text(
                            text = "Max Says 💬",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = homeData.maxMessage.yesterday,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = homeData.maxMessage.today,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!homeData.healthVitalsUpdated) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Update your profile to set personalized goals!",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { navController.navigate("profile") }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showMaxPopup = false
                            homeViewModel.markMaxMessagePlayed()
                        }) {
                            Text(
                                text = "Got it!",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // Tracking Popup
            if (showTrackingPopup) {
                AlertDialog(
                    onDismissRequest = { showTrackingPopup = false },
                    title = {
                        Text(
                            text = "Max Says",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Text(
                            text = "You're walking but not tracking! Start now?",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val userId = commonViewModel.getAuthRepository().getAuthState().getId() ?: "1"
                            val token = commonViewModel.getAuthRepository().getAuthState().jwt ?: ""
                            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                                putExtra("userId", userId)
                                putExtra("workoutType", workoutType)
                                putExtra("manualStart", true)
                                putExtra("token", token)
                            }
                            Log.d("HomeScreen", "Starting WorkoutTrackingService with type: $workoutType")
                            try {
                                ContextCompat.startForegroundService(context, intent)
                                homeViewModel.startTracking()
                                showTrackingPopup = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Tracking started: $workoutType")
                                }
                            } catch (e: Exception) {
                                Log.e("HomeScreen", "Failed to start service: ${e.message}", e)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to start tracking: ${e.message}")
                                }
                            }
                        }) {
                            Text(
                                text = "Start",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showTrackingPopup = false
                            commonViewModel.updateTrackedSteps(0f)
                            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                                putExtra("resetSteps", true)
                            }
                            ContextCompat.startForegroundService(context, intent)
                            Log.d("HomeScreen", "Reset steps via 'Later'")
                        }) {
                            Text(
                                text = "Later",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // Range Picker Popup
            if (showRangePicker) {
                AlertDialog(
                    onDismissRequest = { showRangePicker = false },
                    title = {
                        Text(
                            text = "Select Range",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column {
                            TextButton(onClick = {
                                homeViewModel.setDateRangeMode("Day")
                                showRangePicker = false
                            }) {
                                Text(
                                    text = "Day",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(onClick = {
                                homeViewModel.setDateRangeMode("Week")
                                showRangePicker = false
                            }) {
                                Text(
                                    text = "Week",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(onClick = {
                                showRangePicker = false
                                showCustomDatePicker = true
                                selectingStartDate = true
                                homeViewModel.setDateRangeMode("Custom")
                            }) {
                                Text(
                                    text = "Custom",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // Custom Date Picker Dialog
            if (showCustomDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showCustomDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val selectedDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                if (selectingStartDate) {
                                    homeViewModel.setCustomDateRange(start = selectedDate, end = null)
                                    selectingStartDate = false
                                } else {
                                    homeViewModel.setCustomDateRange(
                                        start = homeData.customStartDate,
                                        end = selectedDate
                                    )
                                    showCustomDatePicker = false
                                }
                            }
                        }) {
                            Text(
                                text = if (selectingStartDate) "Select Start" else "Select End",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomDatePicker = false }) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                ) {
                    DatePicker(
                        state = datePickerState,
                        title = {
                            Text(
                                text = if (selectingStartDate) "Pick Start Date" else "Pick End Date",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    )
                }
            }

            // Workout Picker Bottom Sheet
            if (showWorkoutPicker) {
                ModalBottomSheet(
                    onDismissRequest = { showWorkoutPicker = false },
                    sheetState = SheetState(
                        skipPartiallyExpanded = true,
                        density = LocalDensity.current
                    ),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select Workout Type",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            workoutTypes.forEach { (type, icon) ->
                                IconButton(
                                    onClick = {
                                        workoutType = type
                                        showWorkoutPicker = false
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            if (workoutType == type) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else Color.Transparent,
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = type,
                                        tint = if (workoutType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}