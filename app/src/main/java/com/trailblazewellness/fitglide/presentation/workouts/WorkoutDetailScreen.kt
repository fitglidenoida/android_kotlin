package com.trailblazewellness.fitglide.presentation.workouts

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.navigation.NavController
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.trailblazewellness.fitglide.R
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnrememberedMutableState")
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    strapiApi: StrapiApi,
    commonViewModel: CommonViewModel,
    navController: NavController,
    healthConnectManager: HealthConnectManager
) {
    val workoutLogState = remember { mutableStateOf<StrapiApi.WorkoutLogEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(workoutId) {
        try {
            val token = "Bearer ${commonViewModel.getAuthRepository().getAuthState().jwt}"
            val response = strapiApi.getWorkoutLogs(
                filters = mapOf("logId" to workoutId),
                token = token,
                contentType = "application/json",
                accept = "application/json"
            )

            if (response.isSuccessful) {
                val strapiLog = response.body()?.data?.firstOrNull()
                if (strapiLog != null) {
                    val startTime = try {
                        Instant.parse(strapiLog.startTime)
                    } catch (e: Exception) {
                        Instant.now().minusSeconds(3600)
                    }
                    val endTime = try {
                        Instant.parse(strapiLog.endTime)
                    } catch (e: Exception) {
                        Instant.now()
                    }
                    val workoutData = try {
                        val startDate = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault()).toLocalDate()
                        healthConnectManager.readExerciseSessions(startDate).find { it.start?.toString()?.contains(workoutId) == true }
                    } catch (e: Exception) {
                        Log.e("WorkoutDetailScreen", "HealthConnect read failed: ${e.message}")
                        null
                    }
                    val heartRateStats = try {
                        healthConnectManager.readHeartRateStats(startTime, endTime)
                    } catch (e: Exception) {
                        Log.e("WorkoutDetailScreen", "HeartRate read failed: ${e.message}")
                        null
                    }
                    workoutLogState.value = strapiLog.copy(
                        heartRateAverage = workoutData?.heartRateAvg ?: strapiLog.heartRateAverage,
                        heartRateMaximum = heartRateStats?.maximum ?: strapiLog.heartRateMaximum,
                        heartRateMinimum = heartRateStats?.minimum ?: strapiLog.heartRateMinimum,
                        distance = workoutData?.distance?.toFloat() ?: strapiLog.distance,
                        totalTime = workoutData?.duration?.toSeconds()?.toFloat()?.div(3600f) ?: strapiLog.totalTime,
                        type = workoutData?.type ?: strapiLog.type
                    )
                    Log.d("WorkoutDetailScreen", "WorkoutLog: ${workoutLogState.value}")
                    Log.d("WorkoutDetailScreen", "HealthConnect WorkoutData: $workoutData")
                    Log.d("WorkoutDetailScreen", "HealthConnect HeartRateStats: $heartRateStats")
                }
            } else {
                Log.e("WorkoutDetailScreen", "Failed to fetch workout details: ${response.code()}")
                scope.launch { snackbarHostState.showSnackbar("Failed to fetch workout details") }
            }
        } catch (e: Exception) {
            Log.e("WorkoutDetailScreen", "Error fetching workout: ${e.message}")
            scope.launch { snackbarHostState.showSnackbar("Error fetching workout details") }
        }

        val apiKey = try {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            null
        }
        Log.d("WorkoutDetailScreen", "Maps API Key: $apiKey")
    }

    val workoutLog = workoutLogState.value

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Workout Details", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (workoutLog != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    workoutLog.route?.let { route ->
                        item {
                            val latLngList = parseRoute(route)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                GoogleMap(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    cameraPositionState = rememberCameraPositionState {
                                        position = CameraPosition(
                                            latLngList.firstOrNull() ?: LatLng(0.0, 0.0), 15f, 0f, 0f
                                        )
                                    },
                                    properties = MapProperties(
                                        mapStyleOptions = try {
                                            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_dark_style)
                                        } catch (e: Exception) {
                                            Log.e("WorkoutDetailScreen", "Failed to load map style: ${e.message}")
                                            null
                                        }
                                    )
                                ) {
                                    Polyline(
                                        points = latLngList,
                                        color = MaterialTheme.colorScheme.primary,
                                        width = 5f
                                    )
                                    Marker(
                                        state = MarkerState(position = latLngList.firstOrNull() ?: LatLng(0.0, 0.0)),
                                        title = "Start"
                                    )
                                    Marker(
                                        state = MarkerState(position = latLngList.lastOrNull() ?: LatLng(0.0, 0.0)),
                                        title = "End"
                                    )
                                }
                            }
                        }
                    }

                    item {
                        WorkoutStatsSection(workoutLog)
                    }

                    workoutLog.distance?.let { distance ->
                        if (distance > 0) {
                            item {
                                SplitsSection(workoutLog)
                            }
                        }
                    }

                    workoutLog.heartRateAverage?.let {
                        item {
                            HeartRateZonesSection(workoutLog)
                        }
                    }

                    item {
                        AchievementsSection(workoutLog)
                    }

                    item {
                        Button(
                            onClick = {
                                val shareText = """
                                    🏃 FitGlide Workout Summary 🏃
                                    Type: ${workoutLog.type ?: "Workout"}
                                    Distance: ${workoutLog.distance?.let { "$it km" } ?: "N/A"}
                                    Calories: ${workoutLog.calories?.toInt()?.let { "$it kcal" } ?: "N/A"}
                                    Duration: ${workoutLog.totalTime?.let { "$it hours" } ?: "N/A"}
                                    Avg HR: ${workoutLog.heartRateAverage?.let { "$it bpm" } ?: "N/A"}
                                    #FitGlide #Fitness
                                """.trimIndent()
                                ShareCompat.IntentBuilder(context)
                                    .setType("text/plain")
                                    .setText(shareText)
                                    .createChooserIntent()
                                    .let { context.startActivity(it) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Share Workout", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No workout data available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutStatsSection(workoutLog: StrapiApi.WorkoutLogEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Workout Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            WorkoutDetailCard("Type", workoutLog.type ?: "Unknown")
            WorkoutDetailCard("Calories Burned", workoutLog.calories?.toInt()?.let { "$it kcal" } ?: "N/A")
            WorkoutDetailCard("Average Heart Rate", workoutLog.heartRateAverage?.let { "$it bpm" } ?: "N/A")
            WorkoutDetailCard("Max Heart Rate", workoutLog.heartRateMaximum?.let { "$it bpm" } ?: "N/A")
            WorkoutDetailCard("Min Heart Rate", workoutLog.heartRateMinimum?.let { "$it bpm" } ?: "N/A")
            WorkoutDetailCard("Distance", workoutLog.distance?.let { "$it km" } ?: "N/A")
            WorkoutDetailCard(
                "Duration",
                workoutLog.totalTime?.let { time ->
                    val hours = (time * 3600).toLong()
                    String.format("%02d:%02d:%02d", hours / 3600, (hours % 3600) / 60, hours % 60)
                } ?: try {
                    val start = LocalDateTime.parse(workoutLog.startTime)
                    val end = LocalDateTime.parse(workoutLog.endTime)
                    val seconds = ChronoUnit.SECONDS.between(start, end)
                    String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
                } catch (e: Exception) {
                    "N/A"
                }
            )
            WorkoutDetailCard("Pace", calculatePace(workoutLog))
            WorkoutDetailCard("Notes", workoutLog.notes ?: "No notes available")
        }
    }
}

@Composable
fun SplitsSection(workoutLog: StrapiApi.WorkoutLogEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Splits (Per Kilometer)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val distance = workoutLog.distance ?: 0f
                val totalTime = workoutLog.totalTime?.let { it * 3600 } ?: try {
                    val start = LocalDateTime.parse(workoutLog.startTime)
                    val end = LocalDateTime.parse(workoutLog.endTime)
                    ChronoUnit.SECONDS.between(start, end).toFloat()
                } catch (e: Exception) {
                    0f
                }
                if (distance > 0 && totalTime > 0) {
                    val kmCount = distance.toInt() + if (distance % 1 > 0) 1 else 0
                    items(kmCount) { index ->
                        val km = index + 1
                        val splitTime = totalTime / distance
                        val minutes = (splitTime / 60).toInt()
                        val seconds = (splitTime % 60).toInt()
                        Card(
                            modifier = Modifier
                                .width(120.dp)
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "KM $km",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format("%d:%02d min/km", minutes, seconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "No split data available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeartRateZonesSection(workoutLog: StrapiApi.WorkoutLogEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Heart Rate Zones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            val avgHr = workoutLog.heartRateAverage?.toFloat() ?: 0f
            val maxHr = workoutLog.heartRateMaximum?.toFloat() ?: 0f
            val minHr = workoutLog.heartRateMinimum?.toFloat() ?: 0f
            if (avgHr > 0) {
                WorkoutDetailCard(
                    "Low Zone (50-70%)",
                    if (minHr > 0) "${minHr.toInt()} - ${(maxHr * 0.7f).toInt()} bpm" else "N/A"
                )
                WorkoutDetailCard(
                    "Moderate Zone (70-85%)",
                    if (avgHr > 0) "${(maxHr * 0.7f).toInt()} - ${(maxHr * 0.85f).toInt()} bpm" else "N/A"
                )
                WorkoutDetailCard(
                    "High Zone (85-100%)",
                    if (maxHr > 0) "${(maxHr * 0.85f).toInt()} - ${maxHr.toInt()} bpm" else "N/A"
                )
            } else {
                Text(
                    text = "No heart rate data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AchievementsSection(workoutLog: StrapiApi.WorkoutLogEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val distance = workoutLog.distance ?: 0f
                val achievements = listOfNotNull(
                    if (distance >= 5) "5K Runner 🏃" else null,
                    if (distance >= 10) "10K Champion 🏅" else null,
                    if (workoutLog.calories?.toInt() ?: 0 >= 500) "Calorie Crusher 🔥" else null
                )
                if (achievements.isNotEmpty()) {
                    items(achievements) { badge ->
                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Celebration,
                                    contentDescription = "Badge",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "No achievements yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun parseRoute(route: List<Map<String, Float>>): List<LatLng> {
    return route.mapNotNull { point ->
        val lat = point["lat"]?.toDouble()
        val lng = point["lng"]?.toDouble()
        if (lat != null && lng != null) {
            LatLng(lat, lng)
        } else {
            null
        }
    }
}

@Composable
fun WorkoutDetailCard(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun calculatePace(workoutLog: StrapiApi.WorkoutLogEntry): String {
    val distance = workoutLog.distance ?: return "N/A"
    val totalTime = workoutLog.totalTime?.let { it * 3600 } ?: try {
        val start = LocalDateTime.parse(workoutLog.startTime)
        val end = LocalDateTime.parse(workoutLog.endTime)
        ChronoUnit.SECONDS.between(start, end).toFloat()
    } catch (e: Exception) {
        return "N/A"
    }
    if (distance <= 0 || totalTime <= 0) return "N/A"
    val pace = totalTime / distance / 60
    val minutes = pace.toInt()
    val seconds = ((pace - minutes) * 60).toInt()
    return String.format("%d:%02d min/km", minutes, seconds)
}