package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.rcht.jist.data.db.entity.ChatMessageEntity

@Dao
interface ChatMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_messages WHERE watchedChatId = :chatId ORDER BY timestamp DESC")
    suspend fun getByWatchedChat(chatId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE chatAppKey = :pkg AND chatId = :cid ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentByChat(pkg: String, cid: String, limit: Int): List<ChatMessageEntity>

    @Query(
        "SELECT * FROM chat_messages WHERE chatAppKey = :chatAppKey AND chatId = :chatId " +
            "AND timestamp >= :timeFrom AND timestamp <= :timeTo ORDER BY timestamp DESC"
    )
    suspend fun getByChatAndTimeRange(chatAppKey: String, chatId: String, timeFrom: Long, timeTo: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE chatAppKey = :pkg AND chatId = :cid AND msgSeq = :seq LIMIT 1")
    suspend fun findByMsgSeq(pkg: String, cid: String, seq: Long): ChatMessageEntity?

    @Query("SELECT COUNT(*) FROM chat_messages WHERE watchedChatId = :chatId")
    suspend fun countByWatchedChat(chatId: Long): Int

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(vararg ids: Long)

    @Query("DELETE FROM chat_messages WHERE watchedChatId = :chatId")
    suspend fun deleteByWatchedChat(chatId: Long)

    @Query("DELETE FROM chat_messages WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
