package dev.rcht.jist.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.NotificationEntity
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
            val appInfo = NotificationParser.extractAppInfo(sbn)
            
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
                senderName = appInfo.senderName
            )
            
            // Derive conversation key with app-specific extraction
            val conversationKey = ConversationKeyExtractor.extractConversationKey(notification)
            
            // Update with proper key
            val notificationWithKey = notification.copy(conversationKey = conversationKey)
            
            // Insert into database
            val app = applicationContext as? JistApplication

            // Store original content intent (if available) for later reuse in summary notifications
            try {
                val originalPendingIntent = sbn.notification?.contentIntent
                if (originalPendingIntent != null) {
                    dev.rcht.jist.notification.PendingIntentStore.put(conversationKey, originalPendingIntent)
                    Log.d(TAG, "Stored pending intent for conversation: $conversationKey")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to store original pending intent", e)
            }

            app?.let {
                scope.launch {
                    val isDup = it.notificationRepository.isDuplicate(
                        notificationWithKey.packageName,
                        notificationWithKey.title,
                        notificationWithKey.content
                    )
                    if (isDup) {
                        Log.d(TAG, "Skipping duplicate notification: $conversationKey")
                    } else {
                        it.notificationRepository.insert(notificationWithKey)
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
