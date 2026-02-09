package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.SummaryDao
import dev.rcht.jist.data.db.entity.SummaryEntity

class SummaryRepository(private val summaryDao: SummaryDao) {
    
    suspend fun insert(summary: SummaryEntity): Long {
        return summaryDao.insert(summary)
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
}
