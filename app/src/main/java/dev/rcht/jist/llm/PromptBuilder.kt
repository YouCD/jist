package dev.rcht.jist.llm

import dev.rcht.jist.llm.model.ChatMessage

/**
 * Builds prompts for notification summarization
 */
class PromptBuilder {

    companion object {
        private val DEFAULT_SYSTEM_PROMPT = """
            You are a notification summarizer for a mobile app. Your task is to create concise, 
            clear summaries of notifications from messaging apps, emails, and other sources.
            
            Guidelines:
            - Be brief but complete - capture essential information
            - Highlight action items or questions directed at the user
            - Preserve tone (urgent, casual, formal, etc)
            - Use bullet points if summarizing multiple messages
            - Keep the summary under 150 words
            - Do not include timestamps or sender names unless critical to understanding
        """.trimIndent()
    }

    fun buildSystemPrompt(
        appName: String? = null,
        contactOrGroup: String? = null,
        customPrompt: String? = null
    ): String {
        return customPrompt ?: buildDefaultSystemPrompt(appName, contactOrGroup)
    }

    private fun buildDefaultSystemPrompt(appName: String?, contactOrGroup: String?): String {
        return if (appName != null || contactOrGroup != null) {
            val context = when {
                appName != null && contactOrGroup != null ->
                    "for $contactOrGroup in $appName"
                appName != null -> "from $appName"
                else -> "from $contactOrGroup"
            }
            "$DEFAULT_SYSTEM_PROMPT\n\nYou are summarizing notifications $context."
        } else {
            DEFAULT_SYSTEM_PROMPT
        }
    }

    fun buildUserPrompt(
        notifications: List<NotificationForSummary>,
        appName: String? = null,
        contactOrGroup: String? = null
    ): String {
        if (notifications.isEmpty()) {
            return "No notifications to summarize"
        }

        val header = when {
            appName != null && contactOrGroup != null -> "Notifications from $contactOrGroup in $appName:"
            appName != null -> "Notifications from $appName:"
            else -> "Notifications:"
        }

        val notificationsText = notifications.mapIndexed { index, notification ->
            val timestamp = formatTimestamp(notification.timestamp)
            val sender = if (notification.sender.isNotEmpty()) "${notification.sender}: " else ""
            "${index + 1}. [$timestamp] $sender${notification.text}"
        }.joinToString("\n")

        return """
            $header
            
            $notificationsText
            
            Please summarize these notifications concisely.
        """.trimIndent()
    }

    fun buildMessages(
        notifications: List<NotificationForSummary>,
        appName: String? = null,
        contactOrGroup: String? = null,
        customPrompt: String? = null
    ): List<ChatMessage> {
        val systemPrompt = buildSystemPrompt(appName, contactOrGroup, customPrompt)
        val userPrompt = buildUserPrompt(notifications, appName, contactOrGroup)

        return listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMinutes = (now - timestamp) / 60000

        return when {
            diffMinutes < 1 -> "just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            else -> "${diffMinutes / 1440}d ago"
        }
    }
}

data class NotificationForSummary(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String = "",
    val appName: String = ""
)
