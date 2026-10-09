package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.rcht.jist.data.db.entity.ChatImageEntity

@Dao
interface ChatImageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(image: ChatImageEntity): Long

    @Query("SELECT * FROM chat_images ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ChatImageEntity>

    @Query("SELECT * FROM chat_images WHERE chatId = :chatId ORDER BY capturedAt DESC")
    suspend fun getByChat(chatId: String): List<ChatImageEntity>

    @Query("SELECT * FROM chat_images WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): ChatImageEntity?

    @Query("SELECT * FROM chat_images WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ChatImageEntity?

    @Query("SELECT COUNT(*) FROM chat_images")
    suspend fun count(): Int

    @Query("DELETE FROM chat_images WHERE id = :id")
    suspend fun deleteById(id: Long)
}
