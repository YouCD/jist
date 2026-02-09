package dev.rcht.jist.llm

import dev.rcht.jist.llm.client.ClaudeClient
import dev.rcht.jist.llm.client.GeminiClient
import dev.rcht.jist.llm.client.OpenAiCompatibleClient
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import okhttp3.OkHttpClient

/**
 * Factory for creating LLM clients based on provider type
 */
object LlmClientFactory {

    enum class Provider {
        OPENAI,
        OPENROUTER,
        GEMINI,
        CLAUDE,
        CUSTOM
    }

    fun createClient(
        config: LlmConfigEntity,
        httpClient: OkHttpClient
    ): LlmClient {
        return when (config.provider) {
            "OPENAI", "OPENROUTER", "CUSTOM" -> OpenAiCompatibleClient(httpClient)
            "GEMINI" -> GeminiClient(httpClient)
            "CLAUDE" -> ClaudeClient(httpClient)
            else -> OpenAiCompatibleClient(httpClient) // Default fallback
        }
    }

    fun getDefaultBaseUrl(provider: String): String = when (provider) {
        "OPENAI" -> "https://api.openai.com"
        "OPENROUTER" -> "https://openrouter.ai/api"
        "GEMINI" -> "https://generativelanguage.googleapis.com"
        "CLAUDE" -> "https://api.anthropic.com"
        else -> ""
    }

    fun getDefaultModel(provider: String): String = when (provider) {
        "OPENAI" -> "gpt-4o-mini"
        "OPENROUTER" -> "openai/gpt-4o-mini"
        "GEMINI" -> "gemini-2.0-flash"
        "CLAUDE" -> "claude-3-5-haiku-20241022"
        else -> ""
    }

    fun getAvailableProviders() = listOf(
        Provider.OPENAI,
        Provider.OPENROUTER,
        Provider.GEMINI,
        Provider.CLAUDE,
        Provider.CUSTOM
    )
}
