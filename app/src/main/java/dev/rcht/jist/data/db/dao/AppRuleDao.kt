package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.rcht.jist.data.db.entity.AppRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    
    @Insert
    suspend fun insert(rule: AppRuleEntity): Long
    
    @Update
    suspend fun update(rule: AppRuleEntity)
    
    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun getForApp(packageName: String): AppRuleEntity?
    
    @Query("SELECT * FROM app_rules WHERE enabled = 1")
    suspend fun getEnabledApps(): List<AppRuleEntity>
    
    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    suspend fun getAll(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    fun getAllFlow(): Flow<List<AppRuleEntity>>
    
    @Delete
    suspend fun delete(rule: AppRuleEntity)

    @Query("DELETE FROM app_rules")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(rules: List<AppRuleEntity>)
}
