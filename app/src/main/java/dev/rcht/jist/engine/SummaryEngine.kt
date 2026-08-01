package dev.rcht.jist.engine

import android.content.Context
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.repository.AppRuleRepository
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.preferences.PreferencesRepository
import dev.rcht.jist.data.repository.SummaryRepository
import dev.rcht.jist.webhook.WebhookService
import dev.rcht.jist.llm.LlmClient
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.NotificationForSummary
import dev.rcht.jist.llm.PromptBuilder
import dev.rcht.jist.util.AppIconExtractor
import kotlinx.coroutines.flow.first
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
    private val preferencesRepository: PreferencesRepository,
    private val httpClient: OkHttpClient,
    private val webhookService: WebhookService
) {

    private val promptBuilder = PromptBuilder()

    /**
     * Summarize notifications for a specific conversation
     * @param includeSummarized If true, includes already-summarized notifications (for re-summarize)
     */
    suspend fun summarizeConversation(
        conversationKey: String,
        includeSummarized: Boolean = false
    ): SummaryResult {
        try {
            val notifications = if (includeSummarized) {
                notificationRepository.getByConversationKey(conversationKey)
            } else {
                notificationRepository.getUnsummarizedByConversationKey(conversationKey)
            }

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
            val appRule = if (!packageName.isNullOrBlank() && packageName != "Unknown") {
                appRuleRepository.getForApp(packageName)
            } else {
                null
            }
            val customPrompt = appRule?.customPrompt
            // Get human-readable app label if package name is available
            val appLabelForDisplay = if (!packageName.isNullOrBlank() && packageName != "Unknown") {
                AppIconExtractor.getAppLabel(context, packageName)
            } else {
                appName
            }
            val contactOrGroup = notifications.firstOrNull()?.title
            val prefs = preferencesRepository.preferencesFlow.first()
            val tone = prefs.summaryTone
            val length = prefs.summaryLength
            val messages = promptBuilder.buildMessages(
                notificationsForPrompt,
                appName,
                contactOrGroup,
                customPrompt,
                tone,
                length
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
                    val notificationTimeFrom = notifications.minOf { it.timestamp }
                    val notificationTimeTo = notifications.maxOf { it.timestamp }
                    val summaryEntity = SummaryEntity(
                        packageName = packageName ?: "Unknown",
                        conversationKey = conversationKey,
                        appName = appLabelForDisplay ?: "Unknown",
                        contactOrGroup = contactOrGroup ?: "Unknown",
                        summaryText = summary.text,
                        messageCount = notifications.size,
                        modelUsed = summary.model,
                        tokenCount = summary.totalTokens,
                        createdAt = System.currentTimeMillis(),
                        notificationTimeFrom = notificationTimeFrom,
                        notificationTimeTo = notificationTimeTo
                    )

                    val summaryId = summaryRepository.insert(summaryEntity)

                    // Fire webhook after successful save
                    val webhookPrefs = preferencesRepository.preferencesFlow.first()
                    webhookService.sendAsync(summaryEntity, webhookPrefs)

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
                val appRule = if (!packageName.isNullOrBlank() && packageName != "Unknown") {
                    appRuleRepository.getForApp(packageName)
                } else {
                    null
                }
                if (appRule != null && !appRule.enabled) {
                    continue // Skip disabled apps
                }

                val minMessages = appRule?.minMessagesForSummary ?: 5
                if (notifications.size >= minMessages) {
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
