package com.trailblazewellness.fitglide.data.api

import android.util.Log
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.healthconnect.SleepData
import com.trailblazewellness.fitglide.data.healthconnect.WorkoutData
import com.trailblazewellness.fitglide.presentation.meals.*
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StrapiRepository(
    private val strapiApi: StrapiApi,
    private val authRepository: AuthRepository
) {
    private val TAG = "StrapiRepository"
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    // Health Logs
    suspend fun syncHealthLog(
        date: String,
        steps: Long,
        hydration: Float,
        heartRate: Long?,
        caloriesBurned: Float?,
        source: String,
        token: String
    ): Response<StrapiApi.HealthLogResponse> {
        val userId = authRepository.getAuthState().getId() ?: "unknown"
        val request = StrapiApi.HealthLogRequest(
            dateTime = date,
            steps = steps,
            waterIntake = hydration,
            heartRate = heartRate,
            caloriesBurned = caloriesBurned,
            source = source,
            usersPermissionsUser = StrapiApi.UserId(userId)
        )
        val body = StrapiApi.HealthLogBody(request)
        Log.d(TAG, "Syncing health log: $body with token: $token")

        val existingLog = getHealthLog(date, token, source)
        return if (existingLog.isSuccessful && existingLog.body()?.data?.isNotEmpty() == true) {
            val documentId = existingLog.body()!!.data.first().documentId
            Log.d(TAG, "Updating existing health log with documentId: $documentId")
            strapiApi.updateHealthLog(documentId, body, token).also { response ->
                logResponse("updateHealthLog", response)
            }
        } else {
            Log.d(TAG, "Posting new health log")
            strapiApi.postHealthLog(body, token).also { response ->
                logResponse("postHealthLog", response)
            }
        }
    }

    suspend fun getHealthLog(
        date: String,
        token: String,
        source: String? = null
    ): Response<StrapiApi.HealthLogListResponse> {
        val filters = mutableMapOf("filters[dateTime][\$eq]" to date)
        source?.let { filters["filters[source][\$eq]"] = it }
        Log.d(TAG, "Fetching health log with filters: $filters")
        return strapiApi.getHealthLog(filters, token).also { response ->
            logResponse("getHealthLog", response)
        }
    }

    // Sleep Logs
    suspend fun syncSleepLog(date: LocalDate, sleepData: SleepData): Response<StrapiApi.SleepLogResponse> {
        val token = "Bearer ${authRepository.getAuthState().jwt}"
        val isoDate = date.atStartOfDay().format(isoFormatter)
        val userId = authRepository.getAuthState().getId() ?: "unknown"
        val request = StrapiApi.SleepLogRequest(
            date = isoDate,
            sleepDuration = (sleepData.total.toMinutes() / 60.0).toFloat(),
            deepSleepDuration = (sleepData.deep.toMinutes() / 60.0).toFloat(),
            remSleepDuration = (sleepData.rem.toMinutes() / 60.0).toFloat(),
            lightSleepDuration = (sleepData.light.toMinutes() / 60.0).toFloat(),
            sleepAwakeDuration = (sleepData.awake.toMinutes() / 60.0).toFloat(),
            startTime = sleepData.start.toString(),
            endTime = sleepData.end.toString(),
            usersPermissionsUser = StrapiApi.UserId(userId)
        )
        val body = StrapiApi.SleepLogBody(request)
        Log.d(TAG, "Syncing sleep log: $body with token: $token")

        val existingLogs = fetchSleepLog(date)
        return if (existingLogs.isSuccessful && existingLogs.body()?.data?.isNotEmpty() == true) {
            val documentId = existingLogs.body()!!.data.first().documentId
            Log.d(TAG, "Updating existing sleep log with documentId: $documentId")
            updateSleepLog(documentId, sleepData)
        } else {
            Log.d(TAG, "Posting new sleep log")
            strapiApi.postSleepLog(body, token).also { response ->
                logResponse("postSleepLog", response)
            }
        }
    }

    suspend fun fetchSleepLog(date: LocalDate): Response<StrapiApi.SleepLogListResponse> {
        val token = "Bearer ${authRepository.getAuthState().jwt}"
        val isoDate = date.atStartOfDay().format(isoFormatter)
        val filters = mapOf("filters[date][\$eq]" to isoDate)
        Log.d(TAG, "Fetching sleep log for date: $isoDate with filters: $filters")
        return strapiApi.getSleepLog(filters, token).also { response ->
            logResponse("fetchSleepLog", response)
        }
    }

    suspend fun updateSleepLog(documentId: String, sleepData: SleepData): Response<StrapiApi.SleepLogResponse> {
        val token = "Bearer ${authRepository.getAuthState().jwt}"
        val isoDate = sleepData.start.toLocalDate().atStartOfDay().format(isoFormatter)
        val userId = authRepository.getAuthState().getId() ?: "unknown"
        val request = StrapiApi.SleepLogRequest(
            date = isoDate,
            sleepDuration = (sleepData.total.toMinutes() / 60.0).toFloat(),
            deepSleepDuration = (sleepData.deep.toMinutes() / 60.0).toFloat(),
            remSleepDuration = (sleepData.rem.toMinutes() / 60.0).toFloat(),
            lightSleepDuration = (sleepData.light.toMinutes() / 60.0).toFloat(),
            sleepAwakeDuration = (sleepData.awake.toMinutes() / 60.0).toFloat(),
            startTime = sleepData.start.toString(),
            endTime = sleepData.end.toString(),
            usersPermissionsUser = StrapiApi.UserId(userId)
        )
        val body = StrapiApi.SleepLogBody(request)
        Log.d(TAG, "Updating sleep log with documentId: $documentId, data: $body")
        return strapiApi.updateSleepLog(documentId, body, token).also { response ->
            logResponse("updateSleepLog", response)
        }
    }

    // Workouts (Plans)
    suspend fun syncWorkoutPlan(
        workoutId: String,
        title: String,
        description: String?,
        distancePlanned: Float,
        totalTimePlanned: Float,
        caloriesPlanned: Float,
        sportType: String,
        weekNumber: Int,
        exercises: List<StrapiApi.ExerciseId>,
        token: String
    ): Response<StrapiApi.WorkoutResponse> {
        val userId = authRepository.getAuthState().getId() ?: "unknown"
        val request = StrapiApi.WorkoutRequest(
            workoutId = workoutId,
            title = title,
            description = description,
            distancePlanned = distancePlanned,
            totalTimePlanned = totalTimePlanned,
            caloriesPlanned = caloriesPlanned,
            sportType = sportType,
            weekNumber = weekNumber,
            exercises = exercises,
            usersPermissionsUser = StrapiApi.UserId(userId)
        )
        val body = StrapiApi.WorkoutBody(request)
        Log.d(TAG, "Syncing workout plan: $body with token: $token")

        val existingPlans = getWorkoutPlans(userId, token)
        return if (existingPlans.isSuccessful && existingPlans.body()?.data?.isNotEmpty() == true) {
            val documentId = existingPlans.body()!!.data.first().id
            Log.d(TAG, "Updating existing workout plan with id: $documentId")
            strapiApi.updateWorkout(documentId, body, token).also { response ->
                logResponse("updateWorkout", response)
            }
        } else {
            Log.d(TAG, "Posting new workout plan")
            strapiApi.postWorkout(body, token).also { response ->
                logResponse("postWorkout", response)
            }
        }
    }

    suspend fun getWorkoutPlans(userId: String, token: String): Response<StrapiApi.WorkoutListResponse> {
        val filters = mapOf("filters[users_permissions_user][id][\$eq]" to userId)
        Log.d(TAG, "Fetching workout plans with filters: $filters")
        return strapiApi.getWorkouts(filters, token).also { response ->
            logResponse("getWorkouts", response)
        }
    }

    // Workout Logs
    suspend fun syncWorkoutLog(log: StrapiApi.WorkoutLogRequest, token: String): Response<StrapiApi.WorkoutLogResponse> {
        val body = StrapiApi.WorkoutLogBody(log)
        Log.d(TAG, "Syncing workout log: $body with token: $token")

        val dateStr = log.startTime.split("T")[0]
        val filters = mapOf(
            "filters[users_permissions_user][id][\$eq]" to (log.usersPermissionsUser.id ?: "unknown"),
            "filters[startTime][\$gte]" to "${dateStr}T00:00:00.000Z",
            "filters[startTime][\$lte]" to "${dateStr}T23:59:59.999Z"
        )
        Log.d(TAG, "Checking existing workout logs with filters: $filters")
        val existingLogs = strapiApi.getWorkoutLogs(filters, token)
        if (existingLogs.isSuccessful && existingLogs.body()?.data?.isNotEmpty() == true) {
            val existingLog = existingLogs.body()!!.data.find {
                it.startTime == log.startTime || it.logId.startsWith("wearable_${dateStr}")
            }
            if (existingLog != null) {
                Log.d(TAG, "Updating existing workout log with documentId: ${existingLog.documentId}")
                return strapiApi.updateWorkoutLog(existingLog.documentId, body, token).also { response ->
                    Log.d(TAG, "updateWorkoutLog: success=${response.isSuccessful}, code=${response.code()}, body=${response.body()?.toString()}")
                }
            }
        } else if (!existingLogs.isSuccessful) {
            Log.w(TAG, "Failed to fetch logs for deduplication: ${existingLogs.code()} - ${existingLogs.errorBody()?.string()}")
            return Response.success(null) // Skip sync to avoid duplicates
        }
        Log.d(TAG, "Posting new workout log")
        return strapiApi.postWorkoutLog(body, token).also { response ->
            Log.d(TAG, "postWorkoutLog: success=${response.isSuccessful}, code=${response.code()}, body=${response.body()?.toString()}")
        }
    }

    suspend fun getWorkoutLogs(userId: String, date: String, token: String): Response<StrapiApi.WorkoutLogListResponse> {
        val filters = mapOf(
            "filters[users_permissions_user][id][\$eq]" to userId,
            "filters[startTime][\$gte]" to "${date}T00:00:00.000Z",
            "filters[startTime][\$lte]" to "${date}T23:59:59.999Z"
        )
        Log.d(TAG, "Fetching workout logs with filters: $filters, token: $token")
        return strapiApi.getWorkoutLogs(filters, token).also { response ->
            Log.d(TAG, "getWorkoutLogs: success=${response.isSuccessful}, code=${response.code()}, body=${response.body()?.toString()}")
        }
    }

    // Health Vitals
    suspend fun getHealthVitals(userId: String, token: String): Response<StrapiApi.HealthVitalsListResponse> {
        Log.d(TAG, "Fetching health vitals for userId: $userId")
        return strapiApi.getHealthVitals(userId, "*", token).also { response ->
            logResponse("getHealthVitals", response)
        }
    }

    suspend fun postHealthVitals(data: StrapiApi.HealthVitalsRequest, token: String): Response<StrapiApi.HealthVitalsResponse> {
        val body = StrapiApi.HealthVitalsBody(data)
        Log.d(TAG, "Posting health vitals: $body with token: $token")
        return strapiApi.postHealthVitals(body, token).also { response ->
            logResponse("postHealthVitals", response)
        }
    }

    suspend fun updateHealthVitals(documentId: String, data: StrapiApi.HealthVitalsRequest, token: String): Response<StrapiApi.HealthVitalsResponse> {
        val body = StrapiApi.HealthVitalsBody(data)
        Log.d(TAG, "Updating health vitals with documentId: $documentId, data: $body")
        return strapiApi.updateHealthVitals(documentId, body, token).also { response ->
            logResponse("updateHealthVitals", response)
        }
    }

    // User Profile
    suspend fun getUserProfile(token: String): Response<StrapiApi.UserProfileResponse> {
        Log.d(TAG, "Fetching user profile")
        return strapiApi.getUserProfile(token).also { response ->
            logResponse("getUserProfile", response)
        }
    }

    suspend fun updateUserProfile(userId: String, data: StrapiApi.UserProfileRequest, token: String): Response<StrapiApi.UserProfileResponse> {
        val body = StrapiApi.UserProfileBody(data)
        Log.d(TAG, "Updating user profile for userId: $userId, data: $body with token: $token")
        return strapiApi.updateUserProfile(userId, body, token).also { response ->
            logResponse("updateUserProfile", response)
        }
    }

    // Meals and Diet
    suspend fun postCustomMealRequest(request: CustomMealRequest, token: String): Response<StrapiApi.CustomMealResponse> {
        val body = StrapiApi.CustomMealRequestBody(request)
        Log.d(TAG, "Posting custom meal request: $body with token: $token")
        return strapiApi.postCustomMealRequest(body, token).also { response ->
            logResponse("postCustomMealRequest", response)
        }
    }

    suspend fun postMealGoal(request: MealGoalRequest, token: String): Response<StrapiApi.MealGoalResponse> {
        val body = StrapiApi.MealGoalBody(request)
        Log.d(TAG, "Posting meal goal: $body with token: $token")
        return strapiApi.postMealGoal(body, token).also { response ->
            logResponse("postMealGoal", response)
        }
    }

    suspend fun getDietPlan(userId: String, date: LocalDate, token: String): Response<StrapiApi.DietPlanListResponse> {
        Log.d(TAG, "Fetching diet plan for userId: $userId and date: $date")
        return strapiApi.getDietPlan(userId, "meals.diet_components", token).also { response ->
            logResponse("getDietPlan", response)
        }
    }

    suspend fun getDietComponents(type: String, token: String): Response<StrapiApi.DietComponentListResponse> {
        Log.d(TAG, "Fetching all diet components for type: $type")
        val allComponents = mutableListOf<StrapiApi.DietComponentEntry>()
        var page = 1
        val pageSize = 100

        do {
            val response = strapiApi.getDietComponents(type, pageSize, page, token)
            logResponse("getDietComponents page $page", response)
            if (response.isSuccessful) {
                val components = response.body()?.data ?: emptyList()
                allComponents.addAll(components)
                page++
            } else {
                Log.e(TAG, "Failed to fetch page $page: ${response.code()} - ${response.errorBody()?.string()}")
                break
            }
        } while (response.body()?.data?.isNotEmpty() == true)

        Log.d(TAG, "Fetched total ${allComponents.size} diet components")
        return Response.success(StrapiApi.DietComponentListResponse(allComponents))
    }

    suspend fun postDietPlan(body: DietPlanRequest, token: String): Response<StrapiApi.DietPlanResponse> {
        val dietPlanBody = StrapiApi.DietPlanBody(body)
        Log.d(TAG, "Posting diet plan: $dietPlanBody with token: $token")
        val userId = body.userId.id ?: "unknown"
        val existingPlans = getDietPlan(userId, LocalDate.now(), token)
        if (existingPlans.isSuccessful) {
            existingPlans.body()?.data?.filter { it.active }?.forEach { plan ->
                val updatedPlan = DietPlanRequest(
                    name = plan.planId,
                    totalCalories = plan.totalCalories,
                    dietPreference = plan.dietPreference,
                    active = false,
                    pointsEarned = plan.pointsEarned,
                    dietGoal = plan.dietGoal,
                    mealIds = plan.meals?.map { it.documentId } ?: emptyList(),
                    userId = StrapiApi.UserId(userId)
                )
                updateDietPlan(plan.documentId, updatedPlan, token)
            }
        }
        return strapiApi.postDietPlan(dietPlanBody, token).also { response ->
            logResponse("postDietPlan", response)
        }
    }

    suspend fun updateDietPlan(documentId: String, body: DietPlanRequest, token: String): Response<StrapiApi.DietPlanResponse> {
        val dietPlanBody = StrapiApi.DietPlanBody(body)
        Log.d(TAG, "Updating diet plan with documentId: $documentId, data: $dietPlanBody")
        return strapiApi.updateDietPlan(documentId, dietPlanBody, token).also { response ->
            logResponse("updateDietPlan", response)
        }
    }

    suspend fun postMeal(body: MealRequest, token: String): Response<StrapiApi.MealResponse> {
        val mealBody = StrapiApi.MealBody(body)
        Log.d(TAG, "Posting meal: $mealBody with token: $token")
        return strapiApi.postMeal(mealBody, token).also { response ->
            logResponse("postMeal", response)
        }
    }

    suspend fun updateMeal(documentId: String, body: MealRequest, token: String): Response<StrapiApi.MealResponse> {
        val mealBody = StrapiApi.MealBody(body)
        Log.d(TAG, "Updating meal with documentId: $documentId, data: $mealBody")
        return strapiApi.updateMeal(documentId, mealBody, token).also { response ->
            logResponse("updateMeal", response)
        }
    }

    suspend fun getDietLogs(userId: String, date: LocalDate, token: String): Response<StrapiApi.DietLogListResponse> {
        Log.d(TAG, "Fetching diet logs for userId: $userId and date: $date")
        return strapiApi.getDietLogs(userId, date.toString(), token).also { response ->
            logResponse("getDietLogs", response)
        }
    }

    suspend fun postDietLog(body: StrapiApi.DietLogRequest, token: String): Response<StrapiApi.DietLogResponse> {
        val dietLogBody = StrapiApi.DietLogBody(body)
        Log.d(TAG, "Posting diet log: $dietLogBody with token: $token")
        return strapiApi.postDietLog(dietLogBody, token).also { response ->
            logResponse("postDietLog", response)
        }
    }

    suspend fun putDietLog(logId: String, request: StrapiApi.DietLogUpdateRequest, token: String): Response<StrapiApi.DietLogResponse> {
        val body = StrapiApi.DietLogUpdateBody(request)
        Log.d(TAG, "Updating diet log with PUT: logId=$logId, request=$body")
        return strapiApi.putDietLog(logId, body, token).also { response ->
            logResponse("putDietLog", response)
        }
    }

    suspend fun postFeedback(request: FeedbackRequest, token: String): Response<StrapiApi.FeedbackResponse> {
        val body = StrapiApi.FeedbackBody(request)
        Log.d(TAG, "Posting feedback: $body with token: $token")
        return strapiApi.postFeedback(body, token).also { response ->
            logResponse("postFeedback", response)
        }
    }

    // Packs
    suspend fun getPacks(userId: String, token: String): Response<StrapiApi.PackListResponse> {
        Log.d(TAG, "Fetching packs for userId: $userId")
        return strapiApi.getPacks(userId, token).also { response ->
            logResponse("getPacks", response)
        }
    }

    suspend fun postPack(request: StrapiApi.PackRequest, token: String): Response<StrapiApi.PackResponse> {
        val body = StrapiApi.PackBody(request)
        Log.d(TAG, "Posting pack: $body with token: $token")
        return strapiApi.postPack(body, token).also { response ->
            logResponse("postPack", response)
        }
    }

    suspend fun updatePack(id: String, request: StrapiApi.PackRequest, token: String): Response<StrapiApi.PackResponse> {
        val body = StrapiApi.PackBody(request)
        Log.d(TAG, "Updating pack with id: $id, data: $body")
        return strapiApi.updatePack(id, body, token).also { response ->
            logResponse("updatePack", response)
        }
    }

    // Posts
    suspend fun getPosts(packId: String?, token: String): Response<StrapiApi.PostListResponse> {
        Log.d(TAG, "Fetching posts for packId: $packId")
        return strapiApi.getPosts(packId, token).also { response ->
            logResponse("getPosts", response)
        }
    }

    suspend fun postPost(request: StrapiApi.PostRequest, token: String): Response<StrapiApi.PostResponse> {
        val body = StrapiApi.PostBody(request)
        Log.d(TAG, "Posting post: $body with token: $token")
        return strapiApi.postPost(body, token).also { response ->
            logResponse("postPost", response)
        }
    }

    // Cheers
    suspend fun getCheers(userId: String, token: String): Response<StrapiApi.CheerListResponse> {
        Log.d(TAG, "Fetching cheers for userId: $userId")
        return strapiApi.getCheers(userId, token).also { response ->
            logResponse("getCheers", response)
        }
    }

    suspend fun postCheer(request: StrapiApi.CheerRequest, token: String): Response<StrapiApi.CheerResponse> {
        val body = StrapiApi.CheerBody(request)
        Log.d(TAG, "Posting cheer: $body with token: $token")
        return strapiApi.postCheer(body, token).also { response ->
            logResponse("postCheer", response)
        }
    }

    // Challenges
    suspend fun getChallenges(userId: String, token: String): Response<StrapiApi.ChallengeListResponse> {
        Log.d(TAG, "Fetching challenges for userId: $userId")
        return strapiApi.getChallenges(userId, token).also { response ->
            logResponse("getChallenges", response)
        }
    }

    suspend fun postChallenge(request: StrapiApi.ChallengeRequest, token: String): Response<StrapiApi.ChallengeResponse> {
        val body = StrapiApi.ChallengeBody(request)
        Log.d(TAG, "Posting challenge: $body with token: $token")
        return strapiApi.postChallenge(body, token).also { response ->
            logResponse("postChallenge", response)
        }
    }

    suspend fun updateChallenge(id: String, request: StrapiApi.ChallengeRequest, token: String): Response<StrapiApi.ChallengeResponse> {
        val body = StrapiApi.ChallengeBody(request)
        Log.d(TAG, "Updating challenge with id: $id, data: $body")
        return strapiApi.updateChallenge(id, body, token).also { response ->
            logResponse("updateChallenge", response)
        }
    }

    // Friends
    suspend fun getFriends(filters: Map<String, String> = emptyMap(), token: String): Response<StrapiApi.FriendListResponse> {
        val adjustedFilters = filters.mapKeys { it.key.replace("[\$eq]", "") }
        Log.d(TAG, "Fetching friends with adjusted filters: $adjustedFilters")
        return strapiApi.getFriends(adjustedFilters, token).also { response ->
            logResponse("getFriends", response)
        }
    }

    suspend fun postFriend(request: StrapiApi.FriendRequest, token: String): Response<StrapiApi.FriendResponse> {
        val body = StrapiApi.FriendBody(request)
        Log.d(TAG, "Posting friend: $body with token: $token")
        return strapiApi.postFriend(body, token).also { response ->
            logResponse("postFriend", response)
        }
    }

    suspend fun updateFriend(id: String, request: StrapiApi.FriendRequest, token: String): Response<StrapiApi.FriendResponse> {
        val body = StrapiApi.FriendBody(request)
        Log.d(TAG, "Updating friend with id: $id, data: $body")
        return strapiApi.updateFriend(id, body, token).also { response ->
            logResponse("updateFriend", response)
        }
    }

    // Comments
    suspend fun getComments(postId: String, token: String): Response<StrapiApi.CommentListResponse> {
        Log.d(TAG, "Fetching comments for postId: $postId")
        return strapiApi.getComments(postId, token).also { response ->
            logResponse("getComments", response)
        }
    }

    suspend fun postComment(request: StrapiApi.CommentEntry, token: String): Response<StrapiApi.CommentListResponse> {
        Log.d(TAG, "Posting comment: $request with token: $token")
        return strapiApi.postComment(request, token).also { response ->
            logResponse("postComment", response)
        }
    }

    suspend fun getDesiMessages(token: String): Response<StrapiApi.DesiMessageResponse> {
        return strapiApi.getDesiMessages("*", token)
    }

    suspend fun getBadges(token: String): Response<StrapiApi.BadgeListResponse> {
        return strapiApi.getBadges("*", token)
    }

    private fun <T> logResponse(method: String, response: Response<T>) {
        if (response.isSuccessful) {
            Log.d(TAG, "$method successful: ${response.code()} - Body: ${response.body()}")
        } else {
            Log.e(TAG, "$method failed: ${response.code()} - Error: ${response.errorBody()?.string() ?: "No error body"}")
        }
    }
}