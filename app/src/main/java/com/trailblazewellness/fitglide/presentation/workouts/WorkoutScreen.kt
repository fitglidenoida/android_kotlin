package com.trailblazewellness.fitglide.presentation.workout

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.FitGlideTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    navController: NavController,
    userName: String
) {
    val workoutData by viewModel.workoutData.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Log workoutData for debugging
    LaunchedEffect(workoutData) {
        Log.d("WorkoutScreen", "WorkoutData: steps=${workoutData.steps}, heartRate=${workoutData.heartRate}, caloriesBurned=${workoutData.caloriesBurned}, schedule.size=${workoutData.schedule.size}")
    }

    FitGlideTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFFFFF))
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Greeting
                Text(
                    text = "Hey $userName, Power Up!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.setDate(workoutData.selectedDate.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
                    }
                    Text(
                        text = workoutData.selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        fontSize = 18.sp,
                        color = Color(0xFF212121)
                    )
                    IconButton(onClick = { viewModel.setDate(workoutData.selectedDate.plusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Steps Circle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val radius = size.width / 2 - 10.dp.toPx()
                        drawArc(
                            color = Color(0xFF4CAF50),
                            startAngle = -90f,
                            sweepAngle = 360f * (workoutData.steps / 10000f), // Max 10K steps
                            useCenter = false,
                            topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                            size = Size(radius * 2, radius * 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text("${workoutData.steps.toInt()}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                    Text("Steps", fontSize = 14.sp, color = Color(0xFF757575))
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Stats (HR, Calories, Stress)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Heart Rate
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val radius = size.width / 2 - 10.dp.toPx()
                            drawArc(
                                color = Color(0xFFFF5722),
                                startAngle = -90f,
                                sweepAngle = 360f * (workoutData.heartRate / 200f), // Max 200 BPM
                                useCenter = false,
                                topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                size = Size(radius * 2, radius * 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text("${workoutData.heartRate.toInt()}", fontSize = 16.sp, color = Color(0xFF212121))
                        Text("BPM", fontSize = 12.sp, color = Color(0xFF757575))
                    }
                    // Calories Burned
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val radius = size.width / 2 - 10.dp.toPx()
                            drawArc(
                                color = Color(0xFF9C27B0),
                                startAngle = -90f,
                                sweepAngle = 360f * (workoutData.caloriesBurned / 500f), // Max 500 cal
                                useCenter = false,
                                topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                size = Size(radius * 2, radius * 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text("${workoutData.caloriesBurned.toInt()}", fontSize = 16.sp, color = Color(0xFF212121))
                        Text("Cal", fontSize = 12.sp, color = Color(0xFF757575))
                    }
                    // Stress Score (Stubbed)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val radius = size.width / 2 - 10.dp.toPx()
                            drawArc(
                                color = Color(0xFF00C4B4),
                                startAngle = -90f,
                                sweepAngle = 360f * 0.3f, // Stubbed Low
                                useCenter = false,
                                topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                size = Size(radius * 2, radius * 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text("Low", fontSize = 16.sp, color = Color(0xFF212121))
                        Text("Stress", fontSize = 12.sp, color = Color(0xFF757575))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Goal Picker
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("Strength", "Cardio", "Flex")) { goal ->
                        FilterChip(
                            selected = workoutData.selectedGoal == goal,
                            onClick = { viewModel.setGoal(goal) },
                            label = { Text(goal) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Current Workout Card
                Text(
                    text = "Current Workout",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                val currentWorkout = workoutData.schedule.firstOrNull {
                    it.date == workoutData.selectedDate && it.type == workoutData.selectedGoal
                }
                if (currentWorkout != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(2.dp, Color(0xFF4CAF50)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
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
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${currentWorkout.type} - ${currentWorkout.time}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121)
                                )
                            }
                            Text(
                                "${workoutData.caloriesBurned.toInt()} cal",
                                fontSize = 14.sp,
                                color = Color(0xFF757575)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No workout scheduled",
                        fontSize = 16.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Active Workout Card
                val activeWorkout = workoutData.schedule.firstOrNull {
                    it.date == workoutData.selectedDate &&
                            it.type == workoutData.selectedGoal &&
                            it.moves.any { !it.isCompleted }
                }
                if (activeWorkout != null) {
                    WorkoutCard(activeWorkout, viewModel, workoutData.schedule.indexOf(activeWorkout))
                } else {
                    Text(
                        text = "All Workouts Done",
                        fontSize = 16.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Insights Card
                Text(
                    text = "Insights",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text("Push HR to 130 for max burn!", fontSize = 14.sp, color = Color(0xFF424242))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Steps on track—aim for 10K!", fontSize = 14.sp, color = Color(0xFF424242))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Challenges + Streak
                Text(
                    text = "Challenges",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(workoutData.dailyChallenge, fontSize = 14.sp, color = Color(0xFF212121))
                        Text("${workoutData.challengeProgress}/3", fontSize = 12.sp, color = Color(0xFF757575))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                workoutData.buddyChallenges.forEach { challenge ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${challenge.buddyName}: ${challenge.goal}", fontSize = 14.sp, color = Color(0xFF212121))
                            Text("${challenge.userProgress}/${challenge.buddyProgress}", fontSize = 12.sp, color = Color(0xFF757575))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (workoutData.streak > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Streak",
                                tint = Color(0xFF212121),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Streak: ${workoutData.streak} days",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )
                        }
                    }
                }
            }

            // Workout Details Overlay
            if (showDetails) {
                WorkoutDetailsOverlay(workoutData, { showDetails = false })
            }
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun WorkoutCard(slot: WorkoutSlot, viewModel: WorkoutViewModel, workoutIndex: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, Color(0xFF4CAF50)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (slot.type) {
                            "Strength" -> Icons.Default.FitnessCenter
                            "Cardio" -> Icons.AutoMirrored.Filled.DirectionsRun
                            "Flex" -> Icons.Default.SelfImprovement
                            else -> Icons.Default.FitnessCenter
                        },
                        contentDescription = "Workout Type",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${slot.type} - ${slot.time}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                }
                Text(
                    "HR: ${viewModel.workoutData.value.heartRate.toInt()}",
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                slot.moves.forEachIndexed { index, move ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            move.name,
                            fontSize = 14.sp,
                            color = Color(0xFF424242),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            move.repsOrTime,
                            fontSize = 14.sp,
                            color = Color(0xFF424242),
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Switch(
                            checked = move.isCompleted,
                            onCheckedChange = { viewModel.toggleMove(workoutIndex, index) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50)),
                            modifier = Modifier
                                .size(40.dp)
                                .weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${viewModel.workoutData.value.caloriesBurned.toInt()} cal burned",
                fontSize = 14.sp,
                color = Color(0xFF757575)
            )
        }
    }
}

@Composable
fun DailySchedule(schedule: List<WorkoutSlot>, viewModel: WorkoutViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        items(schedule) { slot ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (slot.moves.all { it.isCompleted }) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { /* TODO: Jump to workout in card */ }
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        Icon(
                            imageVector = when (slot.type) {
                                "Strength" -> Icons.Default.FitnessCenter
                                "Cardio" -> Icons.AutoMirrored.Filled.DirectionsRun
                                "Flex" -> Icons.Default.SelfImprovement
                                else -> Icons.Default.FitnessCenter
                            },
                            contentDescription = "Workout Type",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${slot.type} - ${slot.time}", fontSize = 14.sp, color = Color(0xFF212121))
                    }
                    Text(
                        if (slot.moves.all { it.isCompleted }) "Completed" else "Pending",
                        fontSize = 12.sp,
                        color = if (slot.moves.all { it.isCompleted }) Color(0xFF4CAF50) else Color(0xFFFF5722)
                    )
                }
            }
        }
    }
}

@Composable
fun QuestCard(goal: String, progress: Float) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFF3E0),
        modifier = Modifier
            .width(200.dp)
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(goal, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
            Text("${progress.toInt()}g", fontSize = 12.sp, color = Color(0xFF757575))
        }
    }
}

@Composable
fun RecipeCarousel() {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listOf("Dal", "Roti", "Biryani")) { recipe ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE8F5E9),
                modifier = Modifier.width(200.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(recipe, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                    Text("300 cal", fontSize = 12.sp, color = Color(0xFF757575))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* TODO: Log recipe */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Log", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MealsDetailsOverlay(workoutData: WorkoutData, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Workout Details", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Calories Burned: ${workoutData.caloriesBurned.toInt()} cal", fontSize = 16.sp)
                Text("Heart Rate: ${workoutData.heartRate.toInt()} BPM", fontSize = 16.sp)
                Text("Steps: ${workoutData.steps.toInt()}", fontSize = 16.sp)
            }
        }
    }
}
@Composable
fun WorkoutDetailsOverlay(workoutData: WorkoutData, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Workout Details", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Calories Burned: ${workoutData.caloriesBurned.toInt()} cal", fontSize = 16.sp)
                Text("Heart Rate: ${workoutData.heartRate.toInt()} BPM", fontSize = 16.sp)
                Text("Steps: ${workoutData.steps.toInt()}", fontSize = 16.sp)
            }
        }
    }
}
