    package com.trailblazewellness.fitglide.data.api

    import com.google.gson.annotations.SerializedName
    import com.trailblazewellness.fitglide.presentation.meals.*
    import retrofit2.Response
    import retrofit2.http.*

    interface StrapiApi {

        // Health Logs
        @POST("health-logs")
        suspend fun postHealthLog(
            @Body body: HealthLogBody,
            @Header("Authorization") token: String
        ): Response<HealthLogResponse>

        @PUT("health-logs/{id}")
        suspend fun updateHealthLog(
            @Path("id") id: String,
            @Body body: HealthLogBody,
            @Header("Authorization") token: String
        ): Response<HealthLogResponse>

        @GET("health-logs")
        suspend fun getHealthLog(
            @QueryMap(encoded = true) filters: Map<String, String>,
            @Header("Authorization") token: String
        ): Response<HealthLogListResponse>

        // Sleep Logs
        @GET("sleeplogs")
        suspend fun getSleepLog(
            @QueryMap filters: Map<String, String>,
            @Header("Authorization") token: String
        ): Response<StrapiApi.SleepLogListResponse>

        @POST("sleeplogs")
        suspend fun postSleepLog(
            @Body body: StrapiApi.SleepLogBody,
            @Header("Authorization") token: String
        ): Response<StrapiApi.SleepLogResponse>

        @PUT("sleeplogs/{id}")
        suspend fun updateSleepLog(
            @Path("id") id: String,
            @Body body: StrapiApi.SleepLogBody,
            @Header("Authorization") token: String
        ): Response<StrapiApi.SleepLogResponse>

        // Workouts (Plans)
        @POST("workouts")
        suspend fun postWorkout(
            @Body body: WorkoutBody,
            @Header("Authorization") token: String
        ): Response<WorkoutResponse>

        @PUT("workouts/{id}")
        suspend fun updateWorkout(
            @Path("id") id: String,
            @Body body: WorkoutBody,
            @Header("Authorization") token: String
        ): Response<WorkoutResponse>

        @GET("workouts")
        suspend fun getWorkouts(
            @QueryMap filters: Map<String, String>,
            @Header("Authorization") token: String
        ): Response<WorkoutListResponse>

        // Workout Logs
        @POST("workout-logs")
        suspend fun postWorkoutLog(
            @Body body: WorkoutLogBody,
            @Header("Authorization") token: String
        ): Response<WorkoutLogResponse>

        @PUT("workout-logs/{documentId}")
        suspend fun updateWorkoutLog(
            @Path("documentId") documentId: String,
            @Body body: WorkoutLogBody,
            @Header("Authorization") token: String
        ): Response<WorkoutLogResponse>

        @GET("workout-logs")
        suspend fun getWorkoutLogs(
            @QueryMap filters: Map<String, String>,
            @Header("Authorization") token: String,
            @Header("Content-Type") contentType: String = "application/json",
            @Header("Accept") accept: String = "application/json"
        ): Response<WorkoutLogListResponse>

        // Health Vitals
        @GET("health-vitals")
        suspend fun getHealthVitals(
            @Query("filters[users_permissions_user][id][\$eq]") userId: String,
            @Query("populate") populate: String = "*",
            @Header("Authorization") token: String
        ): Response<HealthVitalsListResponse>

        @POST("health-vitals")
        suspend fun postHealthVitals(
            @Body body: HealthVitalsBody,
            @Header("Authorization") token: String
        ): Response<HealthVitalsResponse>

        @PUT("health-vitals/{id}")
        suspend fun updateHealthVitals(
            @Path("id") id: String,
            @Body body: HealthVitalsBody,
            @Header("Authorization") token: String
        ): Response<HealthVitalsResponse>

        // User Profile
        @GET("users/me")
        suspend fun getUserProfile(
            @Header("Authorization") token: String
        ): Response<UserProfileResponse>

        @PUT("users/{id}")
        suspend fun updateUserProfile(
            @Path("id") id: String,
            @Body body: UserProfileBody,
            @Header("Authorization") token: String
        ): Response<UserProfileResponse>

        // Meals and Diet
        @POST("custom-meal-requests")
        suspend fun postCustomMealRequest(
            @Body body: CustomMealRequestBody,
            @Header("Authorization") token: String
        ): Response<CustomMealResponse>

        @POST("meal-goals")
        suspend fun postMealGoal(
            @Body body: MealGoalBody,
            @Header("Authorization") token: String
        ): Response<MealGoalResponse>

        @GET("diet-plans")
        suspend fun getDietPlan(
            @Query("filters[users_permissions_user][id][\$eq]") userId: String,
            @Query("populate") populate: String = "meals.diet_components",
            @Header("Authorization") token: String
        ): Response<DietPlanListResponse>

        @GET("diet-components")
        suspend fun getDietComponents(
            @Query("filters[food_type][\$eq]") type: String,
            @Query("pagination[pageSize]") pageSize: Int = 100,
            @Query("pagination[page]") page: Int = 1,
            @Header("Authorization") token: String
        ): Response<DietComponentListResponse>

        @POST("diet-plans")
        suspend fun postDietPlan(
            @Body body: DietPlanBody,
            @Header("Authorization") token: String
        ): Response<DietPlanResponse>

        @PUT("diet-plans/{documentId}")
        suspend fun updateDietPlan(
            @Path("documentId") documentId: String,
            @Body body: DietPlanBody,
            @Header("Authorization") token: String
        ): Response<DietPlanResponse>

        @POST("meals")
        suspend fun postMeal(
            @Body body: MealBody,
            @Header("Authorization") token: String
        ): Response<MealResponse>

        @PUT("meals/{documentId}")
        suspend fun updateMeal(
            @Path("documentId") documentId: String,
            @Body body: MealBody,
            @Header("Authorization") token: String
        ): Response<MealResponse>

        @GET("diet-logs")
        suspend fun getDietLogs(
            @Query("filters[users_permissions_user][id][\$eq]") userId: String,
            @Query("filters[date][\$eq]") date: String,
            @Header("Authorization") token: String
        ): Response<DietLogListResponse>

        @POST("diet-logs")
        suspend fun postDietLog(
            @Body body: DietLogBody,
            @Header("Authorization") token: String
        ): Response<DietLogResponse>

        @PUT("diet-logs/{documentId}")
        suspend fun putDietLog(
            @Path("documentId") documentId: String,
            @Body body: DietLogUpdateBody,
            @Header("Authorization") token: String
        ): Response<DietLogResponse>

        @POST("meal-feedbacks")
        suspend fun postFeedback(
            @Body body: FeedbackBody,
            @Header("Authorization") token: String
        ): Response<FeedbackResponse>

        // Packs
        @GET("packs")
        suspend fun getPacks(
            @Query("filters[gliders][id][\$eq]") userId: String, // Changed to gliders
            @Header("Authorization") token: String
        ): Response<PackListResponse>

        @POST("packs")
        suspend fun postPack(
            @Body body: PackBody,
            @Header("Authorization") token: String
        ): Response<PackResponse>

        @PUT("packs/{id}")
        suspend fun updatePack(
            @Path("id") id: String,
            @Body body: PackBody,
            @Header("Authorization") token: String
        ): Response<PackResponse>

        // Posts
        @GET("posts")
        suspend fun getPosts(
            @Query("filters[pack][id][\$eq]") packId: String? = null, // Optional
            @Header("Authorization") token: String
        ): Response<PostListResponse>

        @POST("posts")
        suspend fun postPost(
            @Body body: PostBody,
            @Header("Authorization") token: String
        ): Response<PostResponse>

        // Cheers
        @GET("cheers")
        suspend fun getCheers(
            @Query("filters[receiver][id][\$eq]") userId: String,
            @Header("Authorization") token: String
        ): Response<CheerListResponse>

        @POST("cheers")
        suspend fun postCheer(
            @Body body: CheerBody,
            @Header("Authorization") token: String
        ): Response<CheerResponse>

        // Challenges
        @GET("challenges")
        suspend fun getChallenges(
            @Query("filters[challengerId][id][\$eq]") userId: String, // Changed to challengerId
            @Header("Authorization") token: String
        ): Response<ChallengeListResponse>

        @POST("challenges")
        suspend fun postChallenge(
            @Body body: ChallengeBody,
            @Header("Authorization") token: String
        ): Response<ChallengeResponse>

        @PUT("challenges/{id}")
        suspend fun updateChallenge(
            @Path("id") id: String,
            @Body body: ChallengeBody,
            @Header("Authorization") token: String
        ): Response<ChallengeResponse>

        // Friends
        @GET("friends")
        suspend fun getFriends(
            @QueryMap filters: Map<String, String>,
            @Header("Authorization") token: String
        ): Response<FriendListResponse>

        @POST("friends")
        suspend fun postFriend(
            @Body body: FriendBody,
            @Header("Authorization") token: String
        ): Response<FriendResponse>

        @PUT("friends/{id}")
        suspend fun updateFriend(
            @Path("id") id: String,
            @Body body: FriendBody,
            @Header("Authorization") token: String
        ): Response<FriendResponse>

        @GET("comments")
        suspend fun getComments(
            @Query("filters[post][id][\$eq]") postId: String,
            @Header("Authorization") token: String
        ): Response<CommentListResponse>

        @POST("comments")
        suspend fun postComment(
            @Body body: CommentEntry,
            @Header("Authorization") token: String
        ): Response<CommentListResponse>

        @GET("strava/auth")
        suspend fun initiateStravaAuth(
            @Header("Authorization") token: String // Strapi JWT
        ): Response<StravaAuthResponse>

        @POST("strava/token")
        suspend fun exchangeStravaCode(
            @Body request: StravaTokenRequest,
            @Header("Authorization") token: String // Strapi JWT
        ): Response<StravaTokenResponse>

        @GET("strava/sync-activities")
        suspend fun syncStravaActivities(
            @Query("per_page") perPage: Int = 10,
            @Header("Authorization") token: String // Strapi JWT
        ): Response<WorkoutLogListResponse> // Returns workout-logs entries

        @GET("desi-messages")
        suspend fun getDesiMessages(
            @Query("populate") populate: String = "*",
            @Header("Authorization") token: String
        ): Response<DesiMessageResponse>

        @GET("badges")
        suspend fun getBadges(
            @Query("populate") populate: String = "*",
            @Header("Authorization") token: String
        ): Response<BadgeListResponse>


        // Data Classes (Reused and New)
        data class UserId(
            @SerializedName("id") val id: String? // Changed to String? to allow null
        )
        // Health Logs
        data class HealthLogBody(val data: HealthLogRequest)
        data class HealthLogRequest(
            val dateTime: String?,
            val steps: Long?,
            val waterIntake: Float?,
            val heartRate: Long?,
            val caloriesBurned: Float? = null, // New field
            val source: String?,
            @SerializedName("users_permissions_user") val usersPermissionsUser: UserId?
        )
        data class HealthLogResponse(val data: HealthLogEntry)
        data class HealthLogListResponse(val data: List<HealthLogEntry>)
        data class HealthLogEntry(
            @SerializedName("documentId") val documentId: String,
            val dateTime: String,
            val steps: Long?,
            val waterIntake: Float?,
            val heartRate: Long?, // Changed to Long? to match biginteger
            val caloriesBurned: Float? = null, // New field
            val source: String?,
            @SerializedName("users_permissions_user") val usersPermissionsUser: UserId?,
            val createdAt: String?
        )

        // Sleep Logs
        data class SleepLogBody(val data: SleepLogRequest)
        data class SleepLogRequest(
            val date: String,
            @SerializedName("sleep_duration") val sleepDuration: Float,
            @SerializedName("deep_sleep_duration") val deepSleepDuration: Float,
            @SerializedName("rem_sleep_duration") val remSleepDuration: Float,
            @SerializedName("light_sleep_duration") val lightSleepDuration: Float,
            @SerializedName("sleep_awake_duration") val sleepAwakeDuration: Float,
            val startTime: String? = null,
            val endTime: String? = null,
            @SerializedName("users_permissions_user") val usersPermissionsUser: UserId
        )
        data class SleepLogResponse(val data: SleepLogEntry)
        data class SleepLogListResponse(val data: List<SleepLogEntry>)
        data class SleepLogEntry(
            @SerializedName("documentId") val documentId: String,
            val attributes: Map<String, Any>?
        )

        // Workouts (Plans)
        data class WorkoutBody(val data: WorkoutRequest)
        data class WorkoutRequest(
            val workoutId: String,
            @SerializedName("Title") val title: String,
            @SerializedName("Description") val description: String?,
            @SerializedName("DistancePlanned") val distancePlanned: Float,
            @SerializedName("TotalTimePlanned") val totalTimePlanned: Float,
            @SerializedName("CaloriesPlanned") val caloriesPlanned: Float,
            @SerializedName("sport_type") val sportType: String,
            @SerializedName("weekNumber") val weekNumber: Int,
            val exercises: List<ExerciseId>,
            @SerializedName("users_permissions_user") val usersPermissionsUser: UserId
        )

        data class ExerciseId(val id: String)
        data class WorkoutResponse(val data: WorkoutEntry)
        data class WorkoutListResponse(val data: List<WorkoutEntry>)
        data class WorkoutEntry(
            val id: String,
            @SerializedName("workoutId") val workoutId: String,
            @SerializedName("Title") val title: String,
            @SerializedName("Description") val description: String?,
            @SerializedName("DistancePlanned") val distancePlanned: Float,
            @SerializedName("TotalTimePlanned") val totalTimePlanned: Float,
            @SerializedName("CaloriesPlanned") val caloriesPlanned: Float,
            @SerializedName("sport_type") val sportType: String,
            @SerializedName("dayNumber") val dayNumber: Int,
            @SerializedName("weekNumber") val weekNumber: Int,
            val exercises: List<ExerciseEntry>
        )
        data class ExerciseEntry(
            val id: String,
            val name: String,
            val reps: Int?,
            val duration: Float?
        )

        // Workout Logs
        data class WorkoutLogBody(val data: WorkoutLogRequest)
        data class WorkoutLogRequest(
            val logId: String,
            val workout: UserId?,
            val startTime: String,
            val endTime: String,
            @SerializedName("Distance") val distance: Float,
            @SerializedName("TotalTime") val totalTime: Float,
            @SerializedName("Calories") val calories: Float,
            @SerializedName("HeartRateAverage") val heartRateAverage: Long,
            @SerializedName("HeartRateMaximum") val heartRateMaximum: Long,
            @SerializedName("HeartRateMinimum") val heartRateMinimum: Long,
            val route: List<Map<String, Float>>,
            val completed: Boolean,
            val notes: String,
            @SerializedName("users_permissions_user") val usersPermissionsUser: UserId
        )
        data class WorkoutLogResponse(val data: WorkoutLogEntry)
        data class WorkoutLogListResponse(val data: List<WorkoutLogEntry>)
        data class WorkoutLogEntry(
            val id: String,
            val documentId: String,
            val logId: String,
            val workout: UserId?,
            val startTime: String,
            val endTime: String,
            @SerializedName("Distance") val distance: Float?,
            @SerializedName("TotalTime") val totalTime: Float?,
            @SerializedName("Calories") val calories: Float?,
            @SerializedName("HeartRateAverage") val heartRateAverage: Long?,
            @SerializedName("HeartRateMaximum") val heartRateMaximum: Long?,
            @SerializedName("HeartRateMinimum") val heartRateMinimum: Long?,
            val route: List<Map<String, Float>>?,
            val completed: Boolean,
            val notes: String?,
            val athleteId: Int,
            val activity_id: Int
        ) {

        }

        // Health Vitals
        data class HealthVitalsBody(val data: HealthVitalsRequest)
        data class HealthVitalsRequest(
            @SerializedName("WeightInKilograms") val WeightInKilograms: Int? = null,
            val height: Int? = null,
            val gender: String? = null,
            @SerializedName("date_of_birth") val date_of_birth: String? = null,
            @SerializedName("activity_level") val activity_level: String? = null,
            @SerializedName("weight_loss_goal") val weight_loss_goal: Int? = null,
            @SerializedName("stepGoal") val stepGoal: Int? = null,
            @SerializedName("waterGoal") val waterGoal: Float? = null,
            @SerializedName("calorieGoal") val calorieGoal: Int? = null,
            @SerializedName("weight_loss_strategy") val weight_loss_strategy: String? = null,
            @SerializedName("users_permissions_user") val users_permissions_user: UserId? = null
        )
        data class HealthVitalsResponse(val data: HealthVitalsEntry)
        data class HealthVitalsListResponse(val data: List<HealthVitalsEntry>)
        data class HealthVitalsEntry(
            @SerializedName("documentId") val documentId: String,
            @SerializedName("WeightInKilograms") val WeightInKilograms: Int?,
            val height: Int?,
            val gender: String?,
            @SerializedName("date_of_birth") val date_of_birth: String?,
            @SerializedName("activity_level") val activity_level: String?,
            @SerializedName("weight_loss_goal") val weight_loss_goal: Int?,
            @SerializedName("stepGoal") val stepGoal: Int?,
            @SerializedName("waterGoal") val waterGoal: Float?,
            @SerializedName("calorieGoal") val calorieGoal: Int?,
            @SerializedName("weight_loss_strategy") val weight_loss_strategy: String?
        )

        // User Profile
        data class UserProfileBody(val data: UserProfileRequest)
        data class UserProfileRequest(
            val username: String? = null,
            @SerializedName("firstName") val firstName: String? = null,
            @SerializedName("lastName") val lastName: String? = null,
            val email: String? = null,
            val mobile: Long? = null,
            @SerializedName("notificationsEnabled") val notificationsEnabled: Boolean? = null,
            @SerializedName("maxGreetingsEnabled") val maxGreetingsEnabled: Boolean? = null
        )
        data class UserProfileResponse(
            val id: String,
            val username: String,
            @SerializedName("firstName") val firstName: String?,
            @SerializedName("lastName") val lastName: String?,
            val email: String,
            val mobile: Long?,
            @SerializedName("notificationsEnabled") val notificationsEnabled: Boolean?,
            @SerializedName("maxGreetingsEnabled") val maxGreetingsEnabled: Boolean?
        )

        // Diet and Meals
        data class CustomMealRequestBody(val data: CustomMealRequest)
        data class CustomMealResponse(val data: Map<String, Any>)
        data class MealGoalBody(val data: MealGoalRequest)
        data class MealGoalResponse(val data: Map<String, Any>)
        data class DietPlanBody(val data: DietPlanRequest)
        data class DietPlanListResponse(val data: List<DietPlanEntry>)
        data class DietPlanResponse(val data: DietPlanEntry)
        data class DietPlanEntry(
            @SerializedName("documentId") val documentId: String,
            @SerializedName("plan_id") val planId: String,
            @SerializedName("total_calories") val totalCalories: Int,
            @SerializedName("diet_preference") val dietPreference: String,
            @SerializedName("Active") val active: Boolean,
            @SerializedName("points_earned") val pointsEarned: Int,
            @SerializedName("diet_goal") val dietGoal: String,
            val meals: List<MealEntry>?
        )
        data class MealBody(val data: MealRequest)
        data class MealResponse(val data: MealEntry)
        data class MealEntry(
            @SerializedName("documentId") val documentId: String,
            val name: String,
            @SerializedName("meal_time") val mealTime: String,
            @SerializedName("base_portion") val basePortion: Int,
            @SerializedName("totalCalories") val totalCalories: Int,
            @SerializedName("meal_date") val mealDate: String,
            @SerializedName("diet_components") val dietComponents: List<DietComponentEntry>?
        )
        data class DietComponentListResponse(val data: List<DietComponentEntry>)
        data class DietComponentEntry(
            @SerializedName("documentId") val documentId: String,
            val name: String?,
            val calories: Int?,
            @SerializedName("food_type") val foodType: String?,
            val protein: String? = "0g",
            @SerializedName("carbohydrate") val carbs: String? = "0g",
            @SerializedName("total_fat") val fat: String? = "0g",
            val fiber: String? = "0g"
        )
        data class DietLogBody(val data: DietLogRequest)
        data class DietLogUpdateBody(val data: DietLogUpdateRequest)
        data class DietLogRequest(
            val date: String,
            @SerializedName("users_permissions_user") // Fix here
            val usersPermissionsUser: UserId,
            val meals: List<MealLogEntry>
        )
        data class DietLogUpdateRequest(
            val date: String,
            val meals: List<MealLogEntry>
        )
        data class DietLogListResponse(val data: List<DietLogEntry>)
        data class DietLogResponse(val data: DietLogEntry)
        data class DietLogEntry(
            @SerializedName("documentId") val documentId: String?,
            val date: String,
            val user: Any?,
            val meals: List<Map<String, Any>>?
        )
        data class MealLogEntry(
            val mealId: String,
            val components: List<ComponentLogEntry>
        )
        data class ComponentLogEntry(
            val componentId: String,
            val consumed: Boolean
        )
        data class FeedbackBody(val data: FeedbackRequest)
        data class FeedbackResponse(val data: Map<String, Any>)

        data class PackBody(val data: PackRequest)
        data class PackRequest(
            val name: String,
            val goal: Int,
            val gliders: List<UserId>,
            val captain: UserId
        )
        data class PackResponse(val data: PackEntry)
        data class PackListResponse(val data: List<PackEntry>)
        data class PackEntry(
            @SerializedName("id") val id: String,
            @SerializedName("name") val name: String,
            @SerializedName("goal") val goal: Int,
            @SerializedName("progress") val progress: Int,
            @SerializedName("gliders") val gliders: List<UserId>,
            @SerializedName("captain") val captain: UserId
        )

        data class PostBody(val data: PostRequest)
        data class PostRequest(
            val user: UserId,
            val pack: UserId, // Pack ID
            val type: String,
            val data: Map<String, Any>
        )
        data class PostResponse(val data: PostEntry)
        data class PostListResponse(val data: List<PostEntry>)
        data class PostEntry(
            @SerializedName("id") val id: String,
            @SerializedName("user") val user: UserId,
            @SerializedName("pack") val pack: UserId,
            @SerializedName("type") val type: String,
            @SerializedName("data") val data: Map<String, Any>,
            @SerializedName("createdAt") val createdAt: String
        )

        data class CheerBody(val data: CheerRequest)
        data class CheerRequest(
            val sender: UserId,
            val receiver: UserId,
            val message: String
        )
        data class CheerResponse(val data: CheerEntry)
        data class CheerListResponse(val data: List<CheerEntry>)
        data class CheerEntry(
            @SerializedName("id") val id: String,
            @SerializedName("sender") val sender: UserId,
            @SerializedName("receiver") val receiver: UserId,
            @SerializedName("message") val message: String,
            @SerializedName("createdAt") val createdAt: String
        )

        data class ChallengeBody(val data: ChallengeRequest)
        data class ChallengeRequest(
            val challenger: UserId,
            val challengee: UserId,
            val participants: Map<String, Any>?,
            val goal: Int,
            val status: String,
            val type: String
        )
        data class ChallengeResponse(val data: ChallengeEntry)
        data class ChallengeListResponse(val data: List<ChallengeEntry>)
        data class ChallengeEntry(
            @SerializedName("id") val id: String,
            @SerializedName("challenger") val challenger: UserId,
            @SerializedName("challengee") val challengee: UserId,
            @SerializedName("participants") val participants: Map<String, Any>?,
            @SerializedName("goal") val goal: Int,
            @SerializedName("status") val status: String,
            @SerializedName("type") val type: String
        )

        data class FriendBody(val data: FriendRequest)
        data class FriendRequest(
            val sender: UserId,
            val receiver: UserId?,
            val friendEmail: String,
            @SerializedName("friends_status") val friendsStatus: String,
            val inviteToken: String
        )

        data class FriendResponse(val data: FriendEntry)

        data class FriendListResponse(val data: List<FriendEntry>)

        data class FriendEntry(
            @SerializedName("id") val id: String,
            @SerializedName("friendEmail") val friendEmail: String,
            @SerializedName("friends_status") val friendsStatus: String,
            @SerializedName("inviteToken") val inviteToken: String,
            @SerializedName("createdAt") val createdAt: String,
            @SerializedName("sender") val sender: FriendSender?,
            @SerializedName("receiver") val receiver: FriendReceiver?
        )

        data class FriendSender(
            @SerializedName("data") val data: UserId?
        )

        data class FriendReceiver(
            @SerializedName("data") val data: UserId?
        )

        data class UserAttributes(
            @SerializedName("username") val username: String? = null // Add other fields if needed
        )

        data class CommentListResponse(val data: List<CommentEntry>)
        data class CommentEntry(
            @SerializedName("id") val id: String,
            @SerializedName("post") val post: UserId, // Post ID as UserId for simplicity
            @SerializedName("user") val user: UserId,
            @SerializedName("text") val text: String, // Changed from "content" to "text"
            @SerializedName("createdAt") val createdAt: String
        )

        data class StravaAuthResponse(
            val redirectUrl: String // URL to launch for OAuth
        )

        data class StravaTokenRequest(
            val code: String // OAuth code from Strava redirect
        )

        data class StravaTokenResponse(
            val access_token: String,
            val refresh_token: String,
            val expires_at: Long
        )

        data class StravaActivity(
            val activity_id: Int,  // Correctly referring to the activity ID
            val workoutId: Int,
            val startTime: String,  // ISO 8601 format
            val endTime: String,    // ISO 8601 format
            val distance: Float,
            val totalTime: Float,
            val calories: Float,
            val heartRateAverage: Int?,
            val heartRateMaximum: Int?,
            val heartRateMinimum: Int?,
            val route: String?,
            val athleteId: Int
        )



        data class DesiMessageResponse(
            val data: List<DesiMessage>,
            val meta: Meta
        )

        data class DesiMessage(
            val id: Int,
            @SerializedName("documentId") val documentId: String,
            @SerializedName("title") val title: String?,
            @SerializedName("yesterday_line") val yesterdayLine: String,
            @SerializedName("today_line") val todayLine: String,
            @SerializedName("badge") val badge: String?,
            @SerializedName("language_style") val languageStyle: String?,
            @SerializedName("is_premium") val isPremium: Boolean?,
            @SerializedName("createdAt") val createdAt: String?,
            @SerializedName("updatedAt") val updatedAt: String?,
            @SerializedName("publishedAt") val publishedAt: String?
        )

        data class Meta(
            val pagination: Pagination
        )

        data class Pagination(
            val page: Int,
            val pageSize: Int,
            val pageCount: Int,
            val total: Int
        )

        data class BadgeListResponse(val data: List<BadgeEntry>)
        data class BadgeEntry(
            val id: Int,
            val documentId: String,
            val name: String,
            val description: String,
            val createdAt: String?,
            val updatedAt: String?,
            val publishedAt: String?,
            val icon: Media? = null
        )

        data class Badge(
            val id: Int,
            val title: String,
            val description: String,
            val iconUrl: String? // Nullable for missing icons
        )

        data class Media(
            val id: Int,
            val documentId: String,
            val name: String,
            val alternativeText: String?,
            val caption: String?,
            val width: Int,
            val height: Int,
            val formats: MediaFormats?,
            val hash: String,
            val ext: String,
            val mime: String,
            val size: Float,
            val url: String,
            val previewUrl: String?,
            val provider: String,
            val provider_metadata: String?,
            val createdAt: String?,
            val updatedAt: String?
        )

        data class MediaFormats(
            val large: MediaFormat?,
            val medium: MediaFormat?,
            val small: MediaFormat?,
            val thumbnail: MediaFormat?
        )

        data class MediaFormat(
            val ext: String,
            val url: String,
            val hash: String,
            val mime: String,
            val name: String,
            val path: String?,
            val size: Float,
            val width: Int,
            val height: Int,
            val sizeInBytes: Int
        )

        data class MediaData(
            val id: Int,
            val attributes: MediaAttributes
        )

        data class MediaAttributes(
            val url: String
        )

    }