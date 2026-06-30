package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.LlmConfigDao
import dev.rcht.jist.data.db.entity.LlmConfigEntity

class LlmConfigRepository(private val llmConfigDao: LlmConfigDao) {
    
    suspend fun insert(config: LlmConfigEntity): Long {
        return llmConfigDao.insert(config)
    }
    
    suspend fun update(config: LlmConfigEntity) {
        llmConfigDao.update(config)
    }
    
    suspend fun getDefault(): LlmConfigEntity? {
        return llmConfigDao.getDefault()
    }

    suspend fun getDefaultConfig(): LlmConfigEntity? {
        return llmConfigDao.getDefault()
    }
    
    suspend fun getAll(): List<LlmConfigEntity> {
        return llmConfigDao.getAll()
    }
    
    suspend fun setDefault(config: LlmConfigEntity) {
        llmConfigDao.clearDefaults()
        val updated = config.copy(isDefault = true)
        llmConfigDao.update(updated)
    }
    
    suspend fun delete(config: LlmConfigEntity) {
        llmConfigDao.delete(config)
    }

    suspend fun replaceAll(configs: List<LlmConfigEntity>) {
        llmConfigDao.deleteAll()
        llmConfigDao.insertAll(configs)
    }
}
