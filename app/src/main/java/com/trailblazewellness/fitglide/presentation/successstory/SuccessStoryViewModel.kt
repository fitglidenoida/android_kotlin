package com.trailblazewellness.fitglide.presentation.successstory

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

data class WeightLossStory(
    val storyId: String,
    val thenWeight: Float,
    val nowWeight: Float,
    val weightLost: Float,
    val storyText: String,
    val userName: String, // First name from users_permissions_user
    val userId: String, // ID of the user who created the story
    val likes: Int,
    val visibility: String // "Everyone" or "Friends"
)

class SuccessStoryViewModel(
    private val strapiRepository: StrapiRepository,
    private val authRepository: AuthRepository,
    private val currentUserId: String,
    private val authToken: String
) : ViewModel() {

    private val _stories = MutableStateFlow<List<WeightLossStory>>(emptyList())
    val stories: StateFlow<List<WeightLossStory>> = _stories.asStateFlow()

    private val _weightHistory = MutableStateFlow<List<WeightLossStory>>(emptyList())
    val weightHistory: StateFlow<List<WeightLossStory>> = _weightHistory.asStateFlow()

    init {
        fetchStories()
        fetchWeightHistory()
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
                        // Only include stories with non-empty storyText
                        if (entry.storyText.isNullOrEmpty()) return@mapNotNull null
                        val userName = entry.usersPermissionsUser?.firstName ?: return@mapNotNull null
                        val userId = entry.usersPermissionsUser?.id?.toString() ?: return@mapNotNull null
                        val visibility = entry.visibility ?: "Everyone"
                        // Apply visibility logic
                        if (visibility == "Everyone" || userId == currentUserId) {
                            // Show stories with visibility "Everyone" or stories created by the current user
                            WeightLossStory(
                                storyId = entry.storyId ?: "unknown_${System.currentTimeMillis()}",
                                thenWeight = entry.thenWeight?.toFloat() ?: 0f,
                                nowWeight = entry.nowWeight?.toFloat() ?: 0f,
                                weightLost = entry.weightLost?.toFloat() ?: 0f,
                                storyText = entry.storyText,
                                userName = userName,
                                userId = userId,
                                likes = entry.likes ?: 0,
                                visibility = visibility
                            )
                        } else if (visibility == "Friends") {
                            // Check if the current user is in the creator's friends list
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
                                    visibility = visibility
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
                        // Fetch weight history for the current user only
                        strapiRepository.getWeightLossStoriesForUser(currentUserId, authToken)
                    }
                }
                if (response?.isSuccessful == true) {
                    val entries = response.body()?.data ?: emptyList()
                    val mappedHistory = entries.mapNotNull { entry ->
                        val userName = entry.usersPermissionsUser?.firstName ?: return@mapNotNull null
                        val userId = entry.usersPermissionsUser?.id?.toString() ?: return@mapNotNull null
                        WeightLossStory(
                            storyId = entry.storyId ?: "unknown_${System.currentTimeMillis()}",
                            thenWeight = entry.thenWeight?.toFloat() ?: 0f,
                            nowWeight = entry.nowWeight?.toFloat() ?: 0f,
                            weightLost = entry.weightLost?.toFloat() ?: 0f,
                            storyText = entry.storyText ?: "",
                            userName = userName,
                            userId = userId,
                            likes = entry.likes ?: 0,
                            visibility = entry.visibility ?: "Everyone"
                        )
                    }.sortedByDescending { it.storyId } // Sort by creation date (newest first)
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
                    storyText = "", // Empty for weight logs
                    usersPermissionsUser = StrapiApi.UserId(currentUserId),
                    visibility = "Everyone" // Default visibility for weight logs
                )
                val response = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        strapiRepository.createWeightLossStory(request, authToken)
                    }
                }
                if (response?.isSuccessful == true) {
                    Log.d("SuccessStoryViewModel", "Successfully submitted weight update for user $currentUserId")
                    fetchWeightHistory() // Refresh weight history
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
                    visibility = "Everyone" // Default visibility for stories
                )
                val response = withTimeoutOrNull(10000L) {
                    withContext(Dispatchers.IO) {
                        strapiRepository.createWeightLossStory(request, authToken)
                    }
                }
                if (response?.isSuccessful == true) {
                    Log.d("SuccessStoryViewModel", "Successfully submitted story for user $currentUserId")
                    fetchStories() // Refresh the story list
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
                    fetchStories() // Refresh the story list
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

    // Function to check if the current user is a friend of the story creator
    private suspend fun checkIfFriend(currentUserId: String, creatorUserId: String): Boolean {
        try {
            // Fetch the creator's friends list using StrapiRepository
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
                // Check if the current user is in the creator's friends list
                val isFriend = friendsList.any { friend ->
                    friend.receiver?.data?.id == currentUserId
                }
                Log.d("SuccessStoryViewModel", "Checked if $currentUserId is a friend of $creatorUserId: $isFriend")
                return isFriend
            } else {
                Log.e("SuccessStoryViewModel", "Failed to fetch friends list: ${response?.code()} - ${response?.errorBody()?.string()}")
                return false // Default to false if we can't fetch the friends list
            }
        } catch (e: Exception) {
            Log.e("SuccessStoryViewModel", "Error checking if friend: ${e.message}", e)
            return false // Default to false on error
        }
    }
}