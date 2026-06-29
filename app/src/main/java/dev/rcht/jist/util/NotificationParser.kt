package dev.rcht.jist.util

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.text.TextUtils

object NotificationParser {
    
    fun extractAppInfo(sbn: StatusBarNotification, context: android.content.Context? = null): AppInfo {
        val packageName = sbn.packageName
        val notification = sbn.notification ?: return AppInfo(packageName, packageName, null, null, null)
        
        val title = extractTitle(notification)
        val content = extractContent(notification)
        val senderName = extractSenderName(notification)
        val appName = try {
            context?.packageManager?.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            )?.toString() ?: packageName
        } catch (e: Exception) {
            packageName
        }
        
        return AppInfo(packageName, appName, title, content, senderName)
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
    
    private fun extractSenderName(notification: Notification): String? {
        return try {
            notification.extras?.getString(Notification.EXTRA_SELF_DISPLAY_NAME)
                ?: notification.extras?.getString(Notification.EXTRA_SUB_TEXT)
        } catch (e: Exception) {
            null
        }
    }

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val title: String?,
        val content: String?,
        val senderName: String? = null
    )
}
