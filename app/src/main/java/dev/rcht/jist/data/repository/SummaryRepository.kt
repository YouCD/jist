package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.SummaryDao
import dev.rcht.jist.data.db.entity.SummaryEntity

class SummaryRepository(private val summaryDao: SummaryDao) {
    
    suspend fun insert(summary: SummaryEntity): Long {
        return summaryDao.insert(summary)
    }

    suspend fun getAll(): List<SummaryEntity> {
        return summaryDao.getAll()
    }
    
    suspend fun getRecent(limit: Int = 50, offset: Int = 0): List<SummaryEntity> {
        return summaryDao.getRecent(limit, offset)
    }
    
    suspend fun getForConversation(key: String): List<SummaryEntity> {
        return summaryDao.getForConversation(key)
    }
    
    suspend fun getByApp(appName: String): List<SummaryEntity> {
        return summaryDao.getByApp(appName)
    }
    
    suspend fun search(query: String): List<SummaryEntity> {
        return summaryDao.search(query)
    }
    
    suspend fun getTotalTokensSince(timestampMs: Long): Int? {
        return summaryDao.getTotalTokensSince(timestampMs)
    }
    
    suspend fun deleteOlderThan(timestampMs: Long) {
        summaryDao.deleteOlderThan(timestampMs)
    }
    
    suspend fun getById(id: Long): SummaryEntity? {
        return summaryDao.getById(id)
    }

    suspend fun getByConversationKey(key: String): SummaryEntity? {
        return summaryDao.getByConversationKey(key)
    }

    suspend fun markAsRead(id: Long) {
        summaryDao.markAsRead(id)
    }

    suspend fun markAllAsRead() {
        summaryDao.markAllAsRead()
    }

    suspend fun countUnread(): Int {
        return summaryDao.countUnread()
    }

    suspend fun deleteByIds(ids: List<Long>) {
        summaryDao.deleteByIds(ids)
    }
    
    suspend fun searchFts(query: String): List<SummaryEntity> {
        return if (query.isBlank()) {
            getAll()
        } else {
            try {
                summaryDao.searchFts(query)
            } catch (e: Exception) {
                // Fallback to LIKE search if FTS fails
                search(query)
            }
        }
    }
    
    suspend fun searchFtsByApp(query: String, appName: String): List<SummaryEntity> {
        return if (query.isBlank()) {
            getByApp(appName)
        } else {
            try {
                summaryDao.searchFtsByApp(query, appName)
            } catch (e: Exception) {
                // Fallback to LIKE search if FTS fails
                search(query).filter { it.appName == appName }
            }
        }
    }
}
