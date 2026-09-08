package dev.rcht.jist.llm.client

import dev.rcht.jist.llm.LlmClient
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.model.ChatMessage
import dev.rcht.jist.llm.model.ClaudeRequest
import dev.rcht.jist.llm.model.ClaudeResponse
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
 * Anthropic Claude API client
 */
class ClaudeClient(private val httpClient: OkHttpClient) : LlmClient {

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

            val apiResponse = json.decodeFromString<ClaudeResponse>(body)

            val textContent = apiResponse.content.firstOrNull { it.type == "text" }?.text
                ?: return LlmResult.Error(LlmError("No text content in response"))

            val llmResponse = LlmResponse(
                text = textContent,
                promptTokens = apiResponse.usage?.input_tokens ?: 0,
                completionTokens = apiResponse.usage?.output_tokens ?: 0,
                totalTokens = (apiResponse.usage?.input_tokens ?: 0) +
                        (apiResponse.usage?.output_tokens ?: 0),
                model = apiResponse.model,
                finishReason = apiResponse.stop_reason ?: "end_turn"
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
        // Extract system prompt if present
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content

        // Filter out system messages for Claude API (they go in system field)
        val apiMessages = messages.filter { it.role != "system" }

        val claudeRequest = ClaudeRequest(
            model = config.model,
            max_tokens = config.maxTokens,
            system = systemPrompt ?: config.systemPrompt,
            messages = apiMessages,
            temperature = config.temperature
        )

        val body = json.encodeToString(claudeRequest)
            .toRequestBody("application/json".toMediaType())

        val url = "${LlmClientFactory.normalizeBaseUrl(config.baseUrl)}/v1/messages"

        return Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("anthropic-version", "2023-06-01")
            .build()
    }
}
