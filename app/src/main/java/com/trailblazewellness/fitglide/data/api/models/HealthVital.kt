package com.trailblazewellness.fitglide.data.api.models

data class HealthVitalsRequest(
    val WeightInKilograms: Int? = null,
    val height: Int? = null,
    val gender: String? = null,
    val date_of_birth: String? = null,
    val activity_level: String? = null,
    val weightLossGoal: Double? = null,
    val stepGoal: Int? = null,
    val waterGoal: Float? = null,
    val calorieGoal: Int? = null,
    val users_permissions_user: UserId? = null
)

data class HealthVitalsListResponse(val data: List<HealthVitalsEntry>)

data class HealthVitalsEntry(
    val documentId: String,
    val WeightInKilograms: Int? = null,
    val height: Int? = null,
    val gender: String? = null,
    val date_of_birth: String? = null,
    val activity_level: String? = null,
    val weightLossGoal: Double? = null, // Added
    val stepGoal: Int? = null,
    val waterGoal: Float? = null,
    val calorieGoal: Int? = null
)

data class UserId(val id: String)

data class UserProfileRequest(
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val mobile: String? = null
)

data class UserProfileResponse(
    val id: String,
    val username: String,
    val firstName: String?,
    val lastName: String?,
    val email: String,
    val mobile: String? = null
)

data class HealthLogRequest(val dateTime: String, val steps: Long, val waterIntake: Float, val users_permissions_user: UserId)
data class HealthLogListResponse(val data: List<HealthLogEntry>)
data class HealthLogEntry(val documentId: String, val attributes: Map<String, Any>)

data class SleepLogRequest(
    val date: String,
    val sleep_duration: Float,
    val deep_sleep_duration: Float,
    val rem_sleep_duration: Float,
    val light_sleep_duration: Float,
    val sleep_awake_duration: Float,
    val startTime: String? = null,
    val endTime: String? = null,
    val users_permissions_user: UserId
)
data class SleepLogListResponse(val data: List<SleepLogEntry>)
data class SleepLogEntry(val documentId: String, val attributes: Map<String, Any>)

data class WorkoutRequest(
    val startTime: String,
    val calories: Double?,
    val heartRateAvg: Long?,
    val sport_type: String = "Running",
    val users_permissions_user: UserId
)
data class WorkoutListResponse(val data: List<WorkoutEntry>)
data class WorkoutEntry(val documentId: String, val attributes: Map<String, Any>)

data class FriendRequest(val username: UserId, val friendUserId: UserId)
data class FriendListResponse(val data: List<FriendEntry>)
data class FriendEntry(val documentId: String, val attributes: Map<String, Any>)

data class ChallengeRequest(val challengerId: UserId, val challengeeId: UserId, val goal: String)
data class ChallengeListResponse(val data: List<ChallengeEntry>)
data class ChallengeEntry(val documentId: String, val attributes: Map<String, Any>)