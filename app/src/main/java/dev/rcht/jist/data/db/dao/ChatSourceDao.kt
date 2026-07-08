package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.rcht.jist.data.db.entity.ChatSourceEntity

@Dao
interface ChatSourceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: ChatSourceEntity): Long

    @Query("SELECT * FROM chat_sources WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackageName(pkg: String): ChatSourceEntity?

    @Query("SELECT * FROM chat_sources WHERE isEnabled = 1")
    suspend fun getEnabled(): List<ChatSourceEntity>

    @Query("SELECT * FROM chat_sources")
    suspend fun getAll(): List<ChatSourceEntity>

    @Update
    suspend fun update(source: ChatSourceEntity)

    @Query("UPDATE chat_sources SET isEnabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)

    @Query("DELETE FROM chat_sources")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<ChatSourceEntity>)
}
