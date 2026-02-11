package dev.rcht.jist.util

import dev.rcht.jist.data.db.entity.NotificationEntity

/**
 * Extracts meaningful conversation keys for different apps.
 * Better than just packageName:title for per-contact grouping.
 */
object ConversationKeyExtractor {
    
    /**
     * Extract conversation key with app-specific logic
     * Examples:
     * - WhatsApp: "com.whatsapp:0123456789" (contact/group ID from content)
     * - Gmail: "com.google.android.gms:thread-abc123" (thread ID)
     * - Telegram: "org.telegram.messenger:chat_123" (chat ID)
     * - Generic: "packageName:title" (fallback)
     */
    fun extractConversationKey(notification: NotificationEntity): String {
        return when {
            isWhatsApp(notification.packageName) -> extractWhatsAppKey(notification)
            isGmail(notification.packageName) -> extractGmailKey(notification)
            isTelegram(notification.packageName) -> extractTelegramKey(notification)
            else -> "${notification.packageName}:${notification.title}"
        }
    }
    
    private fun isWhatsApp(packageName: String): Boolean {
        return packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"
    }
    
    private fun isGmail(packageName: String): Boolean {
        return packageName == "com.google.android.gms" || packageName == "com.google.android.apps.inbox"
    }
    
    private fun isTelegram(packageName: String): Boolean {
        return packageName == "org.telegram.messenger" || packageName == "org.thunderdog.challegram"
    }
    
    private fun extractWhatsAppKey(notification: NotificationEntity): String {
        // WhatsApp includes sender name in title for group messages
        // Format: "Group Name" or "Contact Name"
        // For one-on-one: just contact name
        // For groups: "Contact Name" (in Group Name) format
        
        val title = notification.title ?: return "com.whatsapp:unknown"
        
        // Extract contact/group identifier from title
        // Group chats often have format: "Name (Group Name)"
        val contactKey = if (title.contains("(")) {
            title.substringBefore("(").trim()
        } else {
            title
        }
        
        return "com.whatsapp:$contactKey"
    }
    
    private fun extractGmailKey(notification: NotificationEntity): String {
        // Gmail uses sender email in title
        // Format: "Sender Name <sender@example.com>"
        val title = notification.title ?: return "com.gmail:unknown"
        
        // Extract email or sender name
        val emailMatch = Regex("<([^>]+)>").find(title)
        val key = emailMatch?.groupValues?.get(1) ?: title.substringBefore("<").trim()
        
        return "com.gmail:$key"
    }
    
    private fun extractTelegramKey(notification: NotificationEntity): String {
        // Telegram uses chat/group name in title
        val title = notification.title ?: return "org.telegram:unknown"
        
        return "org.telegram:$title"
    }
}
