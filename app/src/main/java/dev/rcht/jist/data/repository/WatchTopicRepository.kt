package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.WatchTopicDao
import dev.rcht.jist.data.db.entity.WatchTopicEntity

class WatchTopicRepository(private val dao: WatchTopicDao) {

    suspend fun upsert(topic: WatchTopicEntity) = dao.upsert(topic)
    suspend fun update(topic: WatchTopicEntity) = dao.update(topic)
    suspend fun delete(topic: WatchTopicEntity) = dao.delete(topic)
    suspend fun getAll() = dao.getAll()
    suspend fun getActiveTopics() = dao.getActiveTopics()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun getByTitle(title: String) = dao.getByTitle(title)
    suspend fun count() = dao.count()
    suspend fun countActive() = dao.countActive()

    suspend fun replaceAll(topics: List<WatchTopicEntity>) {
        dao.deleteAll()
        dao.insertAll(topics)
    }
}
