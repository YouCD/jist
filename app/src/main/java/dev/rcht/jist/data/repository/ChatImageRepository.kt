package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.ChatImageDao
import dev.rcht.jist.data.db.entity.ChatImageEntity

class ChatImageRepository(private val dao: ChatImageDao) {

    suspend fun insert(image: ChatImageEntity): Long = dao.insert(image)

    suspend fun getRecent(limit: Int = 100): List<ChatImageEntity> = dao.getRecent(limit)

    suspend fun getByChat(chatId: String): List<ChatImageEntity> = dao.getByChat(chatId)

    suspend fun findByHash(hash: String): ChatImageEntity? = dao.findByHash(hash)

    suspend fun getById(id: Long): ChatImageEntity? = dao.getById(id)

    suspend fun count(): Int = dao.count()

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
