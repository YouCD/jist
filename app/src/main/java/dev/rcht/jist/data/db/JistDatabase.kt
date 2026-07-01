package dev.rcht.jist.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.rcht.jist.data.db.dao.AppRuleDao
import dev.rcht.jist.data.db.dao.CustomPromptDao
import dev.rcht.jist.data.db.dao.LlmConfigDao
import dev.rcht.jist.data.db.dao.NotificationDao
import dev.rcht.jist.data.db.dao.SummaryDao
import dev.rcht.jist.data.db.dao.WatchCollectedItemDao
import dev.rcht.jist.data.db.dao.WatchTopicDao
import dev.rcht.jist.data.db.entity.AppRuleEntity
import dev.rcht.jist.data.db.entity.CustomPromptEntity
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.db.entity.WatchCollectedItemEntity
import dev.rcht.jist.data.db.entity.WatchTopicEntity
import dev.rcht.jist.data.db.fts.SummaryFts

@Database(
    entities = [
        NotificationEntity::class,
        SummaryEntity::class,
        AppRuleEntity::class,
        LlmConfigEntity::class,
        SummaryFts::class,
        CustomPromptEntity::class,
        WatchTopicEntity::class,
        WatchCollectedItemEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class JistDatabase : RoomDatabase() {
    
    abstract fun notificationDao(): NotificationDao
    abstract fun summaryDao(): SummaryDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun llmConfigDao(): LlmConfigDao
    abstract fun customPromptDao(): CustomPromptDao
    abstract fun watchTopicDao(): WatchTopicDao
    abstract fun watchCollectedItemDao(): WatchCollectedItemDao
    
    companion object {
        private var instance: JistDatabase? = null
        
        fun getInstance(context: Context): JistDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    JistDatabase::class.java,
                    "jist.db"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
            }
            return instance!!
        }
    }
}
