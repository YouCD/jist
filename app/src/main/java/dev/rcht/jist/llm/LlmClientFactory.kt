package dev.rcht.jist.llm

import dev.rcht.jist.llm.client.ClaudeClient
import dev.rcht.jist.llm.client.OpenAiCompatibleClient
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object LlmClientFactory {

    enum class Provider {
        OPENAI,
        CLAUDE,
        CUSTOM
    }

    @Serializable
    data class ModelInfo(
        val id: String,
        val `object`: String = "model",
        val owned_by: String = ""
    )

    @Serializable
    data class ModelListResponse(
        val `object`: String = "list",
        val data: List<ModelInfo> = emptyList()
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Normalizes a base URL so it never carries a trailing "/v1" (or slash).
     * e.g. "https://host/v1/" -> "https://host", "https://host/" -> "https://host"
     */
    fun normalizeBaseUrl(baseUrl: String): String {
        var clean = baseUrl.trim().trimEnd('/')
        if (clean.endsWith("/v1", ignoreCase = true)) {
            clean = clean.dropLast(2).trimEnd('/')
        }
        return clean
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
        "OPENAI" -> ""
        "CLAUDE" -> ""
        else -> ""
    }

    fun getAvailableProviders() = listOf(
        Provider.OPENAI,
        Provider.CLAUDE,
        Provider.CUSTOM
    )

    /**
     * 从API端点获取可用模型列表
     * @param baseUrl API基础URL
     * @param apiKey API密钥
     * @param httpClient OkHttp客户端
     * @return 模型ID列表，失败返回空列表
     */
    suspend fun fetchAvailableModels(
        baseUrl: String,
        apiKey: String,
        httpClient: OkHttpClient
    ): Result<List<String>> {
        return try {
            val cleanBaseUrl = normalizeBaseUrl(baseUrl)
            val url = "$cleanBaseUrl/v1/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response body"))
            val modelList = json.decodeFromString<ModelListResponse>(body)
            val modelIds = modelList.data.map { it.id }.sorted()

            if (modelIds.isEmpty()) {
                Result.failure(Exception("No models available"))
            } else {
                Result.success(modelIds)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to fetch models: ${e.message}"))
        }
    }
}
