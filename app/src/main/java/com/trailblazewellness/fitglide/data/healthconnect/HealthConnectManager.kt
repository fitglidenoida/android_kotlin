package com.trailblazewellness.fitglide.data.healthconnect

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.records.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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

    private data class CacheEntry(val records: List<Record>, val timestamp: Long)
    private val recordCache = mutableMapOf<String, CacheEntry>()
    private var lastQuotaErrorTime: Long = 0
    private var apiCallCount: Int = 0
    private val cacheTTL = 3600000 // 1 hour in ms

    init {
        checkAvailability()
    }

    private fun checkAvailability() {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                if (!isClientInitialized) {
                    try {
                        healthConnectClient = HealthConnectClient.getOrCreate(context)
                        isClientInitialized = true
                        Log.d("HealthConnectManager", "Health Connect client initialized successfully")
                    } catch (e: Exception) {
                        Log.e("HealthConnectManager", "Failed to initialize Health Connect client: ${e.message}", e)
                    }
                }
                availability.value = HealthConnectAvailability.INSTALLED
                isClientReady.value = true
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                availability.value = HealthConnectAvailability.NOT_INSTALLED
                Log.w("HealthConnectManager", "Health Connect requires provider update")
            }
            HealthConnectClient.SDK_UNAVAILABLE -> {
                availability.value = if (Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK) HealthConnectAvailability.NOT_INSTALLED else HealthConnectAvailability.NOT_SUPPORTED
                Log.w("HealthConnectManager", "Health Connect not supported on this device")
            }
        }
    }

    private fun isClientInitialized(): Boolean {
        return healthConnectClient != null
    }

    suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
        return try {
            if (!isClientInitialized()) {
                Log.w("HealthConnectManager", "Client not initialized for permission check")
                return false
            }
            val granted = healthConnectClient!!.permissionController.getGrantedPermissions()
            Log.d("HealthConnectManager", "Granted permissions: $granted")
            granted.containsAll(permissions)
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error checking permissions: ${e.message}", e)
            false
        }
    }

    suspend fun checkWorkoutPermissions(): Boolean {
        val requiredPermissions = setOf(
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_TOTAL_CALORIES_BURNED",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_HYDRATION" // Added
        )
        val hasPerms = hasAllPermissions(requiredPermissions)
        Log.d("HealthConnectManager", "Workout permissions granted: $hasPerms, required: $requiredPermissions")
        return hasPerms
    }

    suspend fun <T : Record> readRecords(
        recordType: KClass<T>,
        startTime: Instant,
        endTime: Instant,
        retryCount: Int = 3,
        initialDelay: Long = 2000,
        maxDelay: Long = 7200000 // 2 hours
    ): List<T> {
        val cacheKey = "${recordType.simpleName}_${startTime}_${endTime}"
        val currentTime = System.currentTimeMillis()

        // Check cache
        recordCache[cacheKey]?.let { entry ->
            if (currentTime - entry.timestamp < cacheTTL) {
                Log.d("HealthConnectManager", "Returning cached ${recordType.simpleName}: ${entry.records.size} records")
                @Suppress("UNCHECKED_CAST")
                return entry.records as List<T>
            } else {
                recordCache.remove(cacheKey)
            }
        }

        // Skip if recent quota error
        if (currentTime - lastQuotaErrorTime < maxDelay) {
            Log.w("HealthConnectManager", "Skipping read for ${recordType.simpleName}; recent quota error")
            return emptyList()
        }

        // Check permissions
        val permission = when (recordType) {
            HydrationRecord::class -> "android.permission.health.READ_HYDRATION"
            else -> "android.permission.health.READ_${recordType.simpleName?.uppercase()}"
        }
        if (!hasAllPermissions(setOf(permission))) {
            Log.w("HealthConnectManager", "Missing permission $permission for ${recordType.simpleName}")
            return emptyList()
        }

        var attempt = 0
        while (attempt < retryCount) {
            try {
                if (!isClientInitialized()) {
                    Log.w("HealthConnectManager", "Client not initialized for reading ${recordType.simpleName}")
                    return emptyList()
                }
                val request = ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
                apiCallCount++
                Log.d("HealthConnectManager", "API call count: $apiCallCount for ${recordType.simpleName}")
                val response = healthConnectClient!!.readRecords(request)
                Log.d("HealthConnectManager", "Read ${recordType.simpleName}: ${response.records.size} records")
                recordCache[cacheKey] = CacheEntry(response.records, currentTime)
                return response.records
            } catch (e: Exception) {
                Log.e("HealthConnectManager", "Error reading ${recordType.simpleName}: ${e.message}", e)
                if (e.message?.contains("API call quota exceeded") == true) {
                    lastQuotaErrorTime = currentTime
                    Log.w("HealthConnectManager", "Quota exceeded, attempt ${attempt + 1}/$retryCount")
                    if (attempt < retryCount - 1) {
                        val delay = initialDelay * (1 shl attempt)
                        Log.d("HealthConnectManager", "Waiting $delay ms before retry")
                        delay(delay)
                    }
                    attempt++
                } else {
                    return emptyList()
                }
            }
        }
        Log.w("HealthConnectManager", "Failed to read ${recordType.simpleName} after $retryCount attempts")
        return emptyList()
    }

    suspend fun readSteps(date: LocalDate): Long {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.atTime(23, 59, 59, 999999999).atZone(ZoneId.systemDefault()).toInstant()
        val stepsRecords = readRecords(StepsRecord::class, start, end)
        val totalSteps = stepsRecords.sumOf { it.count }
        Log.d("HealthConnectManager", "Steps for $date: $totalSteps")
        return totalSteps
    }

    suspend fun readNutritionRecords(startTime: Instant, endTime: Instant): List<NutritionRecord> {
        return readRecords(NutritionRecord::class, startTime, endTime)
    }

    suspend fun readSleepSessions(date: LocalDate): SleepData {
        val windowStart = date.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
        val windowEnd = date.atTime(23, 59, 59, 999999999).atZone(ZoneId.systemDefault()).toInstant()

        val sessions = readRecords(SleepSessionRecord::class, windowStart, windowEnd)

        if (sessions.isEmpty()) {
            Log.d("HealthConnectManager", "No sleep sessions for $date")
            return SleepData(
                total = Duration.ZERO,
                deep = Duration.ZERO,
                rem = Duration.ZERO,
                light = Duration.ZERO,
                awake = Duration.ZERO,
                start = LocalDateTime.of(date, java.time.LocalTime.of(22, 0)),
                end = LocalDateTime.of(date.plusDays(1), java.time.LocalTime.of(6, 0))
            )
        }

        val primarySession = sessions.maxByOrNull {
            Duration.between(it.startTime, it.endTime)
        } ?: sessions.first()

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
        if (!checkWorkoutPermissions()) {
            Log.w("HealthConnectManager", "Missing workout permissions for $date")
            return WorkoutData(null, null, null, null, null, null, "Unknown")
        }

        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val exerciseSessions = readRecords(ExerciseSessionRecord::class, start, end)
            .filter { LocalDateTime.ofInstant(it.startTime, ZoneId.systemDefault()).toLocalDate() == date }

        Log.d("HealthConnectManager", "Exercise sessions for $date: ${exerciseSessions.size} found, types=${exerciseSessions.map { it.exerciseType }}")
        exerciseSessions.forEach {
            Log.d("HealthConnectManager", "Session: type=${it.exerciseType}, start=${it.startTime}, end=${it.endTime}, title=${it.title}, dataOrigin=${it.metadata.dataOrigin}")
        }

        if (exerciseSessions.isEmpty()) {
            Log.w("HealthConnectManager", "No exercise sessions for $date")
            return WorkoutData(null, null, null, null, null, null, "Unknown")
        }

        val distanceRecords = readRecords(DistanceRecord::class, start, end)
        val caloriesRecords = readRecords(TotalCaloriesBurnedRecord::class, start, end)
        val heartRateRecords = readRecords(HeartRateRecord::class, start, end)

        val primarySession = exerciseSessions.find { it.exerciseType == 6 } ?: exerciseSessions.maxByOrNull {
            Duration.between(it.startTime, it.endTime).toMinutes()
        } ?: exerciseSessions.first()

        val duration = exerciseSessions.fold(Duration.ZERO) { acc, s -> acc.plus(Duration.between(s.startTime, s.endTime)) }
        val distance = distanceRecords.filter {
            exerciseSessions.any { session ->
                it.startTime >= session.startTime && it.endTime <= session.endTime
            }
        }.sumOf { it.distance.inMeters }
        val calories = caloriesRecords.filter {
            exerciseSessions.any { session ->
                it.startTime >= session.startTime && it.endTime <= session.endTime
            }
        }.sumOf { it.energy.inKilocalories }
        val heartRates = heartRateRecords.filter {
            exerciseSessions.any { session ->
                it.startTime >= session.startTime && it.endTime <= session.endTime
            }
        }.flatMap { it.samples }.map { it.beatsPerMinute }
        val heartRateAvg = if (heartRates.isNotEmpty()) heartRates.average().toLong() else null
        val type = when (primarySession.exerciseType) {
            6 -> "Cycling"
            1 -> "Running"
            else -> "Cardio (Type ${primarySession.exerciseType})"
        }

        return WorkoutData(
            distance = distance.takeIf { it > 0.0 },
            duration = duration,
            calories = calories.takeIf { it > 0.0 },
            heartRateAvg = heartRateAvg,
            start = LocalDateTime.ofInstant(exerciseSessions.minByOrNull { it.startTime }?.startTime ?: start, ZoneId.systemDefault()),
            end = LocalDateTime.ofInstant(exerciseSessions.maxByOrNull { it.endTime }?.endTime ?: end, ZoneId.systemDefault()),
            type = type
        ).also {
            Log.d("HealthConnectManager", "Exercise data for $date: type=${it.type}, distance=${it.distance} m, calories=${it.calories} kcal, HR=${it.heartRateAvg}")
        }
    }

    suspend fun readWeightRecords(startTime: Instant, endTime: Instant): List<WeightRecord> {
        return readRecords(WeightRecord::class, startTime, endTime)
    }

    suspend fun readHydrationRecords(startTime: Instant, endTime: Instant): List<HydrationRecord> {
        return readRecords(HydrationRecord::class, startTime, endTime)
    }

    suspend fun logHydration(date: LocalDate, volumeLiters: Double) {
        if (!isClientInitialized()) {
            Log.w("HealthConnectManager", "Client not initialized for hydration logging")
            return
        }
        try {
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
            apiCallCount++
            Log.d("HealthConnectManager", "API call count: $apiCallCount for HydrationRecord insert")
            healthConnectClient!!.insertRecords(listOf(record))
            Log.d("HealthConnectManager", "Logged $volumeLiters L hydration for $date")
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error logging hydration: ${e.message}", e)
        }
    }

    suspend fun readDailyHeartRate(date: LocalDate): Long? {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()
        val hrRecords = readRecords(HeartRateRecord::class, start, end)
        val samples = hrRecords.flatMap { it.samples }
        val avg = if (samples.isNotEmpty()) samples.map { it.beatsPerMinute }.average().toLong() else null
        Log.d("HealthConnectManager", "Heart rate for $date: $avg BPM")
        return avg
    }

    suspend fun isTracking(): Boolean {
        Log.d("HealthConnectManager", "Checking tracking status - placeholder")
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