package dev.rcht.jist.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val enabled: Boolean = true,
    val mode: String = "AUTO", // AUTO, MANUAL, DISABLED
    val batchWindowMinutes: Int = 15,
    val minMessagesForSummary: Int = 3,
    val customPrompt: String? = null
)
