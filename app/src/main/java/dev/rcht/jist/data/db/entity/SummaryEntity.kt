package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationKey: String,
    val appName: String,
    val contactOrGroup: String,
    val summaryText: String,
    val messageCount: Int,
    val modelUsed: String,
    val tokenCount: Int? = null,
    val createdAt: Long
)
