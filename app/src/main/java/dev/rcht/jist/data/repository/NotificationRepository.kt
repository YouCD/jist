package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.NotificationDao
import dev.rcht.jist.data.db.entity.NotificationEntity

class NotificationRepository(private val notificationDao: NotificationDao) {
    
    suspend fun insert(notification: NotificationEntity): Long {
        return notificationDao.insert(notification)
    }
    
    suspend fun insertAll(notifications: List<NotificationEntity>) {
        notificationDao.insertAll(notifications)
    }

    suspend fun update(notification: NotificationEntity) {
        notificationDao.update(notification)
    }
    
    suspend fun getUnsummarizedForKey(key: String): List<NotificationEntity> {
        return notificationDao.getUnsummarizedForKey(key)
    }

    suspend fun getUnsummarizedByConversationKey(key: String): List<NotificationEntity> {
        return notificationDao.getUnsummarizedForKey(key)
    }

    suspend fun getAllUnsummarized(): List<NotificationEntity> {
        return notificationDao.getAllUnsummarized()
    }
    
    suspend fun getPendingConversationKeys(minCount: Int): List<String> {
        return notificationDao.getPendingConversationKeys(minCount)
    }
    
    suspend fun markSummarized(ids: List<Long>, summaryId: Long) {
        notificationDao.markSummarized(ids, summaryId)
    }
    
    suspend fun getRecent(limit: Int = 50, offset: Int = 0): List<NotificationEntity> {
        return notificationDao.getRecent(limit, offset)
    }
    
    suspend fun getByApp(packageName: String): List<NotificationEntity> {
        return notificationDao.getByApp(packageName)
    }
    
    suspend fun deleteOlderThan(timestampMs: Long) {
        notificationDao.deleteOlderThan(timestampMs)
    }
    
    suspend fun deleteAll() {
        notificationDao.deleteAll()
    }
    
    suspend fun getByConversationKey(key: String): List<NotificationEntity> {
        return notificationDao.getByConversationKey(key)
    }
}
