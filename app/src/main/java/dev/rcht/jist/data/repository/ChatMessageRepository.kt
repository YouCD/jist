package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.ChatMessageDao
import dev.rcht.jist.data.db.entity.ChatMessageEntity

class ChatMessageRepository(private val dao: ChatMessageDao) {

    suspend fun insert(message: ChatMessageEntity): Long = dao.insert(message)

    suspend fun insertAll(messages: List<ChatMessageEntity>) = dao.insertAll(messages)

    suspend fun getByWatchedChat(chatId: Long): List<ChatMessageEntity> =
        dao.getByWatchedChat(chatId)

    suspend fun getRecentByChat(pkg: String, cid: String, limit: Int = 50): List<ChatMessageEntity> =
        dao.getRecentByChat(pkg, cid, limit)

    suspend fun findByMsgSeq(pkg: String, cid: String, seq: Long): ChatMessageEntity? =
        dao.findByMsgSeq(pkg, cid, seq)

    suspend fun countByWatchedChat(chatId: Long): Int = dao.countByWatchedChat(chatId)

    suspend fun deleteByIds(vararg ids: Long) = dao.deleteByIds(*ids)

    suspend fun deleteOlderThan(before: Long) = dao.deleteOlderThan(before)
}
