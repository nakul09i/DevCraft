package com.devcraft.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class AiStatus {
    ONLINE,
    OFFLINE,
    UNAVAILABLE
}

data class AiResponse<T>(
    val isSuccess: Boolean,
    val data: T? = null,
    val errorMessage: String? = null,
    val status: AiStatus = AiStatus.ONLINE
)

/**
 * Secure HTTP client for Anthropic Claude.
 *
 * CRITICAL SECURITY & ARCHITECTURE RULES:
 *  1. NEVER put the Anthropic API key directly inside the Android APK or Kotlin source code.
 *  2. All requests are proxied via the secure backend endpoint.
 *  3. Network failure or 4xx/5xx responses fall back gracefully to offline deterministic logic.
 */
class ClaudeApiClient(
    private val proxyUrl: String = PROXY_URL,
    private val client: OkHttpClient = defaultClient()
) {
    var modelName: String = CLAUDE_MODEL
    var requestsTodayCount: Int = 0
        private set

    suspend fun queryClaude(
        prompt: String,
        systemPrompt: String = "You are DevCraft AI, an intelligent order management assistant."
    ): AiResponse<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JsonObject().apply {
                addProperty("model", modelName)
                addProperty("system", systemPrompt)
                addProperty("prompt", prompt)
                addProperty("max_tokens", 1024)
            }

            val request = Request.Builder()
                .url(proxyUrl)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val status = when (response.code) {
                        401, 403 -> AiStatus.UNAVAILABLE
                        429 -> AiStatus.UNAVAILABLE
                        else -> AiStatus.OFFLINE
                    }
                    return@withContext AiResponse(
                        isSuccess = false,
                        errorMessage = "AI service HTTP ${response.code}",
                        status = status
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext AiResponse(isSuccess = false, errorMessage = "Empty response")
                }

                val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                val content = json?.get("content")?.asString ?: json?.get("text")?.asString

                requestsTodayCount++

                if (content != null) {
                    AiResponse(isSuccess = true, data = content, status = AiStatus.ONLINE)
                } else {
                    AiResponse(isSuccess = false, errorMessage = "Malformed AI response shape")
                }
            }
        } catch (e: IOException) {
            AiResponse(isSuccess = false, errorMessage = "Network offline - using local deterministic engine", status = AiStatus.OFFLINE)
        } catch (e: Exception) {
            AiResponse(isSuccess = false, errorMessage = e.message ?: "AI request failed", status = AiStatus.UNAVAILABLE)
        }
    }

    companion object {
        const val CLAUDE_MODEL = "claude-3-7-sonnet-20250219"
        const val PROXY_URL = "https://devcraft-backend.vercel.app/api/ai/claude"

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
