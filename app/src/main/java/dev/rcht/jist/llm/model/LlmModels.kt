package dev.rcht.jist.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Chat message for LLM API requests
@Serializable
data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String,
    // Optional vision payload: data URLs ("data:image/jpeg;base64,...").
    // @Transient because the wire shape differs per provider (OpenAI content
    // array vs Anthropic image blocks); the clients build it manually.
    @Transient val images: List<String> = emptyList()
) { val hasImages get() = images.isNotEmpty() }

// OpenAI-compatible API response
@Serializable
data class OpenAiChatResponse(
    val id: String,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
) {
    @Serializable
    data class Choice(
        val message: ChatMessage,
        val finish_reason: String? = null,
        val index: Int = 0
    )

    @Serializable
    data class Usage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )
}

// Claude API request format
@Serializable
data class ClaudeRequest(
    val model: String,
    val max_tokens: Int,
    val system: String? = null,
    val messages: List<ChatMessage>,
    val temperature: Float? = null
)

// Claude API response format
@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<Content>,
    val model: String,
    val stop_reason: String? = null,
    val usage: Usage? = null
) {
    @Serializable
    data class Content(
        val type: String, // "text"
        val text: String? = null
    )

    @Serializable
    data class Usage(
        val input_tokens: Int,
        val output_tokens: Int
    )
}

// Unified LLM response for internal use
data class LlmResponse(
    val text: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val model: String = "",
    val finishReason: String = "stop"
)

// LLM error response
data class LlmError(
    val message: String,
    val code: String? = null,
    val statusCode: Int? = null
)
