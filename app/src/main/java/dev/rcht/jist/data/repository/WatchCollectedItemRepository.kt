package dev.rcht.jist.data.repository

import dev.rcht.jist.data.db.dao.WatchCollectedItemDao
import dev.rcht.jist.data.db.entity.WatchCollectedItemEntity

class WatchCollectedItemRepository(private val dao: WatchCollectedItemDao) {

    suspend fun insert(item: WatchCollectedItemEntity) = dao.insert(item)
    suspend fun getStatsForTopic(topicId: Long) = dao.getStatsForTopic(topicId)
    suspend fun getItemsForTopic(topicId: Long) = dao.getItemsForTopic(topicId)
    suspend fun getRecentItemsForTopic(topicId: Long, limit: Int = 5) = dao.getRecentItemsForTopic(topicId, limit)
    suspend fun countForTopic(topicId: Long) = dao.countForTopic(topicId)
    suspend fun deleteForTopic(topicId: Long) = dao.deleteForTopic(topicId)
    suspend fun deleteItems(ids: List<Long>) = dao.deleteByIds(ids)
    suspend fun markAllRead(topicId: Long) = dao.markAllRead(topicId)
    suspend fun countUnread(topicId: Long) = dao.countUnread(topicId)
}
