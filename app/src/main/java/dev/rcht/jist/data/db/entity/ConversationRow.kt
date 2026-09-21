package dev.rcht.jist.data.db.entity

/**
 * Projection row for listing distinct conversations from notifications.
 * Used by NotificationDao.listConversations() (GROUP BY conversationKey).
 */
data class ConversationRow(
    val packageName: String,
    val title: String,
    val conversationKey: String,
    val count: Int,
    val lastSeen: Long
)
