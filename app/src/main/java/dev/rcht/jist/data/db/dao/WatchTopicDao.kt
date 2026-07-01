package dev.rcht.jist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import dev.rcht.jist.data.db.entity.WatchTopicEntity

@Dao
interface WatchTopicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(topic: WatchTopicEntity): Long

    @Update
    suspend fun update(topic: WatchTopicEntity)

    @Delete
    suspend fun delete(topic: WatchTopicEntity)

    @Query("SELECT * FROM watch_topics ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WatchTopicEntity>

    @Query("SELECT * FROM watch_topics WHERE isEnabled = 1 ORDER BY updatedAt DESC")
    suspend fun getActiveTopics(): List<WatchTopicEntity>

    @Query("SELECT * FROM watch_topics WHERE id = :id")
    suspend fun getById(id: Long): WatchTopicEntity?

    @Query("SELECT * FROM watch_topics WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): WatchTopicEntity?

    @Query("SELECT COUNT(*) FROM watch_topics")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM watch_topics WHERE isEnabled = 1")
    suspend fun countActive(): Int

    @Query("DELETE FROM watch_topics")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(topics: List<WatchTopicEntity>)
}
