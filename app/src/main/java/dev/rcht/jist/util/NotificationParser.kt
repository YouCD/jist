package dev.rcht.jist.util

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.text.TextUtils

object NotificationParser {
    
    fun extractAppInfo(sbn: StatusBarNotification): AppInfo {
        val packageName = sbn.packageName
        val notification = sbn.notification ?: return AppInfo(packageName, packageName, null, null)
        
        val title = extractTitle(notification)
        val content = extractContent(notification)
        
        return AppInfo(packageName, packageName, title, content)
    }
    
    private fun extractTitle(notification: Notification): String? {
        return try {
            notification.extras?.getString(Notification.EXTRA_TITLE)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractContent(notification: Notification): String? {
        return try {
            notification.extras?.let { extras ->
                val text = extras.getString(Notification.EXTRA_TEXT)
                if (!TextUtils.isEmpty(text)) {
                    text
                } else {
                    val bigText = extras.getString(Notification.EXTRA_BIG_TEXT)
                    if (!TextUtils.isEmpty(bigText)) bigText else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val title: String?,
        val content: String?
    )
}
