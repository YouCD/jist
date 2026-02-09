package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.AppRuleDao
import dev.rcht.jist.data.db.entity.AppRuleEntity

class AppRuleRepository(private val appRuleDao: AppRuleDao) {
    
    suspend fun insert(rule: AppRuleEntity): Long {
        return appRuleDao.insert(rule)
    }
    
    suspend fun update(rule: AppRuleEntity) {
        appRuleDao.update(rule)
    }
    
    suspend fun getForApp(packageName: String): AppRuleEntity? {
        return appRuleDao.getForApp(packageName)
    }
    
    suspend fun getEnabledApps(): List<AppRuleEntity> {
        return appRuleDao.getEnabledApps()
    }
    
    suspend fun getAll(): List<AppRuleEntity> {
        return appRuleDao.getAll()
    }
    
    suspend fun delete(rule: AppRuleEntity) {
        appRuleDao.delete(rule)
    }
}
