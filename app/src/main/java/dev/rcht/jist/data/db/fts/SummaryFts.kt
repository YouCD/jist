package dev.rcht.jist.data.db.fts

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Full-text search virtual table for summaries.
 * FTS4 provides efficient text searching across summary content.
 */
@Entity(tableName = "summaries_fts")
@Fts4(contentEntity = dev.rcht.jist.data.db.entity.SummaryEntity::class)
data class SummaryFts(
    @PrimaryKey(autoGenerate = true)
    val rowid: Long = 0,
    val summaryText: String,
    val appName: String,
    val contactOrGroup: String
)
