package com.trailblazewellness.fitglide.presentation.profile

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectRepository
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

data class ProfileData(
    val weight: Double? = null,
    val height: Double? = null,
    val gender: String? = null,
    val dob: String? = null,
    val activityLevel: String? = null,
    val bmi: Double? = null,
    val bmr: Double? = null,
    val tdee: Double? = null,
    val weightLossGoal: Double? = null,
    val weightLossStrategy: String? = null,
    val stepGoal: Int? = null,
    val waterGoal: Float? = null,
    val calorieGoal: Int? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val mobile: Long? = null,
    val email: String? = null,
    val notificationsEnabled: Boolean? = true,
    val maxGreetingsEnabled: Boolean? = true
)

class ProfileViewModel(
    private val strapiRepository: StrapiRepository,
    private val authRepository: AuthRepository,
    private val healthConnectRepository: HealthConnectRepository,
    private val homeViewModel: HomeViewModel // Still needed for homeData, but not for badges
) : ViewModel() {
    var profileData by mutableStateOf(ProfileData())
    private var documentId: String? = null

    // Configurable base values (could be fetched from Strapi or a config)
    private val defaultStepBase: Int = 10000
    private val defaultWaterBase: Float = 2.0f

    init {
        fetchProfileData()
        fetchPersonalData()
    }

    fun fetchProfileData() {
        val userId = authRepository.getAuthState().getId().toString()
        viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 3
            while (attempts < maxAttempts) {
                try {
                    val token = "Bearer ${authRepository.getAuthState().jwt}"
                    val response = strapiRepository.getHealthVitals(userId, token)
                    if (response.isSuccessful) {
                        val vitalsList = response.body()?.data
                        Log.d("ProfileViewModel", "Fetched health vitals list: $vitalsList")
                        val vitals = vitalsList?.firstOrNull()
                        if (vitals != null) {
                            documentId = vitals.documentId
                            profileData = profileData.copy(
                                weight = vitals.WeightInKilograms?.toDouble(),
                                height = vitals.height?.toDouble(),
                                gender = vitals.gender,
                                dob = vitals.date_of_birth,
                                activityLevel = vitals.activity_level,
                                weightLossGoal = vitals.weight_loss_goal?.toDouble(),
                                weightLossStrategy = vitals.weight_loss_strategy,
                                stepGoal = vitals.stepGoal,
                                waterGoal = vitals.waterGoal,
                                calorieGoal = vitals.calorieGoal
                            )
                            Log.d("ProfileViewModel", "Loaded profileData: $profileData")
                            calculateMetrics()
                            return@launch
                        } else {
                            Log.e("ProfileViewModel", "No health vitals found in response")
                            calculateMetrics()
                            return@launch
                        }
                    } else {
                        Log.e("ProfileViewModel", "Failed to fetch health vitals: ${response.code()} - ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("ProfileViewModel", "Exception fetching health vitals (attempt ${attempts + 1}): ${e.message}")
                }
                attempts++
                if (attempts < maxAttempts) kotlinx.coroutines.delay(2000)
            }
            Log.e("ProfileViewModel", "Failed to fetch health vitals after $maxAttempts attempts")
            calculateMetrics()
        }
    }

    fun fetchPersonalData() {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt}"
                val response = strapiRepository.getUserProfile(token)
                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        profileData = profileData.copy(
                            firstName = user.firstName ?: authRepository.getAuthState().userName,
                            lastName = user.lastName,
                            email = user.email,
                            mobile = user.mobile,
                            notificationsEnabled = user.notificationsEnabled ?: profileData.notificationsEnabled,
                            maxGreetingsEnabled = user.maxGreetingsEnabled ?: profileData.maxGreetingsEnabled
                        )
                        Log.d("ProfileViewModel", "Loaded personal data: firstName=${user.firstName}, lastName=${user.lastName}, email=${user.email}, mobile=${user.mobile}, notificationsEnabled=${user.notificationsEnabled}, maxGreetingsEnabled=${user.maxGreetingsEnabled}")
                        user.firstName?.let { authRepository.updateUserName(it) }
                    } else {
                        Log.e("ProfileViewModel", "No user profile data in response")
                    }
                } else {
                    Log.e("ProfileViewModel", "Failed to fetch user profile: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Exception fetching user profile: ${e.message}")
            }
        }
    }

    private suspend fun getWorkoutIntensity(): Double {
        val token = "Bearer ${authRepository.getAuthState().jwt}"
        val userId = authRepository.getAuthState().getId().toString()
        val workoutResponse = strapiRepository.getWorkoutLogs(userId, LocalDate.now().minusDays(1).toString(), token)
        return if (workoutResponse.isSuccessful && workoutResponse.body()?.data?.isNotEmpty() == true) {
            val workoutEntry = workoutResponse.body()!!.data.first()
            workoutEntry.calories?.toDouble() ?: 0.0
        } else {
            // Use activityLevel to determine a more dynamic fallback
            val intensityMultiplier = when (profileData.activityLevel) {
                "Sedentary (little/no exercise)" -> 1.0
                "Light exercise (1-3 days/week)" -> 1.5
                "Moderate exercise (3-5 days/week)" -> 2.0
                "Heavy exercise (6-7 days/week)" -> 2.5
                "Very heavy exercise (Twice/day)" -> 3.0
                else -> 1.0 // Default to sedentary if not set
            }
            200.0 * intensityMultiplier // Base value adjusted by activity level
        }
    }

    fun calculateMetrics() {
        val weight = profileData.weight
        val height = profileData.height
        val gender = profileData.gender
        val dob = profileData.dob
        val activityLevel = profileData.activityLevel
        val weightLossGoal = profileData.weightLossGoal
        val strategy = profileData.weightLossStrategy

        if (weight != null && height != null) {
            val bmi = weight / ((height / 100) * (height / 100))
            var bmr: Double? = null
            var tdee: Double? = null

            if (dob != null && gender != null) {
                val age = Period.between(LocalDate.parse(dob), LocalDate.now()).years
                bmr = when (gender.lowercase()) {
                    "male" -> 88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age)
                    "female" -> 447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age)
                    else -> null
                }
                tdee = bmr?.let { bmrValue ->
                    when (activityLevel) {
                        "Sedentary (little/no exercise)" -> bmrValue * 1.2
                        "Light exercise (1-3 days/week)" -> bmrValue * 1.375
                        "Moderate exercise (3-5 days/week)" -> bmrValue * 1.55
                        "Heavy exercise (6-7 days/week)" -> bmrValue * 1.725
                        "Very heavy exercise (Twice/day)" -> bmrValue * 1.9
                        else -> bmrValue * 1.2
                    }
                }
            }

            if (tdee != null && weightLossGoal != null && strategy != null) {
                viewModelScope.launch {
                    val workoutIntensity = getWorkoutIntensity()
                    val deficit = when (strategy) {
                        "Lean-(0.25 kg/week)" -> 275.0
                        "Aggressive-(0.5 kg/week)" -> 550.0
                        "Custom" -> 275.0
                        else -> 0.0
                    }
                    val calorieBonus = if (workoutIntensity > 500.0) 200.0 else 0.0
                    val calorieGoal = (tdee - deficit + calorieBonus).toInt()

                    val stepBase = defaultStepBase
                    val stepAdjust = when (strategy) {
                        "Lean-(0.25 kg/week)" -> 1000
                        "Aggressive-(0.5 kg/week)" -> 2000
                        "Custom" -> 1000
                        else -> 0
                    }
                    val stepWorkoutBoost = (workoutIntensity / 500.0).toInt() * 500
                    val stepGoal = stepBase + stepAdjust + stepWorkoutBoost

                    val waterBase = defaultWaterBase
                    val waterAdjust = when (strategy) {
                        "Lean-(0.25 kg/week)" -> 0.25f
                        "Aggressive-(0.5 kg/week)" -> 0.5f
                        "Custom" -> 0.25f
                        else -> 0.0f
                    }
                    val waterWorkoutBoost = (workoutIntensity / 500.0).toFloat() * 0.1f
                    val waterGoal = waterBase + waterAdjust + waterWorkoutBoost

                    profileData = profileData.copy(
                        bmi = bmi,
                        bmr = bmr,
                        tdee = tdee,
                        calorieGoal = calorieGoal,
                        stepGoal = stepGoal,
                        waterGoal = waterGoal
                    )
                    saveProfileData() // Auto-save after calculation
                }
            } else {
                profileData = profileData.copy(bmi = bmi, bmr = bmr, tdee = tdee)
            }
            Log.d("ProfileViewModel", "Calculated: BMI=$bmi, BMR=$bmr, TDEE=$tdee, Goals=$profileData")
        } else {
            Log.w("ProfileViewModel", "Cannot calculate: weight=$weight, height=$height")
        }
    }

    fun saveProfileData(strategy: String? = null) {
        viewModelScope.launch {
            try {
                val request = StrapiApi.HealthVitalsRequest(
                    WeightInKilograms = profileData.weight?.toInt(),
                    height = profileData.height?.toInt(),
                    gender = profileData.gender,
                    date_of_birth = profileData.dob,
                    activity_level = profileData.activityLevel,
                    weight_loss_goal = profileData.weightLossGoal?.toInt(),
                    stepGoal = profileData.stepGoal,
                    waterGoal = profileData.waterGoal,
                    calorieGoal = profileData.calorieGoal,
                    weight_loss_strategy = profileData.weightLossStrategy,
                    users_permissions_user = StrapiApi.UserId(
                        authRepository.getAuthState().getId().toString()
                    )
                )
                val token = "Bearer ${authRepository.getAuthState().jwt}"
                val response = if (documentId != null) {
                    Log.d("ProfileViewModel", "Updating existing health vitals: $documentId")
                    strapiRepository.updateHealthVitals(documentId!!, request, token)
                } else {
                    Log.d("ProfileViewModel", "Posting new health vitals")
                    strapiRepository.postHealthVitals(request, token)
                }
                if (response.isSuccessful) {
                    Log.d("ProfileViewModel", "Profile data saved successfully")
                    if (strategy != null) {
                        profileData = profileData.copy(weightLossStrategy = strategy)
                    }
                    if (documentId == null) {
                        fetchProfileData()
                    }
                } else {
                    Log.e("ProfileViewModel", "Failed to save profile data: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Exception saving profile data: ${e.message}")
            }
        }
    }

    fun savePersonalData() {
        viewModelScope.launch {
            try {
                val request = StrapiApi.UserProfileRequest(
                    firstName = profileData.firstName,
                    lastName = profileData.lastName,
                    email = profileData.email,
                    mobile = profileData.mobile,
                    notificationsEnabled = profileData.notificationsEnabled,
                    maxGreetingsEnabled = profileData.maxGreetingsEnabled
                )
                val token = "Bearer ${authRepository.getAuthState().jwt}"
                val userId = authRepository.getAuthState().getId().toString()
                Log.d("ProfileViewModel", "Saving personal data: $request for userId=$userId")
                val response = strapiRepository.updateUserProfile(userId, request, token)
                if (response.isSuccessful) {
                    Log.d("ProfileViewModel", "Personal data saved successfully: ${response.body()}")
                    profileData.firstName?.let { authRepository.updateUserName(it) }
                    fetchPersonalData()
                } else {
                    Log.e("ProfileViewModel", "Failed to save personal data: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Exception saving personal data: ${e.message}")
            }
        }
    }
}