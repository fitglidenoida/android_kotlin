package com.trailblazewellness.fitglide.presentation.successstory

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

data class WeightLossStory(
    val storyId: String,
    val thenWeight: Float,
    val nowWeight: Float,
    val weightLost: Float,
    val storyText: String,
    val userName: String,
    val userId: String,
    val likes: Int,
    val visibility: String,
    val beforeImage: String? = null, // URL from Strapi
    val afterImage: String? = null
)

class SuccessStoryViewModel(
    private val strapiRepository: StrapiRepository,
    private val authRepository: AuthRepository,
    private val currentUserId: String,
    private val authToken: String,
    private val context: Context // Add context for content resolver
) : ViewModel() {

    private val _stories = MutableStateFlow<List<WeightLossStory>>(emptyList())
    val stories: StateFlow<List<WeightLossStory>> = _stories.asStateFlow()

    private val _weightHistory = MutableStateFlow<List<WeightLossStory>>(emptyList())
    val weightHistory: StateFlow<List<WeightLossStory>> = _weightHistory.asStateFlow()

    init {
        fetchStories()
        fetchWeightHistory()
    }

    fun addStory(
        userName: String,
        weightLost: String,
        timeTaken: String,
        beforeImageUri: String?,
        afterImageUri: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val weightLostValue = weightLost.replace("[^0-9.]".toRegex(), "").toFloatOrNull() ?: 0f
                val nowWeight = 70f // TODO: Fetch from strapiRepository.getHealthVitals
                val thenWeight = nowWeight + weightLostValue
                val storyText = "$userName lost $weightLost in $timeTaken."

                var beforeImageId: String? = null
                var afterImageId: String? = null

                if (beforeImageUri != null) {
                    val beforeFile = uriToFile(Uri.parse(beforeImageUri))
                    val response = strapiRepository.uploadFile(beforeFile, authToken)
                    if (response.isSuccessful) {
                        beforeImageId = response.body()?.firstOrNull()?.id?.toString()
                    } else {
                        throw Exception("Before image upload failed: ${response.errorBody()?.string()}")
                    }
                }
                if (afterImageUri != null) {
                    val afterFile = uriToFile(Uri.parse(afterImageUri))
                    val response = strapiRepository.uploadFile(afterFile, authToken)
                    if (response.isSuccessful) {
                        afterImageId = response.body()?.firstOrNull()?.id?.toString()
                    } else {
                        throw Exception("After image upload failed: ${response.errorBody()?.string()}")
                    }
                }

                submitStory(
                    thenWeight = thenWeight,
                    nowWeight = nowWeight,
                    weightLost = weightLostValue,
                    storyText = storyText,
                    beforeImageId = beforeImageId,
                    afterImageId = afterImageId,
                    onSuccess = {
                        fetchStories()
                        onSuccess()
                    },
                    onError = onError
                )
            } catch (e: Exception) {
                val errorMsg = "Error creating story: ${e.message}"
                Log.e("SuccessStoryViewModel", errorMsg)
                onError(errorMsg)
            }
        }
    }

    private suspend fun uriToFile(uri: Uri): File {
        return withContext(Dispatchers.IO) {
            val file = File.createTempFile("image", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Failed to read image from URI")
            file
        }
    }

    fun fetchStories() {
        viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        strapiRepository.getWeightLossStories(authToken)
                    }
                }
                if (response?.isSuccessful == true) {
                    val storyEntries = response.body()?.data ?: emptyList()
                    val mappedStories = storyEntries.mapNotNull { entry ->
                        if (entry.storyText.isNullOrEmpty()) return@mapNotNull null
                        val userName = entry.usersPermissionsUser?.firstName ?: return@mapNotNull null
                        val userId = entry.usersPermissionsUser?.id?.toString() ?: return@mapNotNull null
                        val visibility = entry.visibility ?: "Everyone"
                        val beforeImage = entry.beforeImage?.attributes?.url?.let { "https://admin.fitglide.in$it" }
                        val afterImage = entry.afterImage?.attributes?.url?.let { "https://admin.fitglide.in$it" }
                        if (visibility == "Everyone" || userId == currentUserId) {
                            WeightLossStory(
                                storyId = entry.storyId ?: "unknown_${System.currentTimeMillis()}",
                                thenWeight = entry.thenWeight?.toFloat() ?: 0f,
                                nowWeight = entry.nowWeight?.toFloat() ?: 0f,
                                weightLost = entry.weightLost?.toFloat() ?: 0f,
                                storyText = entry.storyText,
                                userName = userName,
                                userId = userId,
                                likes = entry.likes ?: 0,
                                visibility = visibility,
                                beforeImage = beforeImage,
                                afterImage = afterImage
                            )
                        } else if (visibility == "Friends") {
                            val isFriend = checkIfFriend(currentUserId, userId)
                            if (isFriend) {
                                WeightLossStory(
                                    storyId = entry.storyId ?: "unknown_${System.currentTimeMillis()}",
                                    thenWeight = entry.thenWeight?.toFloat() ?: 0f,
                                    nowWeight = entry.nowWeight?.toFloat() ?: 0f,
                                    weightLost = entry.weightLost?.toFloat() ?: 0f,
                                    storyText = entry.storyText,
                                    userName = userName,
                                    userId = userId,
                                    likes = entry.likes ?: 0,
                                    visibility = visibility,
                                    beforeImage = beforeImage,
                                    afterImage = afterImage
                                )
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                    _stories.value = mappedStories
                    Log.d("SuccessStoryViewModel", "Fetched ${mappedStories.size} weight loss stories")
                } else {
                    Log.e("SuccessStoryViewModel", "Failed to fetch stories: ${response?.code()} - ${response?.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("SuccessStoryViewModel", "Error fetching stories: ${e.message}", e)
            }
        }
    }

    fun fetchWeightHistory() {
        viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        strapiRepository.getWeightLossStoriesForUser(currentUserId, authToken)
                    }
                }
                if (response?.isSuccessful == true) {
                    val entries = response.body()?.data ?: emptyList()
                    val mappedHistory = entries.mapNotNull { entry ->
                        val userName = entry.usersPermissionsUser?.firstName ?: return@mapNotNull null
                        val userId = entry.usersPermissionsUser?.id?.toString() ?: return@mapNotNull null
                        val beforeImage = entry.beforeImage?.attributes?.url?.let { "https://admin.fitglide.in$it" }
                        val afterImage = entry.afterImage?.attributes?.url?.let { "https://admin.fitglide.in$it" }
                        WeightLossStory(
                            storyId = entry.storyId ?: "unknown_${System.currentTimeMillis()}",
                            thenWeight = entry.thenWeight?.toFloat() ?: 0f,
                            nowWeight = entry.nowWeight?.toFloat() ?: 0f,
                            weightLost = entry.weightLost?.toFloat() ?: 0f,
                            storyText = entry.storyText ?: "",
                            userName = userName,
                            userId = userId,
                            likes = entry.likes ?: 0,
                            visibility = entry.visibility ?: "Everyone",
                            beforeImage = beforeImage,
                            afterImage = afterImage
                        )
                    }.sortedByDescending { it.storyId }
                    _weightHistory.value = mappedHistory
                    Log.d("SuccessStoryViewModel", "Fetched ${mappedHistory.size} weight history entries for user $currentUserId")
                } else {
                    Log.e("SuccessStoryViewModel", "Failed to fetch weight history: ${response?.code()} - ${response?.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("SuccessStoryViewModel", "Error fetching weight history: ${e.message}", e)
            }
        }
    }

    fun submitWeightUpdate(
        thenWeight: Float,
        nowWeight: Float,
        weightLost: Float,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val request = StrapiApi.WeightLossStoryRequest(
                    storyId = "weight_log_${currentUserId}_${System.currentTimeMillis()}",
                    thenWeight = thenWeight.toDouble(),
                    nowWeight = nowWeight.toDouble(),
                    weightLost = weightLost.toDouble(),
                    storyText = "",
                    usersPermissionsUser = StrapiApi.UserId(currentUserId),
                    visibility = "Everyone"
                )
                val response = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        strapiRepository.createWeightLossStory(request, authToken)
                    }
                }
                if (response?.isSuccessful == true) {
                    Log.d("SuccessStoryViewModel", "Successfully submitted weight update for user $currentUserId")
                    fetchWeightHistory()
                    onSuccess()
                } else {
                    val errorMsg = "Failed to submit weight update: ${response?.code()} - ${response?.errorBody()?.string()}"
                    Log.e("SuccessStoryViewModel", errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Error submitting weight update: ${e.message}"
                Log.e("SuccessStoryViewModel", errorMsg, e)
                onError(errorMsg)
            }
        }
    }

    fun submitStory(
        thenWeight: Float,
        nowWeight: Float,
        weightLost: Float,
        storyText: String,
        beforeImageId: String?,
        afterImageId: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val request = StrapiApi.WeightLossStoryRequest(
                    storyId = "story_${currentUserId}_${System.currentTimeMillis()}",
                    thenWeight = thenWeight.toDouble(),
                    nowWeight = nowWeight.toDouble(),
                    weightLost = weightLost.toDouble(),
                    storyText = storyText,
                    usersPermissionsUser = StrapiApi.UserId(currentUserId),
                    visibility = "Everyone",
                    beforeImage = beforeImageId?.let { StrapiApi.MediaId(it) },
                    afterImage = afterImageId?.let { StrapiApi.MediaId(it) }
                )
                val response = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        strapiRepository.createWeightLossStory(request, authToken)
                    }
                }
                if (response?.isSuccessful == true) {
                    Log.d("SuccessStoryViewModel", "Successfully submitted story for user $currentUserId")
                    onSuccess()
                } else {
                    val errorMsg = "Failed to submit story: ${response?.code()} - ${response?.errorBody()?.string()}"
                    Log.e("SuccessStoryViewModel", errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Error submitting story: ${e.message}"
                Log.e("SuccessStoryViewModel", errorMsg, e)
                onError(errorMsg)
            }
        }
    }

    fun updateStoryVisibility(
        storyId: String,
        visibility: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        strapiRepository.updateWeightLossStoryVisibility(storyId, visibility, authToken)
                    }
                }
                if (response?.isSuccessful == true) {
                    Log.d("SuccessStoryViewModel", "Successfully updated visibility for story $storyId to $visibility")
                    fetchStories()
                    onSuccess()
                } else {
                    val errorMsg = "Failed to update visibility: ${response?.code()} - ${response?.errorBody()?.string()}"
                    Log.e("SuccessStoryViewModel", errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Error updating visibility: ${e.message}"
                Log.e("SuccessStoryViewModel", errorMsg, e)
                onError(errorMsg)
            }
        }
    }

    private suspend fun checkIfFriend(currentUserId: String, creatorUserId: String): Boolean {
        try {
            val response = withTimeoutOrNull(10000L) {
                withContext(Dispatchers.IO) {
                    val filters = mapOf(
                        "filters[sender][id][\$eq]" to creatorUserId,
                        "filters[friends_status][\$eq]" to "accepted"
                    )
                    strapiRepository.getFriends(filters, authToken)
                }
            }
            if (response?.isSuccessful == true) {
                val friendsList = response.body()?.data ?: emptyList()
                val isFriend = friendsList.any { friend ->
                    friend.receiver?.data?.id == currentUserId
                }
                Log.d("SuccessStoryViewModel", "Checked if $currentUserId is a friend of $creatorUserId: $isFriend")
                return isFriend
            } else {
                Log.e("SuccessStoryViewModel", "Failed to fetch friends list: ${response?.code()} - ${response?.errorBody()?.string()}")
                return false
            }
        } catch (e: Exception) {
            Log.e("SuccessStoryViewModel", "Error checking if friend: ${e.message}", e)
            return false
        }
    }
}