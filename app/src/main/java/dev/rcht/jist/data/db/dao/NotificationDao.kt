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

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAll(): List<NotificationEntity>

    @Query("UPDATE notifications SET pendingIntentData = :data WHERE id = :id")
    suspend fun updatePendingIntentData(id: Long, data: String)
    
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
    
    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getById(id: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getByApp(packageName: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE packageName = :pkg AND ((:tag IS NULL AND notificationTag IS NULL) OR notificationTag = :tag) AND notificationId = :nid LIMIT 1")
    suspend fun findByNotificationKey(pkg: String, tag: String?, nid: Int): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE packageName = :pkg AND title = :title AND content = :content AND ABS(timestamp - :now) < :windowMs LIMIT 1")
    suspend fun findByContentDedup(pkg: String, title: String, content: String, now: Long, windowMs: Long): NotificationEntity?
    
    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
    
    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun count(): Int

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
    
    @Delete
    suspend fun delete(notification: NotificationEntity)
}
