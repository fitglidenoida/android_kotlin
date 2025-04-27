package com.trailblazewellness.fitglide.presentation.sleep

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.FitGlideTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    viewModel: SleepViewModel,
    navController: NavController,
    userName: String
) {
    val sleepData by viewModel.sleepData.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    var challengeClaimed by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val scrollState = rememberScrollState()
    val modalSheetState = rememberModalBottomSheetState()

    LaunchedEffect(selectedDate) {
        viewModel.fetchSleepData(selectedDate)
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
                Text(
                    text = "Hey $userName, Rest Up!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous Day", tint = Color(0xFF00C4B4))
                    }
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        fontSize = 16.sp,
                        color = Color(0xFF212121)
                    )
                    IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next Day", tint = Color(0xFF00C4B4))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (sleepData == null) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading sleep data...",
                        fontSize = 16.sp,
                        color = Color(0xFF757575)
                    )
                } else {
                    if (sleepData?.insights?.any { it.contains("date of birth", ignoreCase = true) } == true) {
                        Text(
                            text = sleepData?.insights?.firstOrNull() ?: "Error fetching sleep data",
                            fontSize = 16.sp,
                            color = Color(0xFF757575),
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { navController.navigate("profile") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C4B4))
                        ) {
                            Text("Update Profile", color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (sleepData?.score == 0f && sleepData?.insights?.isNotEmpty() == true) {
                        Text(
                            text = sleepData?.insights?.firstOrNull() ?: "No sleep recorded",
                            fontSize = 16.sp,
                            color = Color(0xFF757575),
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    SleepScoreArc(
                        score = sleepData?.score ?: 0f,
                        debt = sleepData?.debt ?: "0h0m",
                        injuryRisk = sleepData?.injuryRisk ?: 0f,
                        onClick = { showDetails = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AchievementCard(streak = sleepData?.streak ?: 0)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SleepTimeArc("Suggested", sleepData?.restTime ?: 8f, 10f, Color(0xFF4CAF50))
                        SleepTimeArc("Slept", sleepData?.actualSleepTime ?: 0f, sleepData?.restTime ?: 10f, Color(0xFF42A5F5))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sleep Stages",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                    SleepStagesArcs(stages = if (sleepData?.stages.isNullOrEmpty()) listOf(
                        SleepStage(0, "Light"),
                        SleepStage(0, "Deep"),
                        SleepStage(0, "REM")
                    ) else sleepData?.stages ?: emptyList())
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(2.dp, Color(0xFF4CAF50)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Bedtime,
                                    contentDescription = "Sleep",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Actual Sleep Data",
                                    fontSize = 16.sp,
                                    color = Color(0xFF212121),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Text(
                                    text = "Bedtime: ${sleepData?.bedtime ?: "N/A"}",
                                    fontSize = 14.sp,
                                    color = Color(0xFF212121)
                                )
                                Text(
                                    text = "Wake Time: ${sleepData?.alarm ?: "N/A"}${if (sleepData?.alarm == "8:00 AM") " (default)" else ""}",
                                    fontSize = 14.sp,
                                    color = Color(0xFF212121)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sleep Score",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                    Text(
                        text = sleepData?.scoreLegend?.overallScoreDescription ?: "No data",
                        fontSize = 16.sp,
                        color = Color(0xFF424242),
                        modifier = Modifier.padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Insights",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                    if (sleepData?.insights.isNullOrEmpty()) {
                        Text(
                            text = "Nothing to show here",
                            fontSize = 16.sp,
                            color = Color(0xFF757575),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sleepData?.insights ?: emptyList()) { insight ->
                                InsightCard(
                                    text = insight,
                                    onClick = {
                                        if (insight.contains("update your date of birth", ignoreCase = true)) {
                                            navController.navigate("profile")
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (showDetails && sleepData != null) {
                SleepDetailsOverlay(
                    sleepData = sleepData!!,
                    onDismiss = { showDetails = false }
                )
            }

            if (showSettings) {
                ModalBottomSheet(
                    onDismissRequest = { showSettings = false },
                    sheetState = modalSheetState
                ) {
                    SleepSettingsContent(
                        viewModel = viewModel,
                        onSave = { showSettings = false }
                    )
                }
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF00C4B4))
            }

            AnimatedVisibility(
                visible = sleepData?.challengeActive == true && !challengeClaimed,
                enter = fadeIn(animationSpec = tween(500)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { challengeClaimed = true /* TODO: Claim logic */ },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                ) {
                    Text("Claim Goal", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun SleepScoreArc(score: Float, debt: String, injuryRisk: Float, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val radius = size.width / 2 - 10.dp.toPx()
                val cutWidth = 60f
                drawArc(
                    color = Color(0xFF757575),
                    startAngle = 120f,
                    sweepAngle = 360f - cutWidth,
                    useCenter = false,
                    topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                    size = Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF4CAF50),
                    startAngle = 120f,
                    sweepAngle = (360f - cutWidth) * (score / 100f),
                    useCenter = false,
                    topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                    size = Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Moon",
                        tint = Color(0xFF42A5F5),
                        modifier = Modifier
                            .size(36.dp)
                            .offset(x = 0.dp, y = 36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(64.dp))
                Text(
                    text = "${score.toInt()}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Debt: $debt | Risk: ${injuryRisk}%",
            fontSize = 16.sp,
            color = Color(0xFF212121)
        )
    }
}

@Composable
fun SleepStagesArcs(stages: List<SleepStage>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val colors = listOf(Color(0xFF42A5F5), Color(0xFF9575CD), Color(0xFF7E57C2))
        val totalDuration = stages.sumOf { it.duration }.toFloat()
        stages.forEachIndexed { index, stage ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        val radius = size.width / 2 - 5.dp.toPx()
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = -90f,
                            sweepAngle = if (totalDuration > 0) 360f * (stage.duration / totalDuration) else 0f,
                            useCenter = false,
                            topLeft = Offset(size.width / 2 - radius - 5.dp.toPx(), size.height / 2 - radius - 5.dp.toPx()),
                            size = Size((radius + 5.dp.toPx()) * 2, (radius + 5.dp.toPx()) * 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "${stage.duration}m",
                        fontSize = 16.sp,
                        color = Color(0xFF212121)
                    )
                }
                Text(
                    text = stage.type,
                    fontSize = 12.sp,
                    color = Color(0xFF212121),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SleepTimeArc(label: String, value: Float, max: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(80.dp)) {
                val radius = size.width / 2 - 5.dp.toPx()
                drawArc(
                    color = Color(0xFF757575),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                    size = Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = if (max > 0) 360f * (value / max) else 0f,
                    useCenter = false,
                    topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                    size = Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = if (label == "Slept") formatSleepTime(value) else "${String.format("%.1f", value)}h",
                fontSize = 16.sp,
                color = Color(0xFF212121)
            )
        }
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF212121)
        )
    }
}

@Composable
fun AchievementCard(streak: Int) {
    if (streak > 0) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFF3E0),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Achievement",
                    tint = Color(0xFF212121),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Streak: $streak days - ${
                        when {
                            streak >= 14 -> "Dream Master"
                            streak >= 7 -> "Rest King"
                            streak >= 3 -> "Sleep Star"
                            else -> ""
                        }
                    }",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
            }
        }
    }
}

@Composable
fun InsightCard(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE8F5E9),
        modifier = Modifier
            .width(200.dp)
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF424242),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun SleepDetailsOverlay(sleepData: SleepDataUi, onDismiss: () -> Unit) {
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
                Text("Sleep Details", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Debt: ${sleepData.debt}", fontSize = 16.sp)
                Text("Injury Risk: ${sleepData.injuryRisk}%", fontSize = 16.sp)
                Text("Rest Tonight: ${sleepData.restTime}h", fontSize = 16.sp)
                Text("Slept Last Night: ${formatSleepTime(sleepData.actualSleepTime)}", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Score Explanation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(sleepData.scoreLegend.overallScoreDescription, fontSize = 14.sp)
            }
        }
    }
}

private fun formatSleepTime(hours: Float): String {
    val totalMinutes = (hours * 60).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}h${m}m"
}