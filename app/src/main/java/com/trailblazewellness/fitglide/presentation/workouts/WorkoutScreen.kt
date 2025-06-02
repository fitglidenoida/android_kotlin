package com.trailblazewellness.fitglide.presentation.workouts

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.android.gms.maps.model.LatLng
import com.trailblazewellness.fitglide.FitGlideTheme
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    navController: NavController,
    userName: String,
    homeViewModel: HomeViewModel,
    commonViewModel: CommonViewModel
) {
    val workoutData by viewModel.workoutData.collectAsState()
    val homeData by homeViewModel.homeData.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var fabExpanded by remember { mutableStateOf(false) }
    val fabRotation by animateFloatAsState(
        targetValue = if (fabExpanded) 45f else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    var showDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(workoutData) {
        Log.d("WorkoutDebug", "WorkoutData: steps=${workoutData.steps}, heartRate=${workoutData.heartRate}, caloriesBurned=${workoutData.caloriesBurned}, schedule.size=${workoutData.schedule.size}")
    }

    FitGlideTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hey $userName, Power Up!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.setDate(workoutData.selectedDate.minusDays(1)) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Day",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = workoutData.selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { viewModel.setDate(workoutData.selectedDate.plusDays(1)) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Day",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Steps Section
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val density = LocalDensity.current
                    val strokeWidthPx = with(density) { 10.dp.toPx() }
                    val arcColor = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier
                            .size(180.dp)
                            .padding(8.dp)
                    ) {
                        val radius = size.width / 2 - strokeWidthPx / 2
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = 360f * (workoutData.steps / homeData.stepGoal).coerceAtMost(1f),
                            useCenter = false,
                            topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${workoutData.steps.toInt()}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Steps",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Heart Rate
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val density = LocalDensity.current
                        val strokeWidthPx = with(density) { 10.dp.toPx() }
                        val arcColor = MaterialTheme.colorScheme.secondary
                        Canvas(
                            modifier = Modifier
                                .size(80.dp)
                                .padding(4.dp)
                        ) {
                            val radius = size.width / 2 - strokeWidthPx / 2
                            drawArc(
                                color = arcColor,
                                startAngle = -90f,
                                sweepAngle = 360f * (workoutData.heartRate / homeData.maxHeartRate).coerceAtMost(1f),
                                useCenter = false,
                                topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${workoutData.heartRate.toInt()}",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "BPM",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Calories
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val density = LocalDensity.current
                        val strokeWidthPx = with(density) { 10.dp.toPx() }
                        val arcColor = MaterialTheme.colorScheme.tertiary
                        Canvas(
                            modifier = Modifier
                                .size(80.dp)
                                .padding(4.dp)
                        ) {
                            val radius = size.width / 2 - strokeWidthPx / 2
                            drawArc(
                                color = arcColor,
                                startAngle = -90f,
                                sweepAngle = 360f * (workoutData.caloriesBurned / 500f).coerceAtMost(1f),
                                useCenter = false,
                                topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${workoutData.caloriesBurned.toInt()}",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cal",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Stress
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val density = LocalDensity.current
                        val strokeWidthPx = with(density) { 10.dp.toPx() }
                        val arcColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .size(80.dp)
                                .padding(4.dp)
                        ) {
                            val radius = size.width / 2 - strokeWidthPx / 2
                            drawArc(
                                color = arcColor,
                                startAngle = -90f,
                                sweepAngle = 360f * when (homeData.stressScore) {
                                    in 0..33 -> 0.3f
                                    in 34..66 -> 0.6f
                                    else -> 0.9f
                                },
                                useCenter = false,
                                topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (homeData.stressScore) {
                                in 0..33 -> "Low"
                                in 34..66 -> "Medium"
                                else -> "High"
                            },
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Stress",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Current Workout
                Text(
                    text = "Current Workout",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val currentWorkout = workoutData.schedule.firstOrNull {
                    it.date == workoutData.selectedDate && it.type == workoutData.selectedGoal && it.moves.any { !it.isCompleted }
                }
                if (currentWorkout != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (currentWorkout.type) {
                                        "Strength" -> Icons.Default.FitnessCenter
                                        "Cardio" -> Icons.AutoMirrored.Filled.DirectionsRun
                                        "Flex" -> Icons.Default.SelfImprovement
                                        else -> Icons.Default.FitnessCenter
                                    },
                                    contentDescription = currentWorkout.type,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${currentWorkout.type} - ${currentWorkout.time}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                "${workoutData.caloriesBurned.toInt()} cal",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No active workout scheduled",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Workout Plan
                Text(
                    text = "Workout Plan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val plansForDate = workoutData.plans.filter { it.date == workoutData.selectedDate }
                if (plansForDate.isNotEmpty()) {
                    plansForDate.forEach { plan ->
                        WorkoutPlanCard(slot = plan, viewModel = viewModel)
                    }
                } else {
                    Text(
                        text = "No workout plans for this date",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Workout Log
                Text(
                    text = "Workout Log",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val workoutsForDate = workoutData.schedule.filter {
                    it.date == workoutData.selectedDate && it.type == workoutData.selectedGoal
                }
                if (workoutsForDate.isNotEmpty()) {
                    workoutsForDate.forEachIndexed { index, workout ->
                        WorkoutCard(
                            slot = workout,
                            viewModel = viewModel,
                            workoutIndex = index,
                            onClick = { navController.navigate("workout_detail/${workout.id}") }
                        )
                    }
                } else {
                    Text(
                        text = "No workouts logged for this date. Check Health Connect permissions if expected.",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Insights
                Text(
                    text = "Insights",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        workoutData.insights.forEach { insight ->
                            Text(
                                insight,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (workoutData.insights.isEmpty()) {
                            Text(
                                "Keep moving to get personalized insights!",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Challenges
                Text(
                    text = "Challenges",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                workoutData.challenges.forEach { challenge ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                challenge.goal,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${challenge.progress}/${challenge.target}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (workoutData.challenges.isEmpty()) {
                    Text(
                        text = "No active challenges. Start a new one!",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Streak
                if (workoutData.streak > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Streak",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Streak: ${workoutData.streak} days",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            if (showDetails) {
                WorkoutDetailsOverlay(workoutData, homeData.stressScore, { showDetails = false })
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                if (fabExpanded) {
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create Workout", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                navController.navigate("workout_plan")
                                showDropdown = false
                                fabExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Success Story", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                homeViewModel.onCreateStoryClicked()
                                showDropdown = false
                                fabExpanded = false
                            }
                        )
                    }
                }
                FloatingActionButton(
                    onClick = {
                        fabExpanded = !fabExpanded
                        showDropdown = fabExpanded
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Options",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(36.dp)
                            .rotate(fabRotation)
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutPlanCard(slot: WorkoutSlot, viewModel: WorkoutViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    var isStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // Snackbar Host
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (data.visuals.message.contains("Failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Plan Details (Top Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = when (slot.type) {
                            "Strength" -> Icons.Default.FitnessCenter
                            "Cardio" -> Icons.AutoMirrored.Filled.DirectionsRun
                            "Flex" -> Icons.Default.SelfImprovement
                            else -> Icons.Default.FitnessCenter
                        },
                        contentDescription = "Workout Type",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${slot.type} - ${slot.time}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Plan Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Start Button
            if (!slot.isCompleted && !isStarted) {
                Button(
                    onClick = {
                        viewModel.startWorkout(slot.id, listOf("2", "3")) { success, message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                                if (success) {
                                    isStarted = true
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Start Workout", color = MaterialTheme.colorScheme.onPrimary)
                }
            } else if (isStarted && !slot.isCompleted) {
                Button(
                    onClick = { /* No action; workout in progress */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    enabled = false
                ) {
                    Text("In Progress", color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Completion Status
            if (slot.isCompleted) {
                Text(
                    text = "All workouts done!",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Exercises List
            if (slot.moves.isEmpty()) {
                Text(
                    text = "No exercises planned",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                slot.moves.forEachIndexed { index, move ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Exercise Name
                        Text(
                            text = move.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Reps/Sets
                        Text(
                            text = move.repsOrTime,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(horizontal = 8.dp)
                        )
                        // Completion Checkbox
                        if (!slot.isCompleted) {
                            Checkbox(
                                checked = move.isCompleted,
                                onCheckedChange = { viewModel.toggleMove(slot.id, index) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (index < slot.moves.size - 1) {
                        Divider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun WorkoutCard(
    slot: WorkoutSlot,
    viewModel: WorkoutViewModel,
    workoutIndex: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // Workout Details (Top Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = when (slot.type) {
                            "Strength" -> Icons.Default.FitnessCenter
                            "Cardio" -> Icons.AutoMirrored.Filled.DirectionsRun
                            "Flex" -> Icons.Default.SelfImprovement
                            else -> Icons.Default.FitnessCenter
                        },
                        contentDescription = "Workout Type",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${slot.type} - ${slot.time}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Row (Heart Rate, Calories)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HR: ${viewModel.workoutData.value.heartRate.toInt()} bpm",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${viewModel.workoutData.value.caloriesBurned.toInt()} cal",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Moves List
            if (slot.moves.isEmpty()) {
                Text(
                    text = "No exercises recorded",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                slot.moves.forEachIndexed { index, move ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Move Name
                        Text(
                            text = move.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Reps/Sets
                        Text(
                            text = move.repsOrTime,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(horizontal = 8.dp)
                        )
                    }
                    if (index < slot.moves.size - 1) {
                        Divider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutDetailsOverlay(workoutData: WorkoutUiData, stressScore: Int, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .width(300.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Workout Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Calories Burned: ${workoutData.caloriesBurned.toInt()} cal",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Heart Rate: ${workoutData.heartRate.toInt()} BPM",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Steps: ${workoutData.steps.toInt()}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Stress: ${when (stressScore) {
                        in 0..33 -> "Low"
                        in 34..66 -> "Medium"
                        else -> "High"
                    }}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

data class WorkoutUiData(
    val heartRate: Float,
    val caloriesBurned: Float,
    val steps: Float,
    val schedule: List<WorkoutSlot>,
    val streak: Int,
    val challenges: List<Challenge>,
    val insights: List<String>,
    val selectedDate: LocalDate,
    val selectedGoal: String,
    val plans: List<WorkoutSlot>
)

data class WorkoutSlot(
    val id: String,
    val type: String,
    val time: String,
    val moves: List<WorkoutMove>,
    val date: LocalDate,
    val isCompleted: Boolean = false
)

data class WorkoutMove(
    val name: String,
    val repsOrTime: String,
    val sets: Int,
    val isCompleted: Boolean,
    val imageUrl: String? = null,
    val instructions: String? = null
)

data class Challenge(
    val goal: String,
    val progress: Int,
    val target: Int
)

data class WorkoutLog(
    val documentId: String,
    val logId: String,
    val startTime: String,
    val endTime: String,
    val distance: Double?,
    val calories: Double?,
    val heartRateAvg: Long?,
    val sportType: String,
    val moves: List<WorkoutMove>,
    val route: Route?,
    val completed: Boolean
)

data class Route(
    val coordinates: List<LatLng>,
    val distance: Double
)