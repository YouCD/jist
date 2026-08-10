package dev.rcht.jist.worker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.NotificationForSummary
import dev.rcht.jist.llm.PromptBuilder
import dev.rcht.jist.notification.SummaryNotificationManager
import dev.rcht.jist.engine.SummaryResult
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SummaryWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val app = context.applicationContext as JistApplication
    private val notificationManager = SummaryNotificationManager(context)

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting periodic summarization work")

            // Existing notification summaries
            val results = app.summaryEngine.summarizeAllPending()
            val summaries = mutableListOf<SummaryEntity>()
            results.forEach { result ->
                when (result) {
                    is SummaryResult.Success -> {
                        val summary = app.summaryRepository.getById(result.summaryId)
                        if (summary != null) summaries.add(summary)
                    }
                    is SummaryResult.Error -> Log.w(TAG, "Summarization error: ${result.message}")
                }
            }

            // Xposed chat summaries
            val xposedSummaries = summarizeXposedChats()
            summaries.addAll(xposedSummaries)

            if (summaries.isNotEmpty()) {
                notificationManager.postGroupedSummaryNotifications(summaries)
            }

            // Cleanup old messages for all chats with retentionDays > 0
            cleanupOldMessages()

            try {
                dev.rcht.jist.widget.SummaryWidgetProvider.refreshWidget(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh widget", e)
            }

            Log.d(TAG, "Periodic summarization work completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic summarization", e)
            Result.retry()
        }
    }

    private suspend fun summarizeXposedChats(): List<SummaryEntity> {
        val config = app.llmConfigRepository.getDefault() ?: return emptyList()
        val chats = app.watchedChatRepository.getAll().filter { it.isSummarized }
        if (chats.isEmpty()) return emptyList()

        val client = LlmClientFactory.createClient(config, app.httpClient)
        val llmConfig = LlmRequestConfig(
            model = config.modelId, maxTokens = config.maxTokens,
            temperature = config.temperature, apiKey = config.apiKey,
            baseUrl = config.baseUrl
        )
        val results = mutableListOf<SummaryEntity>()

        for (chat in chats) {
            try {
                val convKey = "xposed_${chat.chatId}"
                val existing = app.summaryRepository.getByConversationKey(convKey)
                val lastTime = existing?.createdAt ?: 0L

                val messages = app.chatMessageRepository.getByWatchedChat(chat.id)
                    .filter { it.timestamp > lastTime }
                if (messages.size < chat.minMessagesForSummary) continue

                val source = app.chatSourceRepository.getEnabled().find { it.id == chat.sourceId }
                val appName = source?.displayName ?: "微信"

                val notifications = messages.map { msg ->
                    NotificationForSummary(text = msg.content, timestamp = msg.timestamp,
                        sender = msg.senderName.ifBlank { "" }, appName = appName)
                }

                val llmMessages = PromptBuilder().buildMessages(notifications = notifications,
                    appName = appName, contactOrGroup = chat.chatName,
                    customPrompt = chat.customPrompt)

                when (val result = client.complete(llmMessages, llmConfig)) {
                    is LlmResult.Success -> {
                        val summary = SummaryEntity(
                            packageName = source?.packageName ?: "com.tencent.mm", conversationKey = convKey,
                            appName = appName, contactOrGroup = chat.chatName,
                            summaryText = result.data.text,
                            messageCount = messages.size,
                            modelUsed = result.data.model,
                            tokenCount = result.data.totalTokens,
                            createdAt = System.currentTimeMillis(),
                            notificationTimeFrom = messages.minOf { it.timestamp },
                            notificationTimeTo = messages.maxOf { it.timestamp }
                        )
                        val id = app.summaryRepository.insert(summary)
                        results.add(summary.copy(id = id))

                        val webhookPrefs = app.preferencesRepository.preferencesFlow.first()
                        app.webhookService.sendAsync(summary.copy(id = id), webhookPrefs)

                        Log.i(TAG, "Xposed summary: ${chat.chatName} (${messages.size} msgs)")
                    }
                    is LlmResult.Error -> Log.w(TAG, "Xposed summary error ${chat.chatName}: ${result.error.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Xposed summary error for ${chat.chatName}", e)
            }
        }
        return results
    }

    private suspend fun cleanupOldMessages() {
        val chats = app.watchedChatRepository.getAll()
        val now = System.currentTimeMillis()
        for (chat in chats) {
            if (chat.retentionDays > 0) {
                val cutoff = now - chat.retentionDays * 86400_000L
                app.chatMessageRepository.deleteOlderThan(cutoff)
                Log.i(TAG, "Retention cleanup: ${chat.chatName} (${chat.retentionDays}d)")
            }
        }
    }

    companion object {
        private const val TAG = "SummaryWorker"
        const val WORK_NAME = "jist_summary_periodic"

        fun schedule(context: Context) {
            val summarizationWork = PeriodicWorkRequestBuilder<SummaryWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, summarizationWork
            )
            Log.d(TAG, "Periodic summarization work scheduled (15 min interval)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Periodic summarization work cancelled")
        }

        fun scheduleImmediate(context: Context) {
            val immediateWork = OneTimeWorkRequestBuilder<SummaryWorker>().build()
            WorkManager.getInstance(context).enqueue(immediateWork)
            Log.d(TAG, "Immediate summarization work enqueued")
        }
    }
}
