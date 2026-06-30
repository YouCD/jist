package dev.rcht.jist.llm

import dev.rcht.jist.llm.client.ClaudeClient
import dev.rcht.jist.llm.client.OpenAiCompatibleClient
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import okhttp3.OkHttpClient

object LlmClientFactory {

    enum class Provider {
        OPENAI,
        CLAUDE,
        CUSTOM
    }

    fun createClient(
        config: LlmConfigEntity,
        httpClient: OkHttpClient
    ): LlmClient {
        return when (config.provider) {
            "OPENAI", "CUSTOM" -> OpenAiCompatibleClient(httpClient)
            "CLAUDE" -> ClaudeClient(httpClient)
            else -> OpenAiCompatibleClient(httpClient)
        }
    }

    fun getDefaultBaseUrl(provider: String): String = when (provider) {
        "OPENAI" -> "https://api.openai.com"
        "CLAUDE" -> "https://api.anthropic.com"
        else -> ""
    }

    fun getDefaultModel(provider: String): String = when (provider) {
        "OPENAI" -> "gpt-4o-mini"
        "CLAUDE" -> "claude-3-5-haiku-20241022"
        else -> ""
    }

    fun getAvailableProviders() = listOf(
        Provider.OPENAI,
        Provider.CLAUDE,
        Provider.CUSTOM
    )
}
