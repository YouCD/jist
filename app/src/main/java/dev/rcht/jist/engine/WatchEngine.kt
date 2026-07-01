package dev.rcht.jist.engine

import android.content.Context
import android.util.Log
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.db.entity.WatchCollectedItemEntity
import dev.rcht.jist.data.db.entity.WatchTopicEntity
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.WatchCollectedItemRepository
import dev.rcht.jist.data.repository.WatchTopicRepository
import dev.rcht.jist.llm.LlmClient
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class WatchEngine(
    private val context: Context,
    private val watchTopicRepository: WatchTopicRepository,
    private val watchCollectedItemRepository: WatchCollectedItemRepository,
    private val llmConfigRepository: LlmConfigRepository,
    private val httpClient: OkHttpClient
) {
    private val matcher = WatchMatcher()

    suspend fun matchNewNotification(notification: NotificationEntity) {
        try {
            val activeTopics = watchTopicRepository.getActiveTopics()
            if (activeTopics.isEmpty()) return

            for (topic in activeTopics) {
                val matchedKeyword = matcher.matches(notification, topic)
                if (matchedKeyword == null) continue

                val matchType: String
                val aiExtractedInfo: String?
                val importance: Int

                if (topic.matchMode == "AI_SEMANTIC") {
                    val result = semanticMatch(notification, topic)
                    if (result == null) continue
                    matchType = "AI_SEMANTIC"
                    aiExtractedInfo = result.keyInfo
                    importance = result.importance
                } else {
                    matchType = "KEYWORD"
                    aiExtractedInfo = notification.content.take(200)
                    importance = 3
                }

                val item = WatchCollectedItemEntity(
                    topicId = topic.id,
                    notificationId = notification.id,
                    sourceApp = notification.packageName,
                    matchedKeyword = matchedKeyword,
                    matchType = matchType,
                    aiExtractedInfo = aiExtractedInfo,
                    importance = importance,
                    matchedAt = System.currentTimeMillis()
                )

                watchCollectedItemRepository.insert(item)

                withContext(Dispatchers.Main) {
                    try {
                        dev.rcht.jist.widget.WatchWidgetProvider.refreshWidget(context)
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in matchNewNotification", e)
        }
    }

    private data class SemanticResult(val keyInfo: String, val importance: Int)

    private suspend fun semanticMatch(
        notification: NotificationEntity,
        topic: WatchTopicEntity
    ): SemanticResult? {
        try {
            val config = llmConfigRepository.getDefaultConfig() ?: return null
            val client = LlmClientFactory.createClient(config, httpClient)

            val prompt = buildSemanticPrompt(notification, topic)
            val llmConfig = LlmRequestConfig(
                model = config.modelId,
                maxTokens = 256,
                temperature = 0.1f,
                apiKey = config.apiKey,
                baseUrl = config.baseUrl
            )

            val response = client.complete(prompt, llmConfig)
            return when (response) {
                is LlmResult.Success -> {
                    parseSemanticResponse(response.data.text)
                }
                is LlmResult.Error -> {
                    Log.w(TAG, "LLM error: ${response.error.message}")
                    SemanticResult(notification.content.take(200), 3)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Semantic match error", e)
            return SemanticResult(notification.content.take(200), 3)
        }
    }

    private fun buildSemanticPrompt(
        notification: NotificationEntity,
        topic: WatchTopicEntity
    ): List<ChatMessage> {
        return listOf(
            ChatMessage("system",
                context.getString(
                    dev.rcht.jist.R.string.watch_prompt_system,
                    topic.title, topic.description
                )
            ),
            ChatMessage("user",
                context.getString(
                    dev.rcht.jist.R.string.watch_prompt_user,
                    notification.appName, notification.title, notification.content
                )
            )
        )
    }

    private fun parseSemanticResponse(text: String): SemanticResult? {
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val obj = json.decodeFromString<SemanticResponseJson>(text)
            if (obj.relevant) {
                SemanticResult(obj.keyInfo ?: "", obj.importance ?: 3)
            } else null
        } catch (_: Exception) {
            if (text.contains("true", ignoreCase = true)) {
                SemanticResult(text.take(200), 3)
            } else null
        }
    }

    private val TAG = "WatchEngine"
}

@kotlinx.serialization.Serializable
data class SemanticResponseJson(
    val relevant: Boolean,
    val keyInfo: String? = null,
    val importance: Int? = null
)
