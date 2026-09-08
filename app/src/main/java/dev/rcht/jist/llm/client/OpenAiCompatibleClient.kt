package dev.rcht.jist.llm.client

import dev.rcht.jist.llm.LlmClient
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.model.ChatMessage
import dev.rcht.jist.llm.model.LlmError
import dev.rcht.jist.llm.model.LlmResponse
import dev.rcht.jist.llm.model.OpenAiChatResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * OpenAI-compatible client supporting:
 * - OpenAI (api.openai.com)
 * - OpenRouter (openrouter.ai/api)
 * - Any OpenAI-compatible endpoint
 */
class OpenAiCompatibleClient(private val httpClient: OkHttpClient) : LlmClient {

    @Serializable
    data class ChatTemplateKwargs(
        val enable_thinking: Boolean = false
    )

    @Serializable
    data class OpenAiRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val max_completion_tokens: Int = 1000,
        val temperature: Float = 0.7f,
        val chat_template_kwargs: ChatTemplateKwargs = ChatTemplateKwargs()
    )

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

            val apiResponse = json.decodeFromString<OpenAiChatResponse>(body)
            val firstChoice = apiResponse.choices.firstOrNull()
                ?: return LlmResult.Error(LlmError("No choices in response"))

            val llmResponse = LlmResponse(
                text = firstChoice.message.content,
                promptTokens = apiResponse.usage?.prompt_tokens ?: 0,
                completionTokens = apiResponse.usage?.completion_tokens ?: 0,
                totalTokens = apiResponse.usage?.total_tokens ?: 0,
                model = apiResponse.model,
                finishReason = firstChoice.finish_reason ?: "stop"
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
        val apiRequest = OpenAiRequest(
            model = config.model,
            messages = messages,
            max_completion_tokens = config.maxTokens,
            temperature = config.temperature
        )

        val body = json.encodeToString(apiRequest)
            .toRequestBody("application/json".toMediaType())

        val url = "${LlmClientFactory.normalizeBaseUrl(config.baseUrl)}/v1/chat/completions"

        return Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
    }
}
