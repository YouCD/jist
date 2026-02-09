package dev.rcht.jist.llm

import dev.rcht.jist.llm.model.ChatMessage
import dev.rcht.jist.llm.model.LlmError
import dev.rcht.jist.llm.model.LlmResponse

sealed class LlmResult<out T> {
    data class Success<T>(val data: T) : LlmResult<T>()
    data class Error<T>(val error: LlmError) : LlmResult<T>()
}

interface LlmClient {
    /**
     * Send chat messages to LLM and get a response.
     * @param messages List of chat messages (user, system, assistant)
     * @param config Configuration for this request
     * @return LlmResult with response or error
     */
    suspend fun complete(
        messages: List<ChatMessage>,
        config: LlmRequestConfig
    ): LlmResult<LlmResponse>
}

data class LlmRequestConfig(
    val model: String,
    val maxTokens: Int = 1000,
    val temperature: Float = 0.7f,
    val systemPrompt: String? = null,
    val apiKey: String = "",
    val baseUrl: String = ""
)
