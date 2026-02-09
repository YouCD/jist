package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import dev.rcht.jist.data.db.entity.SummaryEntity

@Dao
interface SummaryDao {
    
    @Insert
    suspend fun insert(summary: SummaryEntity): Long
    
    @Query("SELECT * FROM summaries ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int, offset: Int): List<SummaryEntity>
    
    @Query("SELECT * FROM summaries WHERE conversationKey = :key ORDER BY createdAt DESC")
    suspend fun getForConversation(key: String): List<SummaryEntity>
    
    @Query("SELECT * FROM summaries WHERE appName = :appName ORDER BY createdAt DESC")
    suspend fun getByApp(appName: String): List<SummaryEntity>
    
    @Query("SELECT * FROM summaries WHERE summaryText LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun search(query: String): List<SummaryEntity>
    
    @Query("SELECT SUM(tokenCount) FROM summaries WHERE createdAt >= :since")
    suspend fun getTotalTokensSince(since: Long): Int?
    
    @Query("DELETE FROM summaries WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
    
    @Delete
    suspend fun delete(summary: SummaryEntity)
}
