package dev.rcht.jist.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Chat message for LLM API requests
@Serializable
data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

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

// Gemini API request format
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null
) {
    @Serializable
    data class GeminiContent(
        val parts: List<Part>,
        val role: String? = null
    ) {
        @Serializable
        data class Part(
            val text: String
        )
    }

    @Serializable
    data class GenerationConfig(
        val maxOutputTokens: Int? = null,
        val temperature: Float? = null,
        val topP: Float? = null,
        val topK: Int? = null
    )

    @Serializable
    data class SafetySetting(
        val category: String,
        val threshold: String
    )
}

// Gemini API response format
@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>,
    val usageMetadata: UsageMetadata? = null
) {
    @Serializable
    data class Candidate(
        val content: Content,
        val finishReason: String? = null,
        val index: Int = 0
    ) {
        @Serializable
        data class Content(
            val parts: List<Part>,
            val role: String? = null
        ) {
            @Serializable
            data class Part(
                val text: String
            )
        }
    }

    @Serializable
    data class UsageMetadata(
        val promptTokenCount: Int,
        val candidatesTokenCount: Int,
        val totalTokenCount: Int
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
