package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.CustomPromptDao
import dev.rcht.jist.data.db.entity.CustomPromptEntity

class CustomPromptRepository(private val customPromptDao: CustomPromptDao) {
    
    suspend fun insert(prompt: CustomPromptEntity): Long {
        return customPromptDao.insert(prompt)
    }
    
    suspend fun update(prompt: CustomPromptEntity) {
        customPromptDao.update(prompt)
    }
    
    suspend fun delete(prompt: CustomPromptEntity) {
        customPromptDao.delete(prompt)
    }
    
    suspend fun getAll(): List<CustomPromptEntity> {
        return customPromptDao.getAll()
    }
    
    suspend fun getByApp(appName: String): CustomPromptEntity? {
        return customPromptDao.getByApp(appName)
    }
    
    suspend fun getEnabled(): List<CustomPromptEntity> {
        return customPromptDao.getEnabled()
    }
    
    suspend fun deleteByApp(appName: String) {
        customPromptDao.deleteByApp(appName)
    }

    suspend fun replaceAll(prompts: List<CustomPromptEntity>) {
        customPromptDao.deleteAll()
        customPromptDao.insertAll(prompts)
    }
}
