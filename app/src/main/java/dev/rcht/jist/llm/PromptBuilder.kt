package dev.rcht.jist.llm

import dev.rcht.jist.llm.model.ChatMessage
import java.util.Locale

private val ENGLISH_PROMPT = """
    You are a notification summarizer for a mobile app. Your task is to create concise, 
    clear summaries of notifications from messaging apps, emails, and other sources.
    
    Guidelines:
    - Be brief but complete - capture essential information
    - Highlight action items or questions directed at the user
    - Preserve tone (urgent, casual, formal, etc)
    - Format as plain paragraphs (NOT bullet points or markdown)
    - Keep the summary under 150 words
    - Include sender names if it's a group chat or multiple people
    - Flag any urgent or time-sensitive messages
    - Extract and highlight decisions or next steps
    - Do NOT use markdown formatting, asterisks, dashes, or special characters
    - Do NOT use bullet points or numbered lists
    - Simply use plain text paragraphs separated by line breaks
""".trimIndent()

private val CHINESE_PROMPT = """
    你是一个手机应用的通知摘要助手。你的任务是为来自即时通讯、邮件等应用的通知生成简洁清晰的摘要。
    
    要求：
    - 简洁完整 — 抓取关键信息
    - 突出需要用户处理的事项或问题
    - 保留原文语气（紧急、随意、正式等）
    - 纯段落格式（不要使用项目符号或 Markdown）
    - 摘要控制在 150 字以内
    - 如果是群聊或多人群组，注明发送者
    - 标记任何紧急或时效性强的消息
    - 提取并突出决策或后续步骤
    - 不要使用 Markdown 格式、星号、破折号或特殊符号
    - 不要使用项目符号或编号列表
    - 仅使用纯文本段落，段落之间用换行分隔
""".trimIndent()

fun getDefaultSystemPrompt(): String {
    return if (Locale.getDefault().language == "zh") CHINESE_PROMPT else ENGLISH_PROMPT
}

class PromptBuilder {

    fun toneInstruction(tone: String): String {
        val isZh = Locale.getDefault().language == "zh"
        return when (tone) {
            "PROFESSIONAL" -> if (isZh) "使用正式、中立的语气进行摘要。" else "Use a professional and neutral tone."
            "CASUAL" -> if (isZh) "使用轻松、口语化的语气进行摘要。" else "Use a casual and conversational tone."
            "WITTY" -> if (isZh) "在保持信息准确的前提下，适当加入轻松幽默的表达。" else "Add subtle wit and humor where appropriate while keeping the summary accurate."
            "URGENT" -> if (isZh) "强调紧急性和时效性信息，突出需要立即处理的事项。" else "Emphasize urgency and time-sensitive information. Highlight action items that need immediate attention."
            else -> ""
        }
    }

    fun lengthInstruction(length: String): String {
        val isZh = Locale.getDefault().language == "zh"
        return when (length) {
            "SHORT" -> if (isZh) "摘要控制在 50 字以内。" else "Keep the summary under 50 words."
            "MEDIUM" -> if (isZh) "摘要控制在 150 字以内。" else "Keep the summary under 150 words."
            "LONG" -> if (isZh) "摘要控制在 300 字以内。" else "Keep the summary under 300 words."
            else -> ""
        }
    }

    fun buildSystemPrompt(
        appName: String? = null,
        contactOrGroup: String? = null,
        customPrompt: String? = null,
        tone: String? = null,
        length: String? = null
    ): String {
        if (customPrompt != null) return customPrompt
        val base = buildDefaultSystemPrompt(appName, contactOrGroup)
        val toneText = if (tone != null) toneInstruction(tone) else ""
        val lengthText = if (length != null) lengthInstruction(length) else ""
        val styleParts = listOfNotNull(toneText, lengthText).filter { it.isNotBlank() }
        if (styleParts.isEmpty()) return base
        val isZh = Locale.getDefault().language == "zh"
        val styleBlock = if (isZh) "风格要求：\n${styleParts.joinToString("\n")}" else "Style: ${styleParts.joinToString(" ")}"
        return "$base\n\n$styleBlock"
    }

    private fun buildDefaultSystemPrompt(appName: String?, contactOrGroup: String?): String {
        val base = getDefaultSystemPrompt()
        if (appName == null && contactOrGroup == null) return base
        val context = when {
            appName != null && contactOrGroup != null ->
                if (Locale.getDefault().language == "zh") "你正在为 $appName 中的 $contactOrGroup 摘要通知。"
                else "for $contactOrGroup in $appName"
            appName != null ->
                if (Locale.getDefault().language == "zh") "你正在为 $appName 摘要通知。"
                else "from $appName"
            else ->
                if (Locale.getDefault().language == "zh") "你正在为 $contactOrGroup 摘要通知。"
                else "from $contactOrGroup"
        }
        return "$base\n\n$context"
    }

    fun buildUserPrompt(
        notifications: List<NotificationForSummary>,
        appName: String? = null,
        contactOrGroup: String? = null
    ): String {
        if (notifications.isEmpty()) {
            return if (Locale.getDefault().language == "zh") "没有需要摘要的通知" else "No notifications to summarize"
        }

        val header = if (Locale.getDefault().language == "zh") {
            when {
                appName != null && contactOrGroup != null ->
                    "请摘要来自 $appName 中 $contactOrGroup 的 ${notifications.size} 条通知："
                appName != null -> "请摘要来自 $appName 的 ${notifications.size} 条通知："
                else -> "请摘要以下 ${notifications.size} 条通知："
            }
        } else {
            when {
                appName != null && contactOrGroup != null -> 
                    "Summarize these ${notifications.size} notifications from $contactOrGroup in $appName:"
                appName != null -> "Summarize these ${notifications.size} notifications from $appName:"
                else -> "Summarize these ${notifications.size} notifications:"
            }
        }

        val notificationsText = notifications.mapIndexed { index, notification ->
            val timestamp = formatTimestamp(notification.timestamp)
            val sender = if (notification.sender.isNotEmpty()) "${notification.sender}: " else ""
            "${index + 1}. [$timestamp] $sender${notification.text}"
        }.joinToString("\n")

        val footer = if (Locale.getDefault().language == "zh") {
            "请提供简洁的摘要，突出关键点和需要处理的事项。"
        } else {
            "Please provide a concise summary highlighting key points and any action items."
        }

        return """
            $header
            
            $notificationsText
            
            $footer
        """.trimIndent()
    }

    fun buildMessages(
        notifications: List<NotificationForSummary>,
        appName: String? = null,
        contactOrGroup: String? = null,
        customPrompt: String? = null,
        tone: String? = null,
        length: String? = null
    ): List<ChatMessage> {
        val systemPrompt = buildSystemPrompt(appName, contactOrGroup, customPrompt, tone, length)
        val userPrompt = buildUserPrompt(notifications, appName, contactOrGroup)

        return listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMinutes = (now - timestamp) / 60000
        val isZh = Locale.getDefault().language == "zh"

        return when {
            diffMinutes < 1 -> if (isZh) "刚刚" else "just now"
            diffMinutes < 60 -> if (isZh) "${diffMinutes}分钟前" else "${diffMinutes}m ago"
            diffMinutes < 1440 -> if (isZh) "${diffMinutes / 60}小时前" else "${diffMinutes / 60}h ago"
            else -> if (isZh) "${diffMinutes / 1440}天前" else "${diffMinutes / 1440}d ago"
        }
    }
}

data class NotificationForSummary(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String = "",
    val appName: String = ""
)
