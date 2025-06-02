package com.trailblazewellness.fitglide.presentation.sleep

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.data.healthconnect.SleepData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

data class SleepDataUi(
    val score: Float,
    val debt: String,
    val injuryRisk: Float,
    val bedtime: String,
    val alarm: String,
    val stages: List<SleepStage>,
    val insights: List<String>,
    val streak: Int,
    val challengeActive: Boolean,
    val restTime: Float,
    val actualSleepTime: Float,
    val scoreLegend: SleepScoreLegend
)

data class SleepStage(val duration: Int, val type: String)

data class SleepScoreLegend(
    val overallScoreDescription: String,
    val scoreRanges: List<String>,
    val deepSleepContribution: String,
    val remSleepContribution: String,
    val awakeTimeImpact: String,
    val consistencyImpact: String
)

class SleepViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val strapiRepository: StrapiRepository,
    private val authRepository: AuthRepository,
    private val context: Context
) : ViewModel() {
    private val _sleepData = MutableStateFlow<SleepDataUi?>(null)
    val sleepData: StateFlow<SleepDataUi?> = _sleepData.asStateFlow()

    init {
        fetchSleepData(LocalDate.now())
    }

    fun fetchSleepData(date: LocalDate) {
        viewModelScope.launch {
            try {
                // Try fetching data from Strapi first
                Log.d("SleepViewModel", "Fetching sleep data from Strapi for $date")
                var sleepData: SleepData? = null
                try {
                    val sleepLogResponse = strapiRepository.fetchSleepLog(date)
                    if (sleepLogResponse.isSuccessful && sleepLogResponse.body()?.data?.isNotEmpty() == true) {
                        val sleepLog = sleepLogResponse.body()!!.data.first()
                        if (sleepLog.sleepDuration > 0.0) {
                            sleepData = strapiLogToSleepData(sleepLog)
                            Log.d("SleepViewModel", "Fetched sleep data from Strapi for $date: $sleepData")
                        } else {
                            Log.w("SleepViewModel", "Strapi log has zero duration for $date, skipping")
                        }
                    }
                } catch (e: UnknownHostException) {
                    Log.w("SleepViewModel", "Strapi unavailable, falling back to Health Connect: ${e.message}")
                }

                // Fallback to Health Connect if Strapi fails or returns no valid data
                if (sleepData == null) {
                    Log.d("SleepViewModel", "No valid sleep data in Strapi for $date, falling back to Health Connect")
                    sleepData = healthConnectManager.readSleepSessions(date)
                }

                // If no data from either source, reset UI
                if (sleepData == null || sleepData.total <= Duration.ZERO) {
                    Log.d("SleepViewModel", "No valid sleep data for $date from Strapi or Health Connect")
                    _sleepData.value = SleepDataUi(
                        score = 0f,
                        debt = "0h0m",
                        injuryRisk = 0f,
                        bedtime = "N/A",
                        alarm = "N/A",
                        stages = emptyList(),
                        insights = listOf("No sleep recorded for this date"),
                        streak = 0,
                        challengeActive = false,
                        restTime = 0f,
                        actualSleepTime = 0f,
                        scoreLegend = SleepScoreLegend(
                            overallScoreDescription = "Poor: No sleep data",
                            scoreRanges = listOf("90-100: Excellent", "70-89: Good", "50-69: Fair", "0-49: Poor"),
                            deepSleepContribution = "0% of score: No deep sleep data",
                            remSleepContribution = "0% of score: No REM sleep data",
                            awakeTimeImpact = "No penalty: No awake time data",
                            consistencyImpact = "0% of score: No consistency data"
                        )
                    )
                    return@launch
                }

                // Process sleep data for UI
                val workoutSessions = healthConnectManager.readExerciseSessions(date)
                val workoutIntensity = workoutSessions.sumOf { it.calories ?: 0.0 } // Sum calories from all sessions

                // Calculate recommended sleep based on age and workout
                val recommendedSleepHours = calculateRecommendedSleep(date, workoutIntensity) ?: run {
                    Log.w("SleepViewModel", "No age data available for recommended sleep")
                    _sleepData.value = SleepDataUi(
                        score = 0f,
                        debt = "0h0m",
                        injuryRisk = 0f,
                        bedtime = "N/A",
                        alarm = "N/A",
                        stages = emptyList(),
                        insights = listOf("Data not available, please update your date of birth on the profile page"),
                        streak = 0,
                        challengeActive = false,
                        restTime = 0f,
                        actualSleepTime = 0f,
                        scoreLegend = SleepScoreLegend(
                            overallScoreDescription = "Poor: No age data",
                            scoreRanges = listOf("90-100: Excellent", "70-89: Good", "50-69: Fair", "0-49: Poor"),
                            deepSleepContribution = "0% of score: No deep sleep data",
                            remSleepContribution = "0% of score: No REM sleep data",
                            awakeTimeImpact = "No penalty: No awake time data",
                            consistencyImpact = "0% of score: No consistency data"
                        )
                    )
                    return@launch
                }

                val stages = listOf(
                    SleepStage(sleepData.light.toMinutes().toInt(), "Light"),
                    SleepStage(sleepData.deep.toMinutes().toInt(), "Deep"),
                    SleepStage(sleepData.rem.toMinutes().toInt(), "REM")
                )
                val totalSleepMinutes = sleepData.total.toMinutes().toFloat()
                val actualSleepHours = totalSleepMinutes / 60f
                val debtMinutes = ((recommendedSleepHours * 60) - totalSleepMinutes).toInt().coerceAtLeast(0)
                val debt = formatDebt(debtMinutes)

                // Fetch alarm time from Android system
                val alarmTime = getNextAlarmTime() ?: sleepData.end
                // Calculate bedtime based on recommended sleep
                val bedtime = calculateBedtime(alarmTime, recommendedSleepHours)

                val formatter = DateTimeFormatter.ofPattern("h:mm a")
                val bedtimeFormatted = bedtime.format(formatter).uppercase()
                val alarmFormatted = alarmTime.format(formatter).uppercase()

                val insights = mutableListOf<String>()
                if (debtMinutes > 0) insights.add("Nap ${debtMinutes / 60}h${debtMinutes % 60}m to cut debt")
                if (workoutIntensity > 500.0) insights.add("Shift bedtime earlier for recovery")

                // Calculate comprehensive sleep score and legend
                val (score, legend) = calculateComprehensiveSleepScore(sleepData, recommendedSleepHours, date)

                _sleepData.value = SleepDataUi(
                    score = score,
                    debt = debt,
                    injuryRisk = if (debtMinutes > 120) 25f else 0f,
                    bedtime = bedtimeFormatted,
                    alarm = alarmFormatted,
                    stages = stages,
                    insights = insights,
                    streak = calculateStreak(date),
                    challengeActive = debtMinutes <= 0,
                    restTime = recommendedSleepHours,
                    actualSleepTime = actualSleepHours,
                    scoreLegend = legend
                )
                Log.d("SleepViewModel", "Updated UI with sleep data: ${_sleepData.value}")

                // Sync Health Connect data to Strapi in the background
                val healthConnectData = healthConnectManager.readSleepSessions(date)
                if (healthConnectData != null && healthConnectData.total > Duration.ZERO) {
                    Log.d("SleepViewModel", "Syncing Health Connect data to Strapi for $date")
                    strapiRepository.syncSleepLog(date, healthConnectData)
                } else {
                    Log.d("SleepViewModel", "No valid Health Connect data to sync for $date")
                }
            } catch (e: Exception) {
                Log.e("SleepViewModel", "Error fetching sleep data: ${e.message}", e)
                _sleepData.value = SleepDataUi(
                    score = 0f,
                    debt = "0h0m",
                    injuryRisk = 0f,
                    bedtime = "N/A",
                    alarm = "N/A",
                    stages = emptyList(),
                    insights = listOf("Error fetching sleep data"),
                    streak = 0,
                    challengeActive = false,
                    restTime = 0f,
                    actualSleepTime = 0f,
                    scoreLegend = SleepScoreLegend(
                        overallScoreDescription = "Poor: Error fetching data",
                        scoreRanges = listOf("90-100: Excellent", "70-89: Good", "50-69: Fair", "0-49: Poor"),
                        deepSleepContribution = "0% of score: No deep sleep data",
                        remSleepContribution = "0% of score: No REM sleep data",
                        awakeTimeImpact = "No penalty: No awake time data",
                        consistencyImpact = "0% of score: No consistency data"
                    )
                )
            }
        }
    }

    private fun strapiLogToSleepData(log: StrapiApi.SleepLogEntry): SleepData? {
        return try {
            val totalSeconds = (log.sleepDuration * 3600).toLong()
            val total = Duration.ofSeconds(totalSeconds)
            if (total <= Duration.ZERO) {
                Log.w("SleepViewModel", "Invalid total duration in Strapi log: $total")
                return null
            }
            val startTime = log.startTime
            val endTime = log.endTime
            if (startTime == null || endTime == null) {
                Log.w("SleepViewModel", "Missing startTime or endTime in Strapi log for documentId: ${log.documentId}")
                return null
            }
            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val start = ZonedDateTime.parse(startTime, formatter).toLocalDateTime()
            val end = ZonedDateTime.parse(endTime, formatter).toLocalDateTime()
            SleepData(
                total = total,
                deep = Duration.ofSeconds((log.deepSleepDuration * 3600).toLong()),
                rem = Duration.ofSeconds((log.remSleepDuration * 3600).toLong()),
                light = Duration.ofSeconds((log.lightSleepDuration * 3600).toLong()),
                awake = Duration.ofSeconds((log.sleepAwakeDuration * 3600).toLong()),
                start = start,
                end = end
            )
        } catch (e: Exception) {
            Log.e("SleepViewModel", "Error converting Strapi log to SleepData: ${e.message}", e)
            return null
        }
    }

    @SuppressLint("Range")
    private fun getNextAlarmTime(): LocalDateTime? {
        try {
            val uri = Uri.parse("content://com.android.deskclock/alarms")
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val hour = it.getInt(it.getColumnIndex("hour"))
                    val minutes = it.getInt(it.getColumnIndex("minutes"))
                    val now = LocalDateTime.now()
                    var alarmTime = now.withHour(hour).withMinute(minutes).withSecond(0).withNano(0)
                    if (alarmTime.isBefore(now)) {
                        alarmTime = alarmTime.plusDays(1)
                    }
                    return alarmTime
                }
            }
            Log.w("SleepViewModel", "No alarms found in DeskClock provider, using default 8 AM")
            return LocalDateTime.now().plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
        } catch (e: Exception) {
            Log.e("SleepViewModel", "Error fetching alarm time: ${e.message}, using default 8 AM")
            return LocalDateTime.now().plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
        }
    }

    private fun setAlarm(alarmTime: LocalDateTime): Boolean {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w("SleepViewModel", "SCHEDULE_EXACT_ALARM permission not granted")
                    return false
                }
            }

            val intent = Intent("com.trailblazewellness.fitglide.ALARM")
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val epochMillis = alarmTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                epochMillis,
                pendingIntent
            )
            Log.d("SleepViewModel", "Alarm set for $alarmTime")
            return true
        } catch (e: Exception) {
            Log.e("SleepViewModel", "Error setting alarm: ${e.message}")
            return false
        }
    }

    private fun calculateBedtime(alarmTime: LocalDateTime, requiredSleepHours: Float): LocalDateTime {
        val sleepSeconds = (requiredSleepHours * 3600).toLong()
        return alarmTime.minusSeconds(sleepSeconds)
    }

    private suspend fun calculateRecommendedSleep(date: LocalDate, workoutIntensity: Double): Float? {
        val userId = authRepository.getAuthState().getId().toString()
        val token = "Bearer ${authRepository.getAuthState().jwt ?: return null}"
        var dateOfBirth: String? = null
        var attempts = 0
        val maxAttempts = 3

        Log.d("SleepViewModel", "Fetching health vitals with userId=$userId, token=$token")
        while (attempts < maxAttempts) {
            try {
                val response = strapiRepository.getHealthVitals(userId, token)
                if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                    dateOfBirth = response.body()!!.data.first().date_of_birth
                    Log.d("SleepViewModel", "Fetched date of birth: $dateOfBirth")
                    break
                } else {
                    Log.w("SleepViewModel", "getHealthVitals failed (attempt ${attempts + 1}): ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("SleepViewModel", "Error fetching health vitals (attempt ${attempts + 1}): ${e.message}")
            }
            attempts++
            if (attempts < maxAttempts) delay(2000)
        }

        if (dateOfBirth == null) {
            Log.w("SleepViewModel", "No date of birth available after $maxAttempts attempts")
            return null
        }

        val age = try {
            val dob = LocalDate.parse(dateOfBirth, DateTimeFormatter.ISO_LOCAL_DATE)
            Period.between(dob, date).years
        } catch (e: Exception) {
            Log.e("SleepViewModel", "Error parsing date of birth: ${e.message}")
            return null
        }

        val baseSleep = when {
            age < 18 -> 9f
            age <= 64 -> 8f
            else -> 7.5f
        }

        // Check workout logs from Strapi for more accurate workout detection
        val workoutAdjustment = try {
            val workoutResponse = strapiRepository.getWorkoutLogs(userId, date.toString(), token)
            if (workoutResponse.isSuccessful && workoutResponse.body()?.data?.isNotEmpty() == true) {
                Log.d("SleepViewModel", "Workout logs found for $date, using workoutIntensity=$workoutIntensity")
                when {
                    workoutIntensity > 1000 -> 1f
                    workoutIntensity > 500 -> 0.5f
                    else -> 0f
                }
            } else {
                Log.d("SleepViewModel", "No workout logs found for $date, workoutIntensity=$workoutIntensity")
                0f
            }
        } catch (e: Exception) {
            Log.e("SleepViewModel", "Error fetching workout logs: ${e.message}")
            0f
        }

        return baseSleep + workoutAdjustment
    }

    private suspend fun calculateComprehensiveSleepScore(
        sleepData: SleepData,
        targetHours: Float,
        date: LocalDate
    ): Pair<Float, SleepScoreLegend> {
        if (sleepData.total.toMinutes() <= 0) {
            Log.w("SleepViewModel", "Invalid total sleep duration: ${sleepData.total}")
            return Pair(0f, SleepScoreLegend(
                overallScoreDescription = "Poor: Invalid sleep data",
                scoreRanges = listOf("90-100: Excellent", "70-89: Good", "50-69: Fair", "0-49: Poor"),
                deepSleepContribution = "0% of score: No deep sleep data",
                remSleepContribution = "0% of score: No REM sleep data",
                awakeTimeImpact = "No penalty: No awake time data",
                consistencyImpact = "0% of score: No consistency data"
            ))
        }

        val totalSleepHours = sleepData.total.toMinutes() / 60f
        val baseScore = ((totalSleepHours / targetHours) * 50).coerceIn(0f, 50f)

        val deepPercentage = (sleepData.deep.toMinutes().toFloat() / sleepData.total.toMinutes()) * 100
        val deepScore = when {
            deepPercentage >= 20 -> 20f
            deepPercentage >= 15 -> 15f
            else -> 10f
        }

        val remPercentage = (sleepData.rem.toMinutes().toFloat() / sleepData.total.toMinutes()) * 100
        val remScore = when {
            remPercentage >= 20 -> 15f
            remPercentage >= 15 -> 10f
            else -> 5f
        }

        val awakePercentage = (sleepData.awake.toMinutes().toFloat() / sleepData.total.toMinutes()) * 100
        val awakePenalty = when {
            awakePercentage <= 10 -> 0f
            awakePercentage <= 20 -> -5f
            else -> -10f
        }

        val consistencyScore = calculateConsistencyScore(date)

        val totalScore = (baseScore + deepScore + remScore + consistencyScore + awakePenalty).coerceIn(0f, 100f)

        val overallDescription = when {
            totalScore >= 90 -> "Excellent: High deep sleep and consistency"
            totalScore >= 70 -> "Good: Balanced sleep with minor disruptions"
            totalScore >= 50 -> "Fair: Adequate sleep but needs improvement"
            else -> "Poor: Insufficient sleep quality or duration"
        }
        val legend = SleepScoreLegend(
            overallScoreDescription = overallDescription,
            scoreRanges = listOf("90-100: Excellent", "70-89: Good", "50-69: Fair", "0-49: Poor"),
            deepSleepContribution = "${(deepScore / 20 * 100).toInt()}% of score: ${if (deepPercentage >= 20) "Adequate" else "Needs more"} deep sleep",
            remSleepContribution = "${(remScore / 15 * 100).toInt()}% of score: ${if (remPercentage >= 20) "Adequate" else "Needs more"} REM sleep",
            awakeTimeImpact = "${if (awakePenalty == 0f) "No penalty" else "Penalty applied"}: ${awakePercentage.toInt()}% awake time",
            consistencyImpact = "${(consistencyScore / 15 * 100).toInt()}% of score: ${if (consistencyScore >= 10) "Consistent" else "Variable"} bedtime"
        )

        return Pair(totalScore, legend)
    }

    private suspend fun calculateConsistencyScore(date: LocalDate): Float {
        try {
            val bedtimes = mutableListOf<Float>()
            for (i in 0 until 7) {
                val pastDate = date.minusDays(i.toLong())
                val response = strapiRepository.fetchSleepLog(pastDate)
                if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                    val log = response.body()!!.data.first()
                    if (log.sleepDuration > 0.0) {
                        log.startTime?.let { startTime ->
                            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            val start = ZonedDateTime.parse(startTime, formatter).toLocalDateTime()
                            val minutes = start.hour * 60 + start.minute
                            bedtimes.add(minutes.toFloat())
                        }
                    }
                }
            }

            if (bedtimes.size < 2) {
                Log.d("SleepViewModel", "Insufficient bedtime data for consistency score")
                return 5f
            }

            val mean = bedtimes.average().toFloat()
            val variance = bedtimes.map { (it - mean) * (it - mean) }.average().toFloat()
            val stdDev = sqrt(variance)

            return when {
                stdDev < 30 -> 15f
                stdDev < 60 -> 10f
                else -> 5f
            }
        } catch (e: Exception) {
            Log.e("SleepViewModel", "Error calculating consistency score: ${e.message}")
            return 5f
        }
    }

    private suspend fun calculateStreak(date: LocalDate): Int {
        try {
            var streak = 0
            var currentDate = date
            while (true) {
                val response = strapiRepository.fetchSleepLog(currentDate)
                if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                    val log = response.body()!!.data.first()
                    if (log.sleepDuration > 0.0) {
                        val recommendedHours = calculateRecommendedSleep(currentDate, 0.0) ?: 8f // Default to 8f if no age data
                        val actualHours = log.sleepDuration
                        if (actualHours >= recommendedHours * 0.9) {
                            streak++
                            currentDate = currentDate.minusDays(1)
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            return streak
        } catch (e: Exception) {
            Log.e("SleepViewModel", "Error calculating streak: ${e.message}")
            return 0
        }
    }

    fun updateSettings(syncEnabled: Boolean, sleepGoal: Float) {
        viewModelScope.launch {
            val current = _sleepData.value
            if (current != null) {
                val newRestTime = if (syncEnabled) current.restTime else sleepGoal
                _sleepData.value = current.copy(restTime = newRestTime)
            }

            if (syncEnabled) {
                val alarmTime = LocalDateTime.now().plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
                if (setAlarm(alarmTime)) {
                    Log.d("SleepViewModel", "Alarm synced successfully for $alarmTime")
                } else {
                    Log.w("SleepViewModel", "Failed to sync alarm, permission may be missing")
                }
            }

            fetchSleepData(LocalDate.now())
        }
    }

    private fun formatDebt(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h${m}m"
    }
}