package net.xian.xianwalletapp.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.xian.xianwalletapp.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal OpenRouter client for chat completions.
 * Uses OkHttp directly to avoid adding new Retrofit interfaces.
 */
object OpenRouterService {
    private const val TAG = "OpenRouterService"
    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    // Reuse a single client with reasonable timeouts
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Calls OpenRouter Chat Completions with the provided system + user content.
     * Returns the assistant content string or throws an exception.
     */
    suspend fun chatCompletion(
        systemPrompt: String,
        userPrompt: String,
        model: String = "openrouter/auto",
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENROUTER_API_KEY ?: ""
        if (apiKey.isBlank()) {
            throw IllegalStateException("Missing OpenRouter API key. Provide OPENROUTER_API_KEY in gradle.properties or environment.")
        }

        val payload = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            ),
            temperature = temperature,
            topP = topP
        )
        val bodyJson = gson.toJson(payload)
        val req = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            // Optional but recommended by OpenRouter
            .addHeader("HTTP-Referer", "https://xianwallet.app")
            .addHeader("X-Title", "Xian Wallet")
            .post(bodyJson.toRequestBody(JSON))
            .build()

        client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e(TAG, "OpenRouter error ${resp.code}: ${resp.message}\n$respBody")
                throw RuntimeException("OpenRouter HTTP ${resp.code}: ${resp.message}")
            }
            val parsed = gson.fromJson(respBody, ChatResponse::class.java)
            val content = parsed.choices.firstOrNull()?.message?.content?.trim()
            if (content.isNullOrBlank()) {
                Log.e(TAG, "OpenRouter response missing content: $respBody")
                throw RuntimeException("OpenRouter response empty")
            }
            content
        }
    }

    // --- DTOs ---

    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Float? = null,
        @SerializedName("top_p") val topP: Float? = null
    )

    data class ChatMessage(
        val role: String,
        val content: String
    )

    data class ChatResponse(
        val id: String? = null,
        val choices: List<Choice> = emptyList()
    ) {
        data class Choice(
            val index: Int = 0,
            val message: ChatMessage = ChatMessage("", "")
        )
    }
}