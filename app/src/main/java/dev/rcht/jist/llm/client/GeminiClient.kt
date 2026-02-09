package dev.rcht.jist.llm.client

import dev.rcht.jist.llm.LlmClient
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.model.ChatMessage
import dev.rcht.jist.llm.model.GeminiRequest
import dev.rcht.jist.llm.model.GeminiResponse
import dev.rcht.jist.llm.model.LlmError
import dev.rcht.jist.llm.model.LlmResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Google Gemini API client
 */
class GeminiClient(private val httpClient: OkHttpClient) : LlmClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun complete(
        messages: List<ChatMessage>,
        config: LlmRequestConfig
    ): LlmResult<LlmResponse> {
        return try {
            // Validate API key
            if (config.apiKey.isBlank()) {
                return LlmResult.Error(LlmError("API key is empty. Please configure it in Settings."))
            }
            
            val request = buildRequest(messages, config)
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return LlmResult.Error(
                    LlmError(
                        message = "${response.code}: ${response.message}\n$errorBody",
                        statusCode = response.code
                    )
                )
            }

            val body = response.body?.string()
                ?: return LlmResult.Error(LlmError("Empty response body"))

            val apiResponse = json.decodeFromString<GeminiResponse>(body)
            val firstCandidate = apiResponse.candidates.firstOrNull()
                ?: return LlmResult.Error(LlmError("No candidates in response"))

            val textContent = firstCandidate.content.parts.firstOrNull()?.text
                ?: return LlmResult.Error(LlmError("No text content in response"))

            val llmResponse = LlmResponse(
                text = textContent,
                promptTokens = apiResponse.usageMetadata?.promptTokenCount ?: 0,
                completionTokens = apiResponse.usageMetadata?.candidatesTokenCount ?: 0,
                totalTokens = apiResponse.usageMetadata?.totalTokenCount ?: 0,
                model = config.model,
                finishReason = firstCandidate.finishReason ?: "STOP"
            )

            LlmResult.Success(llmResponse)
        } catch (e: IOException) {
            LlmResult.Error(LlmError("Network error: ${e.message ?: e.toString()}"))
        } catch (e: Exception) {
            LlmResult.Error(LlmError("Parse error: ${e.message ?: e.toString()}"))
        }
    }

    private fun buildRequest(
        messages: List<ChatMessage>,
        config: LlmRequestConfig
    ): Request {
        // Convert chat messages to Gemini format
        val contents = messages.mapNotNull { msg ->
            if (msg.role != "system") {
                GeminiRequest.GeminiContent(
                    parts = listOf(GeminiRequest.GeminiContent.Part(msg.content)),
                    role = when (msg.role) {
                        "user" -> "user"
                        "assistant" -> "model"
                        else -> null
                    }
                )
            } else null
        }

        // Extract system prompt if present
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.let {
            GeminiRequest.GeminiContent(
                parts = listOf(GeminiRequest.GeminiContent.Part(it.content))
            )
        }

        val generationConfig = GeminiRequest.GenerationConfig(
            maxOutputTokens = config.maxTokens,
            temperature = config.temperature
        )

        val geminiRequest = GeminiRequest(
            contents = contents,
            systemInstruction = systemPrompt,
            generationConfig = generationConfig
        )

        val body = json.encodeToString(geminiRequest)
            .toRequestBody("application/json".toMediaType())

        val url = "${config.baseUrl}/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"

        return Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
    }
}
