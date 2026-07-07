package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.ChatSourceDao
import dev.rcht.jist.data.db.entity.ChatSourceEntity

class ChatSourceRepository(private val dao: ChatSourceDao) {

    suspend fun insert(source: ChatSourceEntity): Long = dao.insert(source)

    suspend fun getByPackageName(pkg: String): ChatSourceEntity? = dao.getByPackageName(pkg)

    suspend fun getEnabled(): List<ChatSourceEntity> = dao.getEnabled()

    suspend fun getAll(): List<ChatSourceEntity> = dao.getAll()

    suspend fun update(source: ChatSourceEntity) = dao.update(source)

    suspend fun setEnabled(pkg: String, enabled: Boolean) = dao.setEnabled(pkg, enabled)

    suspend fun getOrCreate(pkg: String, displayName: String): ChatSourceEntity {
        val existing = dao.getByPackageName(pkg)
        if (existing != null) return existing
        val id = dao.insert(ChatSourceEntity(packageName = pkg, displayName = displayName))
        return ChatSourceEntity(id = id, packageName = pkg, displayName = displayName)
    }
}
