package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.rcht.jist.data.db.entity.WatchedChatEntity

@Dao
interface WatchedChatDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(chat: WatchedChatEntity): Long

    @Query("SELECT * FROM watched_chats WHERE sourceId = :sourceId AND chatId = :chatId LIMIT 1")
    suspend fun getByChatKey(sourceId: Long, chatId: String): WatchedChatEntity?

    @Query("SELECT * FROM watched_chats WHERE isEnabled = 1")
    suspend fun getEnabled(): List<WatchedChatEntity>

    @Query("SELECT * FROM watched_chats WHERE sourceId = :sourceId")
    suspend fun getBySource(sourceId: Long): List<WatchedChatEntity>

    @Query("SELECT * FROM watched_chats")
    suspend fun getAll(): List<WatchedChatEntity>

    @Update
    suspend fun update(chat: WatchedChatEntity)

    @Query("UPDATE watched_chats SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE watched_chats SET isSummarized = :summarized WHERE id = :id")
    suspend fun setSummarized(id: Long, summarized: Boolean)

    @Query("DELETE FROM watched_chats WHERE id = :id")
    suspend fun deleteById(id: Long)
}
