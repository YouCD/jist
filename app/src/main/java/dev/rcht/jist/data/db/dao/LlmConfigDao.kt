package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.rcht.jist.data.db.entity.LlmConfigEntity

@Dao
interface LlmConfigDao {
    
    @Insert
    suspend fun insert(config: LlmConfigEntity): Long
    
    @Update
    suspend fun update(config: LlmConfigEntity)
    
    @Query("SELECT * FROM llm_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): LlmConfigEntity?
    
    @Query("SELECT * FROM llm_configs ORDER BY name ASC")
    suspend fun getAll(): List<LlmConfigEntity>
    
    @Query("UPDATE llm_configs SET isDefault = 0")
    suspend fun clearDefaults()
    
    @Delete
    suspend fun delete(config: LlmConfigEntity)

    @Query("DELETE FROM llm_configs")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(configs: List<LlmConfigEntity>)
}
