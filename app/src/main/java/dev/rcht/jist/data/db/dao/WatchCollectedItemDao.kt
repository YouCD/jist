package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.rcht.jist.data.db.entity.WatchCollectedItemEntity

data class TopicStats(
    val count: Int,
    val latestPreview: String?,
    val latestMatchedAt: Long?
)

@Dao
interface WatchCollectedItemDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: WatchCollectedItemEntity): Long

    @Query("""
        SELECT COUNT(*) as count, 
               MAX(aiExtractedInfo) as latestPreview, 
               MAX(matchedAt) as latestMatchedAt 
        FROM watch_collected_items 
        WHERE topicId = :topicId
    """)
    suspend fun getStatsForTopic(topicId: Long): TopicStats

    @Query("SELECT * FROM watch_collected_items WHERE topicId = :topicId ORDER BY matchedAt DESC")
    suspend fun getItemsForTopic(topicId: Long): List<WatchCollectedItemEntity>

    @Query("SELECT * FROM watch_collected_items WHERE topicId = :topicId ORDER BY matchedAt DESC LIMIT :limit")
    suspend fun getRecentItemsForTopic(topicId: Long, limit: Int): List<WatchCollectedItemEntity>

    @Query("SELECT COUNT(*) FROM watch_collected_items WHERE topicId = :topicId")
    suspend fun countForTopic(topicId: Long): Int

    @Query("DELETE FROM watch_collected_items WHERE topicId = :topicId")
    suspend fun deleteForTopic(topicId: Long)

    @Query("DELETE FROM watch_collected_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE watch_collected_items SET isRead = 1 WHERE topicId = :topicId")
    suspend fun markAllRead(topicId: Long)

    @Query("SELECT COUNT(*) FROM watch_collected_items WHERE topicId = :topicId AND isRead = 0")
    suspend fun countUnread(topicId: Long): Int
}
