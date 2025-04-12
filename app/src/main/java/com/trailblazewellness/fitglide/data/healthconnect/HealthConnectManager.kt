package com.trailblazewellness.fitglide.data.healthconnect

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass

const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.O_MR1

enum class HealthConnectAvailability {
    INSTALLED,
    NOT_INSTALLED,
    NOT_SUPPORTED
}

class HealthConnectManager(private val context: Context) {
    var healthConnectClient: HealthConnectClient? = null
        private set

    var availability = mutableStateOf(HealthConnectAvailability.NOT_SUPPORTED)
        private set

    var isClientReady = mutableStateOf(false)
        private set

    private var isClientInitialized = false

    init {
        checkAvailability()
    }

    private fun checkAvailability() {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                if (!isClientInitialized) {
                    healthConnectClient = HealthConnectClient.getOrCreate(context)
                    isClientInitialized = true
                }
                availability.value = HealthConnectAvailability.INSTALLED
                isClientReady.value = true
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                availability.value = HealthConnectAvailability.NOT_INSTALLED
            }
            HealthConnectClient.SDK_UNAVAILABLE -> {
                availability.value = if (Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK) HealthConnectAvailability.NOT_INSTALLED else HealthConnectAvailability.NOT_SUPPORTED
            }
        }
    }

    private fun isClientInitialized(): Boolean {
        return healthConnectClient != null
    }

    suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
        return try {
            if (!isClientInitialized()) return false
            val granted = healthConnectClient!!.permissionController.getGrantedPermissions()
            Log.d("HealthConnectManager", "Granted permissions: $granted")
            granted.containsAll(permissions)
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error checking permissions: ${e.message}", e)
            false
        }
    }

    suspend fun <T : Record> readRecords(
        recordType: KClass<T>,
        startTime: Instant,
        endTime: Instant
    ): List<T> {
        return try {
            if (!isClientInitialized()) return emptyList()
            val request = ReadRecordsRequest(
                recordType = recordType,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient!!.readRecords(request)
            Log.d("HealthConnectManager", "Read ${recordType.simpleName}: ${response.records.size} records")
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading ${recordType.simpleName}: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun readSteps(date: LocalDate): Long {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.atTime(23, 59, 59, 999999999).atZone(ZoneId.systemDefault()).toInstant()
        val stepsRecords = readRecords(StepsRecord::class, start, end)
        return stepsRecords.sumOf { it.count }
    }

    suspend fun readNutritionRecords(startTime: Instant, endTime: Instant): List<NutritionRecord> {
        return readRecords(NutritionRecord::class, startTime, endTime)
    }

    suspend fun readSleepSessions(date: LocalDate): SleepData {
        val windowStart = date.atStartOfDay().minusHours(6).atZone(ZoneId.systemDefault()).toInstant()
        val windowEnd = date.plusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()

        val sessions = readRecords(SleepSessionRecord::class, windowStart, windowEnd)

        val relevantSessions = sessions.filter {
            val endLocal = LocalDateTime.ofInstant(it.endTime, ZoneId.systemDefault())
            endLocal.toLocalDate() == date.plusDays(1) && endLocal.hour <= 12
        }

        if (relevantSessions.isEmpty()) {
            return SleepData(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, LocalDateTime.now(), LocalDateTime.now())
        }

        val primarySession = relevantSessions.maxByOrNull {
            Duration.between(it.startTime, it.endTime)
        } ?: relevantSessions.first()

        val total = Duration.between(primarySession.startTime, primarySession.endTime)
        val deep = total.dividedBy(4)
        val rem = total.dividedBy(5)
        val light = total.dividedBy(3)
        val awake = total.dividedBy(10)

        return SleepData(
            total = total,
            deep = deep,
            rem = rem,
            light = light,
            awake = awake,
            start = LocalDateTime.ofInstant(primarySession.startTime, ZoneId.systemDefault()),
            end = LocalDateTime.ofInstant(primarySession.endTime, ZoneId.systemDefault())
        ).also {
            Log.d("HealthConnectManager", "Sleep for night of $date: total=${it.total}, start=${it.start}, end=${it.end}")
        }
    }

    suspend fun readExerciseSessions(date: LocalDate): WorkoutData {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val exerciseSessions = readRecords(ExerciseSessionRecord::class, start, end)
            .filter { LocalDateTime.ofInstant(it.startTime, ZoneId.systemDefault()).toLocalDate() == date }
        val distanceRecords = readRecords(DistanceRecord::class, start, end)
        val caloriesRecords = readRecords(TotalCaloriesBurnedRecord::class, start, end)
        val heartRateRecords = readRecords(HeartRateRecord::class, start, end)

        if (exerciseSessions.isEmpty()) {
            return WorkoutData(null, null, null, null, null, null, "Running")
        }

        val duration = exerciseSessions.fold(Duration.ZERO) { acc, s -> acc.plus(Duration.between(s.startTime, s.endTime)) }
        val distance = distanceRecords.sumOf { it.distance.inMeters }
        val calories = caloriesRecords.sumOf { it.energy.inKilocalories }
        val heartRates = heartRateRecords.flatMap { it.samples }.map { it.beatsPerMinute }
        val heartRateAvg = if (heartRates.isNotEmpty()) heartRates.average().toLong() else null

        return WorkoutData(
            distance = distance.takeIf { it > 0.0 },
            duration = duration,
            calories = calories.takeIf { it > 0.0 },
            heartRateAvg = heartRateAvg,
            start = LocalDateTime.ofInstant(exerciseSessions.first().startTime, ZoneId.systemDefault()),
            end = LocalDateTime.ofInstant(exerciseSessions.last().endTime, ZoneId.systemDefault()),
            type = exerciseSessions.first().exerciseType.toString()
        ).also {
            Log.d("HealthConnectManager", "Exercise data: $calories kcal, HR=$heartRateAvg")
        }
    }

    suspend fun readWeightRecords(startTime: Instant, endTime: Instant): List<WeightRecord> {
        return readRecords(WeightRecord::class, startTime, endTime)
    }

    suspend fun readHydrationRecords(startTime: Instant, endTime: Instant): List<HydrationRecord> {
        return readRecords(HydrationRecord::class, startTime, endTime)
    }

    suspend fun logHydration(date: LocalDate, volumeLiters: Double) {
        if (!isClientInitialized()) return
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.now())
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = start.plusSeconds(1)
        val existingRecords = readHydrationRecords(start, end)
        val metadata = existingRecords.firstOrNull()?.metadata ?: throw IllegalStateException("No metadata available")
        val record = HydrationRecord(
            startTime = start,
            startZoneOffset = zoneOffset,
            endTime = end,
            endZoneOffset = zoneOffset,
            volume = Volume.liters(volumeLiters),
            metadata = metadata
        )
        healthConnectClient!!.insertRecords(listOf(record))
        Log.d("HealthConnectManager", "Logged $volumeLiters L hydration")
    }

    suspend fun readDailyHeartRate(date: LocalDate): Long? {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()
        val hrRecords = readRecords(HeartRateRecord::class, start, end)
        val samples = hrRecords.flatMap { it.samples }
        return if (samples.isNotEmpty()) samples.map { it.beatsPerMinute }.average().toLong() else null
    }

    suspend fun isTracking(): Boolean {
        // Placeholder—will be updated later for live activity detection
        return false
    }
}

data class SleepData(
    val total: Duration,
    val deep: Duration,
    val rem: Duration,
    val light: Duration,
    val awake: Duration,
    val start: LocalDateTime,
    val end: LocalDateTime
)

data class WorkoutData(
    val distance: Double?,
    val duration: Duration?,
    val calories: Double?,
    val heartRateAvg: Long?,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val type: String
)