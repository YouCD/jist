package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.AppRuleDao
import dev.rcht.jist.data.db.entity.AppRuleEntity
import kotlinx.coroutines.flow.Flow

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

    suspend fun getByPackageName(packageName: String): AppRuleEntity? {
        return appRuleDao.getForApp(packageName)
    }
    
    suspend fun getEnabledApps(): List<AppRuleEntity> {
        return appRuleDao.getEnabledApps()
    }
    
    suspend fun getAll(): List<AppRuleEntity> {
        return appRuleDao.getAll()
    }

    fun getAllFlow(): Flow<List<AppRuleEntity>> {
        return appRuleDao.getAllFlow()
    }
    
    suspend fun delete(rule: AppRuleEntity) {
        appRuleDao.delete(rule)
    }

    suspend fun replaceAll(rules: List<AppRuleEntity>) {
        appRuleDao.deleteAll()
        appRuleDao.insertAll(rules)
    }
}
