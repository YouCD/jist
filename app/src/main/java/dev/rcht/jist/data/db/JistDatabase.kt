package dev.rcht.jist.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 10,
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
        
        private fun sha256(input: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN contentHash TEXT")
                db.execSQL("UPDATE notifications SET notificationTag = '' WHERE notificationTag IS NULL")
                val cursor = db.query("SELECT id, content FROM notifications")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val content = cursor.getString(1)
                    val hash = sha256(content)
                    db.execSQL("UPDATE notifications SET contentHash = ? WHERE id = ?", arrayOf<Any?>(hash, id))
                }
                cursor.close()
                // Dedup before creating UNIQUE INDEX
                db.execSQL("DELETE FROM notifications WHERE id NOT IN (SELECT MAX(id) FROM notifications GROUP BY packageName, notificationTag, notificationId, contentHash)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notifications_packageName_notificationTag_notificationId_contentHash` ON `notifications` (`packageName`, `notificationTag`, `notificationId`, `contentHash`)")
            }
        }
        
        fun getInstance(context: Context): JistDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    JistDatabase::class.java,
                    "jist.db"
                )
                    .addMigrations(MIGRATION_9_10)
                    .allowMainThreadQueries()
                    .build()
            }
            return instance!!
        }
    }
}
