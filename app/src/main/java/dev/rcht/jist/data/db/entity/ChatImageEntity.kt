package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A captured WeChat chat image. Association with a chat message is soft
 * (by size/time match at capture time); unmatched images keep empty chatId.
 */
@Entity(
    tableName = "chat_images",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["capturedAt"])
    ]
)
data class ChatImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String = "",
    val chatAppKey: String = "",
    val senderName: String = "",
    val messageTimestamp: Long = 0,
    val fileName: String,
    val format: String,
    val sizeBytes: Int,
    val contentHash: String,
    /** Relative path under the app files dir (e.g. "inbox/<hash>.jpg"), or null
     *  when the bytes were stored inline and moved by the provider. */
    val storedPath: String? = null,
    val capturedAt: Long
)
