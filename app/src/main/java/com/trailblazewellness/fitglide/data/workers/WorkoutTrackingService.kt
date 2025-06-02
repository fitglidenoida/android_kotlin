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
import com.trailblazewellness.fitglide.MainActivity
import com.trailblazewellness.fitglide.R
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.auth.GoogleAuthManager
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectManager
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WorkoutTrackingService : Service() {
    private var authRepository: AuthRepository? = null
    private var strapiRepository: StrapiRepository? = null
    private var healthConnect: HealthConnectManager? = null
    private var commonViewModel: CommonViewModel? = null

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var lastLocation: android.location.Location? = null
    private var initialStepCount: Long = -1L
    private var monitoredSteps: Long = 0L
    private var totalSteps: Long = 0L
    private var isTracking = false
    private var isServiceRunning = false
    private var userId: String = "1"
    private var workoutType: String = "Walking"
    private var token: String = ""
    private var lastMovedTime: Long = 0L
    private var lastStepTime: Long = 0L
    private var hasMovedRecently: Boolean = false

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private var locationCallback: LocationCallback? = null
    private var stepListener: SensorEventListener? = null
    private val sharedPreferences by lazy { getSharedPreferences("fitglide_prefs", Context.MODE_PRIVATE) }

    companion object {
        private const val CHANNEL_ID = "workout_tracking"
        private const val NOTIFICATION_ID = 3
        private const val MOVEMENT_THRESHOLD_MS = 15_000L // 15 seconds
        private const val DISTANCE_THRESHOLD_M = 0.5f // 0.5 meters
        private const val MIN_STEP_THRESHOLD = 2L // Minimum steps for fallback
        private const val SENSOR_TIMEOUT_MS = 5_000L // 5 seconds
        private const val GPS_TIMEOUT_MS = 5_000L // 5 seconds
        private const val FALLBACK_TIMEOUT_MS = 3_000L // 3 seconds for fallback
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WorkoutTrackingService", "onCreate: Initializing service")

        // Notification Channel Setup
        try {
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
                Log.d("WorkoutTrackingService", "Notification channel created: $CHANNEL_ID")
            }

            // Start foreground service
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FitGlide")
                .setContentText("Tracking your workout")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            isServiceRunning = true
            Log.d("WorkoutTrackingService", "Foreground service started with NOTIFICATION_ID=$NOTIFICATION_ID")
        } catch (e: Exception) {
            Log.e("WorkoutTrackingService", "Failed to start foreground service: ${e.message}", e)
            postUiMessage("Failed to start tracking service")
            stopSelf()
            return
        }

        // Initialize dependencies in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize Retrofit for StrapiApi
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://admin.fitglide.in/api/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val strapiApi = retrofit.create(StrapiApi::class.java)

                // Initialize AuthRepository
                val googleAuthManager = GoogleAuthManager(this@WorkoutTrackingService)
                authRepository = AuthRepository(googleAuthManager, this@WorkoutTrackingService)
                Log.d("WorkoutTrackingService", "AuthRepository initialized")

                // Initialize StrapiRepository
                strapiRepository = StrapiRepository(strapiApi, authRepository!!)
                Log.d("WorkoutTrackingService", "StrapiRepository initialized")

                // Initialize HealthConnectManager
                healthConnect = HealthConnectManager(this@WorkoutTrackingService)
                Log.d("WorkoutTrackingService", "HealthConnectManager initialized")

                // Initialize CommonViewModel
                commonViewModel = getCommonViewModel()
                if (healthConnect == null || strapiRepository == null || authRepository == null || commonViewModel == null) {
                    Log.e("WorkoutTrackingService", "Failed to initialize dependencies: healthConnect=$healthConnect, strapiRepository=$strapiRepository, authRepository=$authRepository, commonViewModel=$commonViewModel")
                    postUiMessage("Service initialization failed")
                    stopSelf()
                    return@launch
                }
                Log.d("WorkoutTrackingService", "Dependencies initialized successfully")
            } catch (e: Exception) {
                Log.e("WorkoutTrackingService", "Dependency initialization error: ${e.message}", e)
                postUiMessage("Service initialization error")
                stopSelf()
            }
        }

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            // Initialize location
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                    if (location != null) {
                        lastLocation = location
                        lastMovedTime = System.currentTimeMillis()
                        hasMovedRecently = true
                        Log.d("WorkoutTrackingService", "Initial GPS location: lat=${location.latitude}, lng=${location.longitude}")
                    } else {
                        Log.w("WorkoutTrackingService", "Initial location unavailable, requesting updates")
                        requestLocationUpdates()
                    }
                }?.addOnFailureListener { e ->
                    Log.e("WorkoutTrackingService", "Failed to get initial location: ${e.message}")
                    requestLocationUpdates()
                }
            } else {
                Log.w("WorkoutTrackingService", "ACCESS_FINE_LOCATION permission missing")
                postUiMessage("Please grant location permission")
                stopSelf()
                return
            }

            // Location callback
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    for (location in locationResult.locations) {
                        val newLocation = location
                        val distanceMoved = lastLocation?.distanceTo(newLocation) ?: 0f
                        lastLocation = newLocation
                        lastMovedTime = System.currentTimeMillis()
                        hasMovedRecently = distanceMoved > DISTANCE_THRESHOLD_M
                        Log.d("WorkoutTrackingService", "Location update: distance=$distanceMoved m, lat=${newLocation.latitude}, lng=${newLocation.longitude}, hasMovedRecently=$hasMovedRecently")
                    }
                }
            }

            // Step sensor listener
            stepListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let {
                        lastStepTime = System.currentTimeMillis()
                        val currentSteps = it.values[0].toLong()
                        if (initialStepCount == -1L) {
                            if (isTracking) {
                                initialStepCount = currentSteps
                                Log.d("WorkoutTrackingService", "Initialized initialStepCount: $initialStepCount")
                            } else {
                                Log.d("WorkoutTrackingService", "Step sensor active, awaiting session start")
                                return@let
                            }
                        }
                        monitoredSteps = currentSteps - initialStepCount
                        if (monitoredSteps < 0) {
                            Log.w("WorkoutTrackingService", "Negative steps detected, adjusting")
                            monitoredSteps = 0L
                            initialStepCount = currentSteps
                        }
                        if (isTracking && monitoredSteps > totalSteps) {
                            // Count steps if GPS confirms movement or after fallback timeout
                            if (hasMovedRecently || (System.currentTimeMillis() - lastStepTime > FALLBACK_TIMEOUT_MS && monitoredSteps >= MIN_STEP_THRESHOLD)) {
                                totalSteps = monitoredSteps
                                commonViewModel?.updateTrackedSteps(totalSteps.toFloat())
                                sharedPreferences.edit().putFloat("trackedSteps", totalSteps.toFloat()).apply()
                                Log.d("WorkoutTrackingService", "Steps counted: totalSteps=$totalSteps, monitoredSteps=$monitoredSteps, hasMovedRecently=$hasMovedRecently")
                            } else {
                                Log.d("WorkoutTrackingService", "Steps queued: monitoredSteps=$monitoredSteps, hasMovedRecently=$hasMovedRecently, timeSinceMove=${System.currentTimeMillis() - lastMovedTime}")
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    Log.d("WorkoutTrackingService", "Sensor accuracy changed: sensor=$sensor, accuracy=$accuracy")
                }
            }

            // Initialize sensor and location
            registerStepSensor()
            requestLocationUpdates()

            // Monitor sensor and GPS timeouts
            monitorTimeouts()
        } catch (e: Exception) {
            Log.e("WorkoutTrackingService", "Initialization error: ${e.message}", e)
            postUiMessage("Failed to initialize tracking")
            stopSelf()
        }
    }

    private fun monitorTimeouts() {
        CoroutineScope(Dispatchers.Default).launch {
            while (isServiceRunning) {
                delay(5_000L) // Check every 5 seconds
                if (isTracking) {
                    val currentTime = System.currentTimeMillis()
                    // Reinitialize sensor if no step events, preserve session
                    if (currentTime - lastStepTime > SENSOR_TIMEOUT_MS) {
                        Log.w("WorkoutTrackingService", "No step events for $SENSOR_TIMEOUT_MS ms, reinitializing sensor")
                        stepListener?.let { sensorManager.unregisterListener(it) }
                        registerStepSensor()
                    }
                    // Retry GPS if no movement
                    if (currentTime - lastMovedTime > GPS_TIMEOUT_MS && !hasMovedRecently) {
                        Log.w("WorkoutTrackingService", "No GPS movement for $GPS_TIMEOUT_MS ms, retrying location updates")
                        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
                        requestLocationUpdates(true)
                    }
                }
            }
        }
    }

    private fun requestLocationUpdates(useHighAccuracy: Boolean = false) {
        try {
            val priority = if (useHighAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val locationRequest = LocationRequest.Builder(priority, 500)
                .setMinUpdateIntervalMillis(100)
                .setMaxUpdateDelayMillis(1000)
                .setWaitForAccurateLocation(true)
                .build()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationCallback?.let {
                    fusedLocationClient?.requestLocationUpdates(locationRequest, it, mainLooper)
                    Log.d("WorkoutTrackingService", "Location updates requested with priority=$priority")
                } ?: Log.e("WorkoutTrackingService", "LocationCallback is null")
            } else {
                Log.w("WorkoutTrackingService", "ACCESS_FINE_LOCATION permission missing")
                postUiMessage("Please grant location permission")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e("WorkoutTrackingService", "Failed to request location updates: ${e.message}", e)
            postUiMessage("Location tracking unavailable")
        }
    }

    private fun registerStepSensor() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                stepSensor?.let {
                    sensorManager.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_FASTEST)
                    lastStepTime = System.currentTimeMillis()
                    Log.d("WorkoutTrackingService", "Step sensor registered with SENSOR_DELAY_FASTEST")
                } ?: run {
                    Log.w("WorkoutTrackingService", "Step sensor not available")
                    postUiMessage("Step sensor unavailable")
                    stopSelf()
                }
            } else {
                Log.w("WorkoutTrackingService", "ACTIVITY_RECOGNITION permission missing")
                postUiMessage("Please grant activity recognition permission")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e("WorkoutTrackingService", "Failed to register step sensor: ${e.message}", e)
            postUiMessage("Step sensor unavailable")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WorkoutTrackingService", "onStartCommand: intent=$intent, flags=$flags, startId=$startId")
        userId = intent?.getStringExtra("userId") ?: "1"
        workoutType = intent?.getStringExtra("workoutType") ?: "Walking"
        token = intent?.getStringExtra("token") ?: ""
        val manualStart = intent?.getBooleanExtra("manualStart", false) ?: false
        val resetSteps = intent?.getBooleanExtra("resetSteps", false) ?: false

        Log.d("WorkoutTrackingService", "onStartCommand: userId=$userId, workoutType=$workoutType, manualStart=$manualStart, resetSteps=$resetSteps")

        if (!hasRequiredPermissions()) {
            Log.e("WorkoutTrackingService", "Missing required permissions")
            postUiMessage("Missing permissions for tracking")
            stopSelf()
            return START_NOT_STICKY
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val googleApiAvailability = GoogleApiAvailability.getInstance()
                val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this@WorkoutTrackingService)
                if (resultCode != ConnectionResult.SUCCESS) {
                    Log.e("WorkoutTrackingService", "Google Play Services unavailable: $resultCode")
                    postUiMessage("Google Play Services unavailable")
                    stopSelf()
                    return@launch
                }
                Log.d("WorkoutTrackingService", "Google Play Services available")
            } catch (e: Exception) {
                Log.e("WorkoutTrackingService", "Google Play Services check failed: ${e.message}", e)
                postUiMessage("Google Play Services error")
                stopSelf()
            }
        }

        if (resetSteps) {
            resetSteps()
            return START_STICKY
        }

        if (manualStart && !isTracking) {
            startTracking()
            // Restore steps if tracking was interrupted
            totalSteps = sharedPreferences.getFloat("trackedSteps", 0f).toLong()
            isTracking = sharedPreferences.getBoolean("isTracking", false)
            commonViewModel?.updateTrackedSteps(totalSteps.toFloat())
            Log.d("WorkoutTrackingService", "Restored totalSteps=$totalSteps, isTracking=$isTracking")
        } else if (manualStart && isTracking) {
            Log.d("WorkoutTrackingService", "Already tracking, ignoring manualStart")
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
        initialStepCount = -1L // Reset to ensure fresh start
        monitoredSteps = 0L
        totalSteps = 0L
        lastLocation = null
        hasMovedRecently = false
        lastStepTime = System.currentTimeMillis()
        commonViewModel?.updateTrackedSteps(0f)
        sharedPreferences.edit()
            .putBoolean("isTracking", true)
            .putFloat("trackedSteps", 0f)
            .apply()
        Log.d("WorkoutTrackingService", "Tracking started")
    }

    private fun resetSteps() {
        initialStepCount = -1L
        monitoredSteps = 0L
        totalSteps = 0L
        hasMovedRecently = false
        isTracking = false
        commonViewModel?.updateTrackedSteps(0f)
        sharedPreferences.edit()
            .putFloat("trackedSteps", 0f)
            .putBoolean("isTracking", false)
            .apply()
        Log.d("WorkoutTrackingService", "Steps reset to 0")
    }

    private fun getCommonViewModel(): CommonViewModel? {
        return try {
            MainActivity.commonViewModel
        } catch (e: Exception) {
            Log.e("WorkoutTrackingService", "Failed to get CommonViewModel: ${e.message}")
            null
        }
    }

    private fun postUiMessage(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            commonViewModel?.postUiMessage(message)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationCallback?.let {
                fusedLocationClient?.removeLocationUpdates(it)
                Log.d("WorkoutTrackingService", "Location updates removed")
            }
            stepListener?.let {
                sensorManager.unregisterListener(it)
                Log.d("WorkoutTrackingService", "Step sensor unregistered")
            }
            isServiceRunning = false
            // Persist steps and tracking state
            sharedPreferences.edit()
                .putFloat("trackedSteps", totalSteps.toFloat())
                .putBoolean("isTracking", isTracking)
                .apply()
            Log.d("WorkoutTrackingService", "Service destroyed, steps persisted: totalSteps=$totalSteps, isTracking=$isTracking")
        } catch (e: Exception) {
            Log.e("WorkoutTrackingService", "Error during onDestroy: ${e.message}", e)
        }
    }
}