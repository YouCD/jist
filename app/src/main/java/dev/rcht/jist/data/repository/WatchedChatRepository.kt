package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.WatchedChatDao
import dev.rcht.jist.data.db.entity.WatchedChatEntity

class WatchedChatRepository(private val dao: WatchedChatDao) {

    suspend fun insert(chat: WatchedChatEntity): Long = dao.insert(chat)

    suspend fun replaceAll(chats: List<WatchedChatEntity>) {
        dao.deleteAll()
        chats.forEach { dao.insert(it) }
    }

    suspend fun getByChatKey(sourceId: Long, chatId: String): WatchedChatEntity? =
        dao.getByChatKey(sourceId, chatId)

    suspend fun getEnabled(): List<WatchedChatEntity> = dao.getEnabled()

    suspend fun getBySource(sourceId: Long): List<WatchedChatEntity> = dao.getBySource(sourceId)

    suspend fun getAll(): List<WatchedChatEntity> = dao.getAll()

    suspend fun update(chat: WatchedChatEntity) = dao.update(chat)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun setSummarized(id: Long, summarized: Boolean) = dao.setSummarized(id, summarized)

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
