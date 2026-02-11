package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.rcht.jist.data.db.entity.CustomPromptEntity

@Dao
interface CustomPromptDao {
    
    @Insert
    suspend fun insert(prompt: CustomPromptEntity): Long
    
    @Update
    suspend fun update(prompt: CustomPromptEntity)
    
    @Delete
    suspend fun delete(prompt: CustomPromptEntity)
    
    @Query("SELECT * FROM custom_prompts ORDER BY appName ASC")
    suspend fun getAll(): List<CustomPromptEntity>
    
    @Query("SELECT * FROM custom_prompts WHERE appName = :appName")
    suspend fun getByApp(appName: String): CustomPromptEntity?
    
    @Query("SELECT * FROM custom_prompts WHERE enabled = 1 ORDER BY appName ASC")
    suspend fun getEnabled(): List<CustomPromptEntity>
    
    @Query("DELETE FROM custom_prompts WHERE appName = :appName")
    suspend fun deleteByApp(appName: String)
}
