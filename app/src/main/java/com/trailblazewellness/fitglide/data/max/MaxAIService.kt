package com.trailblazewellness.fitglide.data.max

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit


object MaxAiService {
    private const val TAG = "MaxAiService"
    private const val OLLAMA_BASE_URL = "https://max.fitglide.in"
    private const val MODEL_NAME = "phi" // You can swap to "mistral" when ready

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var tts: TextToSpeech? = null

    fun initTTS(context: Context, onReady: () -> Unit = {}) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("en", "IN")
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(0.95f)
                    Log.d(TAG, "TTS initialized successfully 🎤")
                    onReady()
                } else {
                    Log.e(TAG, "TTS initialization failed 😓")
                }
            }
        } else {
            onReady()
        }
    }

    fun speak(text: String) {
        if (tts == null) {
            Log.w(TAG, "TTS engine not ready.")
            return
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MAX_AI_SPEAK")
        }
        Log.d(TAG, "🔊 Speaking text: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "MAX_AI_SPEAK")

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS done: $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
            }
        })
    }

    fun shutdownTTS() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        Log.d(TAG, "TTS shutdown complete 📴")
    }

    suspend fun fetchMaxGreeting(prompt: String): String {
        return try {
            val json = JSONObject().apply {
                put("model", MODEL_NAME)
                put("prompt", prompt)
                put("stream", false)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$OLLAMA_BASE_URL/api/generate")
                .post(body)
                .build()

            Log.d("DesiMaxDebug", "🌐 Sending request to Ollama with prompt:\n$prompt")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("DesiMaxDebug", "❌ Ollama API call failed: ${'$'}{response.code}")
                    return "Max is chilling today 😅 Try again later!"
                }

                val result = JSONObject(response.body?.string() ?: "{}")
                val responseText = result.optString("response", "")
                Log.d("DesiMaxDebug", "✅ Received from Ollama API: $responseText")

                return responseText
            }

        } catch (e: Exception) {
            Log.e("DesiMaxDebug", "🚨 Exception during Ollama API call", e)
            return "Oops! Max forgot his protein shake. Try again later! 😅"
        }
    }
}
