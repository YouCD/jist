package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sources")
data class ChatSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val displayName: String,
    val isEnabled: Boolean = true
)
