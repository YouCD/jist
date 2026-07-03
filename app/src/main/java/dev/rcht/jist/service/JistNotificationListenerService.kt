package dev.rcht.jist.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.notification.PendingIntentStore
import dev.rcht.jist.util.ConversationKeyExtractor
import dev.rcht.jist.util.NotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

class JistNotificationListenerService : NotificationListenerService() {
    
    private val notificationDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(notificationDispatcher)
    private val dedupMutex = Mutex()

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        if (sbn == null) return
        
        // Don't process our own notifications or foreground service notifications
        if (shouldIgnore(sbn)) return
        
        try {
            // Skip group summary notifications (e.g. Telegram's "X messages from Y chats")
            if (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) {
                return
            }

            val appInfo = NotificationParser.extractAppInfo(sbn, this)
            
            if (appInfo.title.isNullOrBlank() || appInfo.content.isNullOrBlank()) {
                return // Ignore notifications without text
            }
            
            // Create entity first
            val rawContent = appInfo.content ?: ""
            val contentHash = sha256(rawContent)
            val notification = NotificationEntity(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                title = appInfo.title ?: "",
                content = rawContent,
                conversationKey = "",  // Will be set below
                timestamp = System.currentTimeMillis(),
                isSummarized = false,
                senderName = appInfo.senderName,
                notificationTag = sbn.tag ?: "",
                notificationId = sbn.id,
                notificationKey = sbn.key,
                contentHash = contentHash
            )
            
            // Derive conversation key with app-specific extraction
            val conversationKey = ConversationKeyExtractor.extractConversationKey(notification)
            
            // Update with proper key
            val notificationWithKey = notification.copy(conversationKey = conversationKey)
            
            // Insert into database
            val app = applicationContext as? JistApplication

            val notificationObj = sbn.notification

            // Capture original PendingIntent for precise redirection
            val originalPendingIntent = notificationObj?.contentIntent
            if (originalPendingIntent != null) {
                PendingIntentStore.put(conversationKey, originalPendingIntent)
            }

            // Extract MessagingStyle Person URI for precise deep linking
            try {
                val messageBundles = notificationObj?.extras?.getParcelableArray("android.messages")
                if (messageBundles != null) {
                    for (msg in messageBundles) {
                        if (msg is android.os.Bundle) {
                            val person = if (android.os.Build.VERSION.SDK_INT >= 28) {
                                msg.getParcelable("sender_person", android.app.Person::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                msg.getParcelable("sender_person")
                            }
                            val uri = person?.uri
                            val key = person?.key
                            val personName = person?.name
                            Log.d(TAG, "Person data for $conversationKey: name=$personName uri=$uri key=$key")
                            if (!uri.isNullOrBlank()) {
                                PendingIntentStore.putChatUri(conversationKey, uri)
                                break
                            }
                            // Fallback: if we have a key, store it as potential deep link data
                            if (!key.isNullOrBlank()) {
                                PendingIntentStore.putChatUri(conversationKey, key)
                                Log.d(TAG, "Stored Person key: $conversationKey -> $key")
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract Person URI: ${e.message}")
            }

            // Serialize to base64 and store with notification entity
            val piData = originalPendingIntent?.let { pi ->
                try {
                    val parcel = android.os.Parcel.obtain()
                    pi.writeToParcel(parcel, 0)
                    val b64 = android.util.Base64.encodeToString(parcel.marshall(), android.util.Base64.NO_WRAP)
                    parcel.recycle()
                    Log.d(TAG, "PendingIntent serialized OK for: $conversationKey (${b64.length} chars)")
                    b64
                } catch (e: Exception) {
                    Log.w(TAG, "PendingIntent serialization FAILED for: $conversationKey - ${e.message}")
                    null
                }
            }

            app?.let {
                scope.launch {
                    // Only save notifications from monitored & enabled apps
                    val appRule = it.appRuleRepository.getForApp(notificationWithKey.packageName)
                    if (appRule == null || !appRule.enabled) {
                        Log.d(TAG, "Skipping notification from unmonitored/disabled app: ${notificationWithKey.packageName}")
                        return@launch
                    }

                    /*
                     * 原子化通知去重：由 NotificationDao.insertOrUpdate 在 @Transaction 内完成
                     *   - 首次通知 → INSERT
                     *   - 内容变了 → INSERT 历史快照（积累多条后 AI 摘要才能触发）
                     *   - 内容没变 → UPDATE 时间戳（不产生冗余行）
                     *   - 3s 内同内容 → 跳过
                     *
                     * @Transaction 内部会切换到 Dispatchers.IO 执行 SQL，
                     * 导致即使单个调度线程也无法防止并发插入。
                     * Mutex 确保同一时间只有一个协程执行去重/写入逻辑。
                     */
                    val savedId = dedupMutex.withLock {
                        it.notificationRepository.insertOrUpdate(
                            notificationWithKey.copy(pendingIntentData = piData)
                        )
                    }
                    val savedNotification = notificationWithKey.copy(
                        id = savedId, pendingIntentData = piData
                    )

                    // WatchEngine: 实时匹配关注（通知已保存，有正确的 id）
                    it.watchEngine.matchNewNotification(savedNotification)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Optional: track removed notifications if needed
    }
    
    private fun shouldIgnore(sbn: StatusBarNotification): Boolean {
        // Ignore our own notifications
        if (sbn.packageName == packageName) return true
        
        // Ignore foreground service notifications
        val flags = sbn.notification?.flags ?: 0
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return true
        
        // Ignore system notifications
        if (isSystemPackage(sbn.packageName)) return true
        
        return false
    }
    
    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.") || 
               packageName.startsWith("android.") ||
               packageName == "com.google.android.gms"
    }
    
    companion object {
        private const val TAG = "JistNotificationListener"
    }
}
