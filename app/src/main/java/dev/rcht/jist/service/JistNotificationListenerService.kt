package dev.rcht.jist.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.NotificationEntity
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
            
            // Derive conversation key
            val conversationKey = "${appInfo.packageName}:${appInfo.title}"
            
            // Create entity
            val notification = NotificationEntity(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                title = appInfo.title ?: "",
                content = appInfo.content ?: "",
                conversationKey = conversationKey,
                timestamp = System.currentTimeMillis(),
                isSummarized = false,
                senderName = appInfo.senderName
            )
            
            // Insert into database
            val app = applicationContext as? JistApplication
            app?.let {
                scope.launch {
                    it.notificationRepository.insert(notification)
                    Log.d(TAG, "Notification inserted: $conversationKey")
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
