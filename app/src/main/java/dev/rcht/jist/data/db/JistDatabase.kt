package dev.rcht.jist.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.rcht.jist.data.db.dao.AppRuleDao
import dev.rcht.jist.data.db.dao.LlmConfigDao
import dev.rcht.jist.data.db.dao.NotificationDao
import dev.rcht.jist.data.db.dao.SummaryDao
import dev.rcht.jist.data.db.entity.AppRuleEntity
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.db.entity.SummaryEntity

@Database(
    entities = [
        NotificationEntity::class,
        SummaryEntity::class,
        AppRuleEntity::class,
        LlmConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JistDatabase : RoomDatabase() {
    
    abstract fun notificationDao(): NotificationDao
    abstract fun summaryDao(): SummaryDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun llmConfigDao(): LlmConfigDao
    
    companion object {
        private var instance: JistDatabase? = null
        
        fun getInstance(context: Context): JistDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    JistDatabase::class.java,
                    "jist.db"
                ).build()
            }
            return instance!!
        }
    }
}
