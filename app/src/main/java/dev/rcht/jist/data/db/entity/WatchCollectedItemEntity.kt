package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_collected_items",
    indices = [
        Index(value = ["topicId", "notificationId"], unique = true),
        Index(value = ["topicId"]),
        Index(value = ["notificationId"])
    ]
)
data class WatchCollectedItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicId: Long,
    val notificationId: Long,
    val sourceApp: String,
    val matchedKeyword: String,
    val matchType: String = "KEYWORD",
    val aiExtractedInfo: String? = null,
    val importance: Int = 3,
    val matchedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
