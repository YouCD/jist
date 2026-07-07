package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watched_chats",
    foreignKeys = [
        ForeignKey(
            entity = ChatSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceId", "chatId"], unique = true),
        Index(value = ["sourceId"])
    ]
)
data class WatchedChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val chatId: String,
    val chatName: String,
    val isEnabled: Boolean = true,
    val isSummarized: Boolean = false,
    val customPrompt: String? = null,
    val minMessagesForSummary: Int = 5,
    val createdAt: Long = System.currentTimeMillis()
)
