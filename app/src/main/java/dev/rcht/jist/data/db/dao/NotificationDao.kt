package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.rcht.jist.data.db.entity.NotificationEntity

@Dao
interface NotificationDao {
    
    @Insert
    suspend fun insert(notification: NotificationEntity): Long
    
    @Insert
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Update
    suspend fun update(notification: NotificationEntity)
    
    @Query("SELECT * FROM notifications WHERE conversationKey = :key AND isSummarized = 0 ORDER BY timestamp ASC")
    suspend fun getUnsummarizedForKey(key: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE conversationKey = :key ORDER BY timestamp ASC")
    suspend fun getByConversationKey(key: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isSummarized = 0 ORDER BY timestamp ASC")
    suspend fun getAllUnsummarized(): List<NotificationEntity>
    
    @Query("""
        SELECT DISTINCT conversationKey FROM notifications 
        WHERE isSummarized = 0 
        GROUP BY conversationKey 
        HAVING COUNT(*) >= :minCount
    """)
    suspend fun getPendingConversationKeys(minCount: Int): List<String>
    
    @Query("UPDATE notifications SET isSummarized = 1, summaryId = :summaryId WHERE id IN (:ids)")
    suspend fun markSummarized(ids: List<Long>, summaryId: Long)
    
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(limit: Int, offset: Int): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getByApp(packageName: String): List<NotificationEntity>
    
    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
    
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
    
    @Delete
    suspend fun delete(notification: NotificationEntity)
}
