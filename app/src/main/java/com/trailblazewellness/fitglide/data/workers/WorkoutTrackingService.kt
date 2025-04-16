package com.trailblazewellness.fitglide.data.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.trailblazewellness.fitglide.R
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutTrackingService : Service() {
    @Inject
    lateinit var healthConnect: HealthConnectManager

    @Inject
    lateinit var strapiRepository: StrapiRepository

    @Inject
    lateinit var commonViewModel: CommonViewModel

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var lastLocation: android.location.Location? = null
    private var totalDistance = 0f
    private var startTime: Instant? = null
    private var totalSteps = 0L
    private var monitoredSteps = 0L
    private var lastStepCount = 0L
    private var movementTime = 0L
    private var isTracking = false
    private var isServiceRunning = false
    private var initialStepCount: Long = -1L
    private var lastMovedTime: Long = 0L
    private var userId: String = "1"
    private var workoutType: String = "Walking"
    private var token: String = ""

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private lateinit var locationCallback: LocationCallback
    private var stepListener: SensorEventListener? = null

    companion object {
        private const val CHANNEL_ID = "workout_tracking"
        private const val NOTIFICATION_ID = 3
        private const val SYNC_INTERVAL_MS = 30_000L
        private const val MOVEMENT_THRESHOLD_MS = 5_000L
        private const val MOVEMENT_TIMEOUT_MS = 10_000L
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        Log.d("WorkoutTrackingService", "Service created")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent channel for workout tracking"
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdateDelayMillis(2000)
            .setWaitForAccurateLocation(true)
            .build()

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lastLocation = location
                Log.d("WorkoutTrackingService", "Initial GPS location: lat=${location.latitude}, lng=${location.longitude}")
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val newLocation = location
                    val distanceMoved = lastLocation?.distanceTo(newLocation) ?: 0f
                    if (distanceMoved > 0.5f) {
                        totalDistance += distanceMoved
                        lastLocation = newLocation
                        lastMovedTime = System.currentTimeMillis()
                        if (!isTracking) {
                            movementTime += 1000L
                            if (movementTime >= MOVEMENT_THRESHOLD_MS && monitoredSteps > 0) {
                                commonViewModel.updateTrackedSteps(monitoredSteps.toFloat())
                            }
                        } else {
                            totalSteps = monitoredSteps
                            commonViewModel.updateTrackedSteps(totalSteps.toFloat())
                        }
                    } else if (!isTracking) {
                        movementTime = 0L
                    }
                }
            }
        }

        stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val currentSteps = it.values[0].toLong()
                    if (initialStepCount == -1L) {
                        initialStepCount = currentSteps
                    } else {
                        monitoredSteps = currentSteps - initialStepCount
                        if (isTracking && (System.currentTimeMillis() - lastMovedTime) < MOVEMENT_TIMEOUT_MS) {
                            totalSteps = monitoredSteps
                            commonViewModel.updateTrackedSteps(totalSteps.toFloat())
                        } else if (!isTracking) {
                            commonViewModel.updateTrackedSteps(monitoredSteps.toFloat())
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            stepSensor?.let { sensorManager.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
            Log.d("WorkoutTrackingService", "Step sensor registered")
        } else {
            Log.w("WorkoutTrackingService", "ACTIVITY_RECOGNITION permission missing")
            commonViewModel.postUiMessage("Please grant activity recognition permission")
        }

        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdateDelayMillis(2000)
            .setWaitForAccurateLocation(true)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            Log.d("WorkoutTrackingService", "Location updates requested")
        } else {
            Log.w("WorkoutTrackingService", "ACCESS_FINE_LOCATION permission missing")
            commonViewModel.postUiMessage("Please grant location permission")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getStringExtra("userId") ?: "1"
        workoutType = intent?.getStringExtra("workoutType") ?: "Walking"
        token = intent?.getStringExtra("token") ?: ""
        val manualStart = intent?.getBooleanExtra("manualStart", false) ?: false

        Log.d("WorkoutTrackingService", "onStartCommand: userId=$userId, workoutType=$workoutType, manualStart=$manualStart, token=$token")

        if (!hasRequiredPermissions()) {
            Log.e("WorkoutTrackingService", "Missing required permissions")
            commonViewModel.postUiMessage("Missing permissions for tracking")
            stopSelf()
            return START_NOT_STICKY
        }

        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.e("WorkoutTrackingService", "Google Play Services unavailable: $resultCode")
            commonViewModel.postUiMessage("Google Play Services unavailable")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isServiceRunning) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FitGlide")
                .setContentText("Tracking your $workoutType workout")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            try {
                startForeground(NOTIFICATION_ID, notification)
                isServiceRunning = true
                Log.d("WorkoutTrackingService", "Foreground service started")
            } catch (e: Exception) {
                Log.e("WorkoutTrackingService", "Foreground start failed: ${e.message}", e)
                commonViewModel.postUiMessage("Failed to start tracking service")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (manualStart && !isTracking) {
            startTracking()
            startPeriodicSync()
        }

        return START_STICKY
    }

    private fun hasRequiredPermissions(): Boolean {
        val perms = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        Log.d("WorkoutTrackingService", "Permissions check: $perms")
        return perms
    }

    private fun startTracking() {
        isTracking = true
        startTime = Instant.now()
        totalDistance = 0f
        totalSteps = monitoredSteps
        lastStepCount = monitoredSteps
        Log.d("WorkoutTrackingService", "Tracking started with initial steps: $totalSteps")
        commonViewModel.updateTrackedSteps(totalSteps.toFloat())
    }

    private fun startPeriodicSync() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isTracking) {
                delay(SYNC_INTERVAL_MS)
                val stepsToSync = totalSteps
                val distanceMiles = totalDistance / 1609.34f // Meters to miles
                Log.d("WorkoutTrackingService", "Periodic sync: $stepsToSync steps, $distanceMiles miles")

                if (token.isNotBlank()) {
                    try {
                        val logRequest = StrapiApi.WorkoutLogRequest(
                            logId = "workout_${userId}_${System.currentTimeMillis()}",
                            workout = null,
                            startTime = startTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) } ?: LocalDateTime.now().toString(),
                            endTime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                            distance = distanceMiles,
                            totalTime = ((System.currentTimeMillis() - (startTime?.toEpochMilli() ?: System.currentTimeMillis())) / 60000f),
                            calories = (stepsToSync * 0.04f).toFloat(),
                            heartRateAverage = 0L,
                            heartRateMaximum = 0L,
                            heartRateMinimum = 0L,
                            route = emptyList(),
                            completed = false,
                            notes = "Auto-tracked $workoutType",
                            usersPermissionsUser = StrapiApi.UserId(userId)
                        )
                        val response = strapiRepository.syncWorkoutLog(logRequest, "Bearer $token")
                        if (response.isSuccessful) {
                            Log.d("WorkoutTrackingService", "Synced to Strapi: steps=$stepsToSync, distance=$distanceMiles")
                        } else {
                            Log.e("WorkoutTrackingService", "Strapi sync failed: ${response.code()} - ${response.errorBody()?.string()}")
                            commonViewModel.postUiMessage("Failed to sync workout data")
                        }
                    } catch (e: Exception) {
                        Log.e("WorkoutTrackingService", "Strapi sync error: ${e.message}", e)
                        commonViewModel.postUiMessage("Error syncing workout: ${e.message}")
                    }
                }
                commonViewModel.updateTrackedSteps(stepsToSync.toFloat())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stepListener?.let {
            sensorManager.unregisterListener(it)
            Log.d("WorkoutTrackingService", "Step sensor unregistered")
        }
        isServiceRunning = false
        isTracking = false
        commonViewModel.updateTrackedSteps(0f)
        Log.d("WorkoutTrackingService", "Service destroyed")
    }
}