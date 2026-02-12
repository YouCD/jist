package dev.rcht.jist.engine

import android.content.Context
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.repository.AppRuleRepository
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.SummaryRepository
import dev.rcht.jist.llm.LlmClient
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.NotificationForSummary
import dev.rcht.jist.llm.PromptBuilder
import dev.rcht.jist.util.AppIconExtractor
import okhttp3.OkHttpClient

/**
 * Orchestrates notification batching and LLM-based summarization
 */
class SummaryEngine(
    private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val summaryRepository: SummaryRepository,
    private val llmConfigRepository: LlmConfigRepository,
    private val appRuleRepository: AppRuleRepository,
    private val httpClient: OkHttpClient
) {

    private val promptBuilder = PromptBuilder()

    /**
     * Summarize all unsummarized notifications for a specific conversation
     */
    suspend fun summarizeConversation(conversationKey: String): SummaryResult {
        try {
            // Get unsummarized notifications for this conversation
            val notifications = notificationRepository.getUnsummarizedByConversationKey(
                conversationKey
            )

            if (notifications.isEmpty()) {
                return SummaryResult.Error("No notifications to summarize")
            }

            // Get the default LLM config
            val llmConfig = llmConfigRepository.getDefaultConfig()
                ?: return SummaryResult.Error("No LLM configuration found")

            // Create LLM client
            val client = LlmClientFactory.createClient(llmConfig, httpClient)

            // Extract notification data for prompt
            val notificationsForPrompt = notifications.map { notification ->
                NotificationForSummary(
                    text = notification.content,
                    timestamp = notification.timestamp,
                    sender = notification.senderName ?: "",
                    appName = notification.appName
                )
            }

            // Build prompt
            val packageName = notifications.firstOrNull()?.packageName
            val appName = notifications.firstOrNull()?.appName
            // Get human-readable app label if package name is available
            val appLabelForDisplay = if (!packageName.isNullOrBlank() && packageName != "Unknown") {
                AppIconExtractor.getAppLabel(context, packageName)
            } else {
                appName
            }
            val contactOrGroup = notifications.firstOrNull()?.title
            val messages = promptBuilder.buildMessages(
                notificationsForPrompt,
                appName,
                contactOrGroup
            )

            // Call LLM
            val llmConfig_ = LlmRequestConfig(
                model = llmConfig.modelId,
                maxTokens = llmConfig.maxTokens,
                temperature = llmConfig.temperature,
                apiKey = llmConfig.apiKey,
                baseUrl = llmConfig.baseUrl
            )

            val response = client.complete(messages, llmConfig_)

            return when (response) {
                is LlmResult.Success -> {
                    val summary = response.data
                    // Store summary in database
                    val summaryEntity = SummaryEntity(
                        packageName = packageName ?: "Unknown",
                        conversationKey = conversationKey,
                        appName = appLabelForDisplay ?: "Unknown",
                        contactOrGroup = contactOrGroup ?: "Unknown",
                        summaryText = summary.text,
                        messageCount = notifications.size,
                        modelUsed = summary.model,
                        tokenCount = summary.totalTokens,
                        createdAt = System.currentTimeMillis()
                    )

                    val summaryId = summaryRepository.insert(summaryEntity)

                    // Mark notifications as summarized
                    notifications.forEach { notification ->
                        notificationRepository.update(
                            notification.copy(
                                isSummarized = true,
                                summaryId = summaryId
                            )
                        )
                    }

                    SummaryResult.Success(summary.text, summaryId)
                }

                is LlmResult.Error -> {
                    SummaryResult.Error(response.error.message)
                }
            }
        } catch (e: Exception) {
            return SummaryResult.Error("Error during summarization: ${e.message}")
        }
    }

    /**
     * Summarize all pending conversations that meet their batch window threshold
     */
    suspend fun summarizeAllPending(): List<SummaryResult> {
        try {
            // Get all unsummarized notifications grouped by conversation key
            val conversationsByKey = notificationRepository.getAllUnsummarized()
                .groupBy { it.conversationKey }

            val results = mutableListOf<SummaryResult>()

            for ((conversationKey, notifications) in conversationsByKey) {
                // Check if app is enabled
                val packageName = notifications.firstOrNull()?.packageName
                if (!packageName.isNullOrBlank() && packageName != "Unknown") {
                    val appRule = appRuleRepository.getForApp(packageName)
                    if (appRule != null && !appRule.enabled) {
                        continue // Skip disabled apps
                    }
                }

                // Summarize when there are 5+ messages from the same chat
                if (notifications.size >= 5) {
                    val result = summarizeConversation(conversationKey)
                    results.add(result)
                }
            }

            return results
        } catch (e: Exception) {
            return listOf(SummaryResult.Error("Error during batch summarization: ${e.message}"))
        }
    }
}

sealed class SummaryResult {
    data class Success(val summaryText: String, val summaryId: Long) : SummaryResult()
    data class Error(val message: String) : SummaryResult()
}
