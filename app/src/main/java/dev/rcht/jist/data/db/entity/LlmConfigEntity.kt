package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "llm_configs")
data class LlmConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val provider: String, // OPENAI, CLAUDE, CUSTOM
    val apiKey: String, // Should be encrypted
    val baseUrl: String,
    val modelId: String,
    val isDefault: Boolean = false,
    val maxTokens: Int = 512,
    val temperature: Float = 0.3f
)
