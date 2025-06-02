package com.trailblazewellness.fitglide.data.workers

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import com.trailblazewellness.fitglide.R
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class LiveCheerService : Service() {
    @Inject lateinit var healthConnect: HealthConnectManager
    @Inject lateinit var strapi: StrapiRepository
    @Inject lateinit var authRepository: AuthRepository

    companion object {
        private const val CHANNEL_ID = "live_cheers"
        private val REQUIRED_PERMISSIONS = arrayOf(
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_EXERCISE",
            "android.permission.POST_NOTIFICATIONS"
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = intent?.getStringExtra("userId") ?: "4"
        val packId = intent?.getStringExtra("packId") ?: "2"
        val workoutId = intent?.getStringExtra("workoutId") ?: ""
        val workoutType = intent?.getStringExtra("workoutType") ?: "Cardio"
        val token = "Bearer ${authRepository.getAuthState().jwt}"

        val hasPermissions = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions) {
            Log.e("LiveCheerService", "Missing permissions: ${REQUIRED_PERMISSIONS.joinToString()}")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FitGlide Live")
                .setContentText("You’re live!")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        )

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val now = Instant.now()
                val startTime = now.minusSeconds(60)

                // Fetch workout data
                val workoutData = try {
                    healthConnect.readRecords(ExerciseSessionRecord::class, startTime, now)
                        .lastOrNull()
                } catch (e: SecurityException) {
                    Log.e("LiveCheerService", "Workout read error: ${e.message}")
                    null
                }

                // Fetch steps
                val steps = try {
                    healthConnect.readRecords(StepsRecord::class, startTime, now)
                        .sumOf { it.count }
                } catch (e: SecurityException) {
                    Log.e("LiveCheerService", "Steps read error: ${e.message}")
                    0L
                }

                // Fetch heart rate
                val hr = try {
                    healthConnect.readRecords(HeartRateRecord::class, startTime, now)
                        .lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute
                } catch (e: SecurityException) {
                    Log.e("LiveCheerService", "HR read error: ${e.message}")
                    null
                }

                // Fetch workout details from Strapi (for sets/reps)
                var sets = 0
                var reps = 0
                if (workoutType == "Strength" && workoutId.isNotEmpty()) {
                    val workoutResponse = strapi.getWorkoutPlans(
                        userId,
                        token
                    )
                    if (workoutResponse.isSuccessful) {
                        val workout = workoutResponse.body()?.data?.find { it.workoutId == workoutId }
                        workout?.exercises?.forEach { exercise: StrapiApi.ExerciseEntry ->
                            sets += exercise.sets ?: 0
                            reps += exercise.reps ?: 0
                        }
                        Log.d("LiveCheerService", "Fetched workout $workoutId: sets=$sets, reps=$reps")
                    } else {
                        Log.e("LiveCheerService", "Failed to fetch workout: ${workoutResponse.errorBody()?.string()}")
                    }
                }

                // Build data map
                val data = mutableMapOf<String, String>()
                data["workoutId"] = workoutId
                data["steps"] = steps.toString()
                data["hr"] = (hr ?: 0).toString()
                data["type"] = workoutType
                workoutData?.let { session ->
                    val duration = (session.endTime.toEpochMilli() - session.startTime.toEpochMilli()) / 1000 / 60
                    data["duration"] = duration.toString()
                    if (workoutType == "Strength") {
                        data["sets"] = sets.toString()
                        data["reps"] = reps.toString()
                    } else if (workoutType in listOf("Cardio", "Running", "Cycling")) {
                        try {
                            val distanceRecords = healthConnect.readRecords(DistanceRecord::class, startTime, now)
                            val distance = distanceRecords.sumOf { it.distance.inMeters }
                            if (distance > 0) data["distance"] = distance.toString()
                        } catch (e: SecurityException) {
                            Log.e("LiveCheerService", "Distance read error: ${e.message}")
                        }
                    }
                }

                // Post live update
                val postResponse = strapi.postPost(
                    StrapiApi.PostRequest(
                        user = StrapiApi.UserId(userId),
                        pack = StrapiApi.UserId(packId),
                        type = "live",
                        data = data
                    ),
                    token
                )
                if (postResponse.isSuccessful) {
                    Log.d("LiveCheerService", "Post successful: ${postResponse.body()?.data}")
                } else {
                    Log.e("LiveCheerService", "Post failed: ${postResponse.errorBody()?.string()}")
                }

                // Fetch and notify cheers
                val cheersResponse = strapi.getCheers(userId, token)
                if (cheersResponse.isSuccessful) {
                    val cheers = cheersResponse.body()?.data ?: emptyList()
                    val friendsResponse = strapi.getFriends(mapOf("filters[sender][id][\$eq]" to userId), token)
                    if (friendsResponse.isSuccessful) {
                        val friends = friendsResponse.body()?.data ?: emptyList()
                        val validFriendIds = friends.filter { it.friendsStatus == "Accepted" }
                            .mapNotNull { it.receiver?.data?.id }
                        val friendCheers = cheers.filter { cheer ->
                            validFriendIds.contains(cheer.sender.id)
                        }
                        if (friendCheers.isNotEmpty()) {
                            notify("Friend Cheer: ${friendCheers.last().message}")
                        }
                    } else {
                        Log.e("LiveCheerService", "Failed to fetch friends: ${friendsResponse.errorBody()?.string()}")
                    }
                } else {
                    Log.e("LiveCheerService", "Failed to fetch cheers: ${cheersResponse.errorBody()?.string()}")
                }

                delay(5000)
            }
        }
        return START_STICKY
    }

    private fun notify(message: String) {
        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            this, "android.permission.POST_NOTIFICATIONS"
        ) == PackageManager.PERMISSION_GRANTED
        if (hasNotificationPermission) {
            Log.d("LiveCheerService", "Notifying: $message")
            NotificationManagerCompat.from(this).notify(
                2,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("FitGlide Cheer")
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()
            )
        } else {
            Log.w("LiveCheerService", "Notification permission missing")
        }
    }
}