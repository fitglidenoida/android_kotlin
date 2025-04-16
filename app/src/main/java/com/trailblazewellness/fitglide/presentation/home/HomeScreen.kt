package com.trailblazewellness.fitglide.presentation.home

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.trailblazewellness.fitglide.FitGlideTheme
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.workers.WorkoutTrackingService
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    context: android.content.Context,
    healthConnectManager: HealthConnectManager,
    homeViewModel: HomeViewModel,
    commonViewModel: CommonViewModel,
    userName: String
) {
    val isLoading by commonViewModel.isLoading.collectAsState()
    val uiMessage by commonViewModel.uiMessage.collectAsState()
    val homeData by homeViewModel.homeData.collectAsState()
    val date by commonViewModel.date.collectAsState()
    val posts by commonViewModel.posts.collectAsState()
    val trackedSteps by commonViewModel.trackedStepsFlow.collectAsState()
    val challenges by commonViewModel.challenges.collectAsState(initial = emptyList())
    val scrollState = rememberScrollState()
    var showTrackingPopup by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var workoutType by remember { mutableStateOf("Walking") }
    var showWorkoutPicker by remember { mutableStateOf(false) }
    val workoutTypes = listOf("Walking", "Running", "Cycling", "Hiking", "Swimming", "Other")
    val snackbarHostState = remember { SnackbarHostState() }
    var showMaxPopup by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            homeViewModel.refreshData()
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(trackedSteps) {
        if (trackedSteps > 5 && !homeData.isTracking && !showTrackingPopup) {
            showTrackingPopup = true
            Log.d("HomeScreen", "Showing tracking popup: trackedSteps=$trackedSteps")
        }
    }

    LaunchedEffect(Unit) {
        Log.d("DesiMaxDebug", "⏳ Launching HomeScreen, initializing ViewModel...")
        homeViewModel.initializeWithContext()
    }

    LaunchedEffect(homeData.maxMessage) {
        Log.d("DesiMaxDebug", "Max message updated: yesterday=${homeData.maxMessage.yesterday}, today=${homeData.maxMessage.today}, hasPlayed=${homeData.maxMessage.hasPlayed}")
        if (homeData.maxMessage.yesterday.isNotBlank() && !homeData.maxMessage.hasPlayed) {
            showMaxPopup = true
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
                                listOf(Color(0xFF4CAF50), Color(0xFF81C784))
                            )
                        )
                        .padding(vertical = 16.dp, horizontal = 24.dp)
                ) {
                    Text(
                        text = "Hey $userName!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            strokeWidth = 4.dp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(Color(0xFFF5F5F5))
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
                            colors = CardDefaults.cardColors(containerColor = Color.White)
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
                                        tint = Color(0xFF4CAF50)
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
                                        "Custom" -> "${
                                            date.minusDays(6).format(
                                                DateTimeFormatter.ofPattern("MMM d")
                                            )
                                        } - ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
                                        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121)
                                )
                                Row {
                                    IconButton(onClick = { showRangePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Range Picker",
                                            tint = Color(0xFF4CAF50)
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
                                            tint = Color(0xFF4CAF50)
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
                            Canvas(modifier = Modifier.size(160.dp)) {
                                val totalStepsDisplay = homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps
                                val radius = size.width / 2 - 10.dp.toPx()
                                drawArc(
                                    color = Color(0xFF4CAF50),
                                    startAngle = -90f,
                                    sweepAngle = 360f * (totalStepsDisplay / homeData.stepGoal).coerceIn(0f, 1f),
                                    useCenter = false,
                                    topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 10.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                            Text(
                                text = "${(homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps).toInt()}",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = "Steps (${homeData.dateRangeMode})",
                                fontSize = 14.sp,
                                color = Color(0xFF757575)
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
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    val radius = size.width / 2 - 10.dp.toPx()
                                    drawArc(
                                        color = Color(0xFFFF5722),
                                        startAngle = -90f,
                                        sweepAngle = 360f * (homeData.heartRate / homeData.maxHeartRate).coerceIn(0f, 1f),
                                        useCenter = false,
                                        topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 10.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }
                                Text(
                                    text = "Avg HR: ${homeData.heartRate.toInt()}/${homeData.maxHeartRate.toInt()}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF212121)
                                )
                                Text(
                                    text = "BPM",
                                    fontSize = 12.sp,
                                    color = Color(0xFF757575)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    val radius = size.width / 2 - 10.dp.toPx()
                                    drawArc(
                                        color = Color(0xFF9C27B0),
                                        startAngle = -90f,
                                        sweepAngle = 360f * (homeData.caloriesBurned / homeData.bmr.toFloat()).coerceIn(0f, 1f),
                                        useCenter = false,
                                        topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 10.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }
                                Text(
                                    text = "${homeData.caloriesBurned.toInt()}/${homeData.bmr}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF212121)
                                )
                                Text(
                                    text = "Cal",
                                    fontSize = 12.sp,
                                    color = Color(0xFF757575)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    val radius = size.width / 2 - 10.dp.toPx()
                                    drawArc(
                                        color = Color(0xFF00C4B4),
                                        startAngle = -90f,
                                        sweepAngle = 360f * 0.3f,
                                        useCenter = false,
                                        topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 10.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }
                                Text(
                                    text = homeData.stressScore,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF212121)
                                )
                                Text(
                                    text = "Stress",
                                    fontSize = 12.sp,
                                    color = Color(0xFF757575)
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
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            workoutType =
                                                workoutTypes[(workoutTypes.indexOf(workoutType) + 1) % workoutTypes.size]
                                        }
                                    ) {
                                        Icon(
                                            imageVector = when (workoutType) {
                                                "Walking" -> Icons.AutoMirrored.Filled.DirectionsWalk
                                                "Running" -> Icons.Default.DirectionsRun
                                                "Cycling" -> Icons.Default.DirectionsBike
                                                "Hiking" -> Icons.Default.Terrain
                                                "Swimming" -> Icons.Default.Pool
                                                else -> Icons.Default.FitnessCenter
                                            },
                                            contentDescription = workoutType,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Canvas(modifier = Modifier.size(60.dp)) {
                                        val totalStepsDisplay =
                                            homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps
                                        val progress =
                                            (totalStepsDisplay / homeData.stepGoal).coerceIn(0f, 1f)
                                        val color = when {
                                            progress >= 1f -> Color(0xFF4CAF50)
                                            progress >= 0.5f -> Color(0xFFFF9800)
                                            else -> Color(0xFFCCCCCC)
                                        }
                                        drawArc(
                                            color = color,
                                            startAngle = -90f,
                                            sweepAngle = 360f * progress,
                                            useCenter = false,
                                            topLeft = Offset(
                                                size.width / 2 - 25.dp.toPx(),
                                                size.height / 2 - 25.dp.toPx()
                                            ),
                                            size = Size(50.dp.toPx(), 50.dp.toPx()),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = 8.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "${(homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps).toInt()}",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF212121)
                                        )
                                        Text(
                                            text = "Goal: ${homeData.stepGoal.toInt()}",
                                            fontSize = 14.sp,
                                            color = Color(0xFF757575)
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            context,
                                            WorkoutTrackingService::class.java
                                        ).apply {
                                            putExtra(
                                                "userId",
                                                commonViewModel.getAuthRepository().getAuthState().getId() ?: "1"
                                            )
                                            putExtra("workoutType", workoutType)
                                            putExtra("manualStart", true)
                                            putExtra("token", commonViewModel.getAuthRepository().getAuthState().jwt ?: "")
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
                                        containerColor = if (homeData.isTracking) Color(0xFFEF5350) else Color(0xFF4CAF50)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (homeData.isTracking) "Stop" else "Start",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Navigation Cards
                        NavigationCard(
                            icon = Icons.Default.FitnessCenter,
                            label = "Strength - ${homeData.caloriesBurned.toInt()} cal",
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

                        // Max Insights (only show after popup is dismissed)
                        if (homeData.maxMessage.hasPlayed) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .shadow(6.dp, RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
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
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Max says: ${homeData.maxMessage.today.takeIf { it.isNotBlank() } ?: "Push HR to 130!"}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF212121)
                                        )
                                    }
                                    IconButton(onClick = {
                                        showMaxPopup = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Replay Max",
                                            tint = Color(0xFF4CAF50)
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
                            colors = CardDefaults.cardColors(containerColor = Color.White)
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
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stepChallenge?.let {
                                        "${it.type}: ${homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps}/${it.goal} steps"
                                    } ?: "No active step challenge",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF212121)
                                )
                            }
                        }

                        // Achievements
                        Text(
                            text = "Achievements",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121),
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(homeData.badges) { badge ->
                                Card(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(180.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        AsyncImage(
                                            model = badge.iconUrl ?: "https://admin.fitglide.in/uploads/placeholder_badge.png",
                                            contentDescription = badge.title,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = badge.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF212121),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = badge.description,
                                            fontSize = 12.sp,
                                            color = Color(0xFF757575),
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }

                        // Stories/Leaderboard
                        Text(
                            text = if (homeData.showStories) "Success Stories" else "Leaderboard",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121),
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .shadow(6.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = homeData.storiesOrLeaderboard.firstOrNull() ?: "No data yet",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF212121)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { homeViewModel.toggleStoriesOrLeaderboard() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (homeData.showStories) "See Leaderboard" else "See Stories",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                PullToRefreshContainer(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullToRefreshState,
                    contentColor = Color(0xFF4CAF50),
                    containerColor = Color.White
                )
            }
        }

        // Max Popup
        if (showMaxPopup && !homeData.maxMessage.hasPlayed) {
            Log.d("DesiMaxDebug", "Rendering Max popup: yesterday=${homeData.maxMessage.yesterday}, today=${homeData.maxMessage.today}")
            AlertDialog(
                onDismissRequest = {
                    showMaxPopup = false
                    homeViewModel.markMaxMessagePlayed()
                },
                title = {
                    Text(
                        "Max Says 💬",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                },
                text = {
                    Column {
                        Text(
                            homeData.maxMessage.yesterday,
                            fontSize = 16.sp,
                            color = Color(0xFF424242)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            homeData.maxMessage.today,
                            fontSize = 16.sp,
                            color = Color(0xFF424242)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showMaxPopup = false
                        homeViewModel.markMaxMessagePlayed()
                    }) {
                        Text("Got it!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White
            )
        }

        // Tracking Popup
        if (showTrackingPopup) {
            AlertDialog(
                onDismissRequest = { showTrackingPopup = false },
                title = {
                    Text(
                        "Max Says",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                },
                text = {
                    Text(
                        "You're walking but not tracking! Start now?",
                        fontSize = 16.sp,
                        color = Color(0xFF424242)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                            putExtra(
                                "userId",
                                commonViewModel.getAuthRepository().getAuthState().getId() ?: "1"
                            )
                            putExtra("workoutType", workoutType)
                            putExtra("manualStart", true)
                            putExtra("token", commonViewModel.getAuthRepository().getAuthState().jwt ?: "")
                        }
                        Log.d("HomeScreen", "Attempting to start WorkoutTrackingService with type: $workoutType")
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
                        Text("Start", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showTrackingPopup = false
                        commonViewModel.updateTrackedSteps(0f)
                    }) {
                        Text("Later", color = Color(0xFF757575))
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White
            )
        }

        // Range Picker Popup
        if (showRangePicker) {
            AlertDialog(
                onDismissRequest = { showRangePicker = false },
                title = {
                    Text(
                        "Select Range",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                },
                text = {
                    Column {
                        TextButton(onClick = {
                            homeViewModel.setDateRangeMode("Day"); showRangePicker = false
                        }) {
                            Text("Day", color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                        }
                        TextButton(onClick = {
                            homeViewModel.setDateRangeMode("Week"); showRangePicker = false
                        }) {
                            Text("Week", color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                        }
                        TextButton(onClick = {
                            homeViewModel.setDateRangeMode("Custom"); showRangePicker = false
                        }) {
                            Text(
                                "Custom (TBD)",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {},
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White
            )
        }

        // Workout Picker Popup
        if (showWorkoutPicker) {
            AlertDialog(
                onDismissRequest = { showWorkoutPicker = false },
                title = {
                    Text(
                        "Select Workout Type",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                },
                text = {
                    Column {
                        workoutTypes.forEach { type ->
                            TextButton(
                                onClick = {
                                    workoutType = type
                                    showWorkoutPicker = false
                                }
                            ) {
                                Text(
                                    type,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White
            )
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
        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF212121)
            )
        }
    }
}