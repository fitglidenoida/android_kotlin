package com.trailblazewellness.fitglide.data.workers

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
            "android.permission.POST_NOTIFICATIONS"
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = intent?.getIntExtra("userId", 4) ?: 4
        val packId = intent?.getIntExtra("packId", 2) ?: 2
        val token = "Bearer ${authRepository.getAuthState().jwt}" // Fetch token from AuthRepository

        val hasPermissions = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions) {
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
                val steps = try {
                    healthConnect.readRecords(StepsRecord::class, now.minusSeconds(5), now)
                        .sumOf { it.count }
                } catch (e: SecurityException) {
                    0L
                }
                val hr = try {
                    healthConnect.readRecords(HeartRateRecord::class, now.minusSeconds(5), now)
                        .lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute
                } catch (e: SecurityException) {
                    null
                }

                val postResponse = strapi.postPost(
                    StrapiApi.PostRequest(
                        user = StrapiApi.UserId(userId.toString()),
                        pack = StrapiApi.UserId(packId.toString()),
                        type = "live",
                        data = mapOf("steps" to steps, "hr" to (hr ?: 0))
                    ),
                    token
                )
                if (postResponse.isSuccessful) {
                    Log.d("LiveCheerService", "Post successful: ${postResponse.body()?.data}")
                } else {
                    Log.e("LiveCheerService", "Post failed: ${postResponse.errorBody()?.string()}")
                }

                val cheersResponse = strapi.getCheers(userId.toString(), token)
                if (cheersResponse.isSuccessful) {
                    val cheers = cheersResponse.body()?.data ?: emptyList()
                    val friendsResponse = strapi.getFriends(mapOf("filters[sender][id][\$eq]" to userId.toString()), token)
                    if (friendsResponse.isSuccessful) {
                        val friends = friendsResponse.body()?.data ?: emptyList()
                        val friendCheers = cheers.filter { cheer ->
                            friends.any { friend ->
                                friend.receiver?.data?.id == cheer.sender.id &&
                                        friend.friendsStatus == "Accepted"
                            }
                        }
                        if (friendCheers.isNotEmpty()) {
                            notify("Friend Cheer: ${friendCheers.last().message}")
                        }
                    }
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
            NotificationManagerCompat.from(this).notify(
                2,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("FitGlide Cheer")
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()
            )
        }
    }
}