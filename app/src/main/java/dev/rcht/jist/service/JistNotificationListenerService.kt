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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JistNotificationListenerService : NotificationListenerService() {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        if (sbn == null) return
        
        // Don't process our own notifications or foreground service notifications
        if (shouldIgnore(sbn)) return
        
        try {
            val appInfo = NotificationParser.extractAppInfo(sbn, this)
            
            if (appInfo.title.isNullOrBlank() || appInfo.content.isNullOrBlank()) {
                return // Ignore notifications without text
            }
            
            // Create entity first
            val notification = NotificationEntity(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                title = appInfo.title ?: "",
                content = appInfo.content ?: "",
                conversationKey = "",  // Will be set below
                timestamp = System.currentTimeMillis(),
                isSummarized = false,
                senderName = appInfo.senderName,
                notificationTag = sbn.tag,
                notificationId = sbn.id
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
                    val newContent = notificationWithKey.content
                    // Dedup by notificationTag + notificationId (app-level notification identity)
                    val existing = it.notificationRepository.findByNotificationKey(
                        notificationWithKey.packageName,
                        notificationWithKey.notificationTag,
                        notificationWithKey.notificationId
                    )
                    if (existing != null) {
                        // Notification update: same tag+id, update content/timestamp
                        it.notificationRepository.update(existing.copy(
                            content = newContent,
                            timestamp = notificationWithKey.timestamp,
                            pendingIntentData = piData ?: existing.pendingIntentData
                        ))
                        Log.d(TAG, "Updated notification (tag+id match): $conversationKey")
                    } else {
                        it.notificationRepository.insert(notificationWithKey.copy(pendingIntentData = piData))
                        Log.d(TAG, "Notification inserted: $conversationKey")
                    }
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
