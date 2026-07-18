package dev.rcht.jist.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.rcht.jist.data.db.dao.AppRuleDao
import dev.rcht.jist.data.db.dao.ChatMessageDao
import dev.rcht.jist.data.db.dao.ChatSourceDao
import dev.rcht.jist.data.db.dao.CustomPromptDao
import dev.rcht.jist.data.db.dao.LlmConfigDao
import dev.rcht.jist.data.db.dao.NotificationDao
import dev.rcht.jist.data.db.dao.SummaryDao
import dev.rcht.jist.data.db.dao.WatchedChatDao
import dev.rcht.jist.data.db.dao.WatchCollectedItemDao
import dev.rcht.jist.data.db.dao.WatchTopicDao
import dev.rcht.jist.data.db.entity.AppRuleEntity
import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.data.db.entity.ChatSourceEntity
import dev.rcht.jist.data.db.entity.CustomPromptEntity
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.db.entity.WatchedChatEntity
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
        WatchCollectedItemEntity::class,
        ChatSourceEntity::class,
        WatchedChatEntity::class,
        ChatMessageEntity::class
    ],
    version = 17,
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
    abstract fun chatSourceDao(): ChatSourceDao
    abstract fun watchedChatDao(): WatchedChatDao
    abstract fun chatMessageDao(): ChatMessageDao
    
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
        
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE summaries ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE summaries ADD COLUMN notificationTimeFrom INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE summaries ADD COLUMN notificationTimeTo INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_sources` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_sources_packageName` ON `chat_sources` (`packageName`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `watched_chats` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sourceId` INTEGER NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `chatName` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY (`sourceId`) REFERENCES `chat_sources`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_watched_chats_sourceId_chatId` ON `watched_chats` (`sourceId`, `chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watched_chats_sourceId` ON `watched_chats` (`sourceId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `watchedChatId` INTEGER NOT NULL,
                        `senderName` TEXT NOT NULL DEFAULT '',
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `msgType` INTEGER NOT NULL DEFAULT 0,
                        `msgSeq` INTEGER NOT NULL DEFAULT 0,
                        `chatAppKey` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `rawData` TEXT,
                        FOREIGN KEY (`watchedChatId`) REFERENCES `watched_chats`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_messages_chatAppKey_chatId_msgSeq` ON `chat_messages` (`chatAppKey`, `chatId`, `msgSeq`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_watchedChatId` ON `chat_messages` (`watchedChatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watched_chats ADD COLUMN isSummarized INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watched_chats ADD COLUMN customPrompt TEXT")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watched_chats ADD COLUMN minMessagesForSummary INTEGER NOT NULL DEFAULT 5")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watched_chats ADD COLUMN retentionDays INTEGER NOT NULL DEFAULT 7")
            }
        }

        fun getInstance(context: Context): JistDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    JistDatabase::class.java,
                    "jist.db"
                )
                    .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    .allowMainThreadQueries()
                    .build()
            }
            return instance!!
        }
    }
}
