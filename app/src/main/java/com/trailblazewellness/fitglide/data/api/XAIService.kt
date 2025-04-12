package com.trailblazewellness.fitglide.data.api

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface XAIService {
    @Headers("Authorization: Bearer xai-wrQXpUp0ve8E82ULlKbVPB5PJkfvIO1CG09oW3PqjibAJvy2pcLpYBzb3FnaNKvjQzt1Ssve2FpJgJVV")
    @POST("chat/completions")
    suspend fun getResponse(@Body request: XAIRequest): XAIResponse
}

data class XAIRequest(val prompt: String, val max_tokens: Int = 50)
data class XAIResponse(val choices: List<Choice>)
data class Choice(val text: String)