package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val conversationKey: String,
    val timestamp: Long,
    val isSummarized: Boolean = false,
    val summaryId: Long? = null,
    val senderName: String? = null
)
