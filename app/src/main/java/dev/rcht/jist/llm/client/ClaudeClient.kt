package dev.rcht.jist.llm.client

import dev.rcht.jist.llm.LlmClient
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.mergeHeaders
import dev.rcht.jist.llm.model.ChatMessage
import dev.rcht.jist.llm.model.ClaudeRequest
import dev.rcht.jist.llm.model.ClaudeResponse
import dev.rcht.jist.llm.model.LlmError
import dev.rcht.jist.llm.model.LlmResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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

    // The OkHttp call below blocks; run it on an IO thread so suspend
    // callers (e.g. the single-threaded notification listener) are never blocked.
    // Note: withContext's block is `crossinline`, so non-local `return`s are
    // prohibited inside the lambda — the returning logic lives in doComplete().
    override suspend fun complete(
        messages: List<ChatMessage>,
        config: LlmRequestConfig
    ): LlmResult<LlmResponse> = withContext(Dispatchers.IO) {
        doComplete(messages, config)
    }

    private fun doComplete(
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

    /** "data:image/jpeg;base64,XXX" -> ("image/jpeg", "XXX") */
    private fun parseDataUrl(url: String): Pair<String, String> {
        val comma = url.indexOf(',')
        if (url.startsWith("data:") && comma > 0) {
            val meta = url.substring(5, comma)
            val mediaType = meta.substringBefore(";").ifBlank { "image/jpeg" }
            return mediaType to url.substring(comma + 1)
        }
        return "image/jpeg" to url
    }

    private fun buildRequest(
        messages: List<ChatMessage>,
        config: LlmRequestConfig
    ): Request {
        // Extract system prompt if present
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content

        // Filter out system messages for Claude API (they go in system field)
        val apiMessages = messages.filter { it.role != "system" }

        val hasImages = apiMessages.any { it.hasImages }
        val bodyText = if (hasImages) {
            // Anthropic vision: content is an array of image/text blocks.
            buildJsonObject {
                put("model", config.model)
                put("max_tokens", config.maxTokens)
                val sys = systemPrompt ?: config.systemPrompt
                if (!sys.isNullOrBlank()) put("system", sys)
                putJsonArray("messages") {
                    for (m in apiMessages) addJsonObject {
                        put("role", m.role)
                        if (m.hasImages) {
                            putJsonArray("content") {
                                for (url in m.images) addJsonObject {
                                    put("type", "image")
                                    val (mediaType, b64) = parseDataUrl(url)
                                    putJsonObject("source") {
                                        put("type", "base64")
                                        put("media_type", mediaType)
                                        put("data", b64)
                                    }
                                }
                                if (m.content.isNotBlank()) addJsonObject {
                                    put("type", "text"); put("text", m.content)
                                }
                            }
                        } else {
                            put("content", m.content)
                        }
                    }
                }
            }.toString()
        } else {
            json.encodeToString(
                ClaudeRequest(
                    model = config.model,
                    max_tokens = config.maxTokens,
                    system = systemPrompt ?: config.systemPrompt,
                    messages = apiMessages,
                    temperature = config.temperature
                )
            )
        }

        val body = bodyText.toRequestBody("application/json".toMediaType())

        val url = "${LlmClientFactory.normalizeBaseUrl(config.baseUrl)}/v1/messages"

        val headers = mergeHeaders(
            mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "Content-Type" to "application/json",
                "anthropic-version" to "2023-06-01"
            ),
            config.extraHeaders
        ) // user headers override built-ins (case-insensitive)

        val builder = Request.Builder()
            .url(url)
            .post(body)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return builder.build()
    }
}
