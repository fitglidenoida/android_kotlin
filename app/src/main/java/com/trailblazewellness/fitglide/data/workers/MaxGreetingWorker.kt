package com.trailblazewellness.fitglide.data.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.max.MaxAiService
import com.trailblazewellness.fitglide.data.max.MaxPromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MaxGreetingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "MaxGreetingWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val appContext = applicationContext

            val authRepo = AuthRepository(context = appContext, googleAuthManager = com.trailblazewellness.fitglide.auth.GoogleAuthManager(appContext))
            val healthManager = HealthConnectManager(appContext)
            val today = LocalDate.now()

            val userName = authRepo.getAuthState().userName ?: "User"
            val steps = healthManager.readSteps(today).toFloat()
            val sleep = healthManager.readSleepSessions(today).total.toHours().toFloat()
            val hydrationRecords = healthManager.readHydrationRecords(
                today.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                today.plusDays(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant()
            )
            val hydration = hydrationRecords.sumOf { it.volume.inLiters }.toFloat()

            Log.d(TAG, "Gathered Stats: steps=$steps, sleep=$sleep, hydration=$hydration")

            val prompt = MaxPromptBuilder.getPrompt(
                userName = userName,
                steps = steps,
                sleep = sleep,
                hydration = hydration
            )

            val response = MaxAiService.fetchMaxGreeting(prompt)
            Log.d(TAG, "Max AI Response: $response")

            val prefs = appContext.getSharedPreferences("max_prefs", Context.MODE_PRIVATE)
            val lines = response.split("\n").filter { it.isNotBlank() }
            val yesterdayMsg = lines.getOrNull(0) ?: "You crushed it yesterday!"
            val todayMsg = lines.getOrNull(1) ?: "Let's win today too!"

            prefs.edit().apply {
                putString("max_yesterday", yesterdayMsg)
                putString("max_today", todayMsg)
                putBoolean("max_hasPlayed", false)
                apply()
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run MaxGreetingWorker", e)
            Result.retry()
        }
    }
}
