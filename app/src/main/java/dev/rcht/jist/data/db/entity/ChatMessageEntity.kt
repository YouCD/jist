package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = WatchedChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["watchedChatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chatAppKey", "chatId", "msgSeq"], unique = true),
        Index(value = ["watchedChatId"]),
        Index(value = ["timestamp"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val watchedChatId: Long,
    val senderName: String = "",
    val content: String,
    val timestamp: Long,
    val msgType: Int = 0,
    val msgSeq: Long = 0,
    val chatAppKey: String,
    val chatId: String,
    val rawData: String? = null
)
