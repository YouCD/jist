package dev.rcht.jist.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.rcht.jist.JistApplication
import dev.rcht.jist.MainActivity
import dev.rcht.jist.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages posting and handling notification summaries
 */
class SummaryNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Post a summary notification for a generated summary
     */
    fun postSummaryNotification(
        summaryId: Long,
        summaryText: String,
        packageName: String = "Unknown",
        conversationKey: String = "",
        appName: String = "Notification",
        contactOrGroup: String = "Summary"
    ) {
        try {
            // Create a descriptive title with app name and contact/group
            val title = if (contactOrGroup != "Unknown" && contactOrGroup.isNotBlank()) {
                "$appName - $contactOrGroup"
            } else {
                "$appName Summary"
            }
            
            // Create clean preview text (first line only)
            val lines = summaryText.split("\n")
            val content = lines.firstOrNull()?.let { firstLine ->
                if (firstLine.length > 60) {
                    firstLine.substring(0, 60) + "..."
                } else {
                    firstLine
                }
            } ?: "Summary ready"

            // Try to build app-specific intent, fallback to MainActivity
            val tapIntent = (dev.rcht.jist.util.ChatIntentBuilder.buildChatIntent(
                context,
                packageName,
                conversationKey,
                contactOrGroup
            ) ?: Intent(context, MainActivity::class.java).apply {
                putExtra("summary_id", summaryId)
            }).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                summaryId.toInt(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Try to get original app icon, fallback to Jist icon
            val appIcon = if (packageName != "Unknown") {
                dev.rcht.jist.util.AppIconExtractor.getAppIcon(context, packageName)
            } else {
                null
            }

            // Create the summary notification with app branding
            val notificationBuilder = NotificationCompat.Builder(
                context,
                JistApplication.CHANNEL_SUMMARIES
            )
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup("summaries")
                .setGroupSummary(false)
                .setShowWhen(true)

            // Use app icon if available, otherwise use Jist icon
            if (appIcon != null) {
                val bitmap = dev.rcht.jist.util.DrawableUtil.drawableToBitmap(appIcon)
                notificationBuilder.setLargeIcon(bitmap)
                Log.d(TAG, "✓ Large icon set from app: $packageName")
            } else {
                Log.d(TAG, "✗ No app icon available for: $packageName")
                notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
            }

            // Always set small icon (required by Android)
            notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)

            val notification = notificationBuilder.build()

            // Post the notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+, check permission (should be granted by app)
                try {
                    notificationManager.notify(summaryId.toInt(), notification)
                    Log.d(TAG, "Summary notification posted: $summaryId ($title)")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Missing POST_NOTIFICATIONS permission", e)
                }
            } else {
                notificationManager.notify(summaryId.toInt(), notification)
                Log.d(TAG, "Summary notification posted: $summaryId ($title)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting summary notification", e)
        }
    }

    /**
     * Post a "Summarize Now" action notification for manual mode
     */
    fun postSummarizeActionNotification(
        conversationKey: String,
        appName: String,
        contactOrGroup: String,
        messageCount: Int
    ) {
        try {
            val title = "$appName: $messageCount new messages"
            val content = "from $contactOrGroup"

            // Create intent for "Summarize" action
            val summarizeIntent = Intent(context, SummarizeActionReceiver::class.java).apply {
                action = ACTION_SUMMARIZE
                putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_CONTACT_OR_GROUP, contactOrGroup)
            }

            val summarizePendingIntent = PendingIntent.getBroadcast(
                context,
                conversationKey.hashCode(),
                summarizeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create the action notification
            val notification = NotificationCompat.Builder(
                context,
                JistApplication.CHANNEL_SUMMARIZE_PROMPT
            )
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(
                    android.R.drawable.ic_menu_view,
                    "Summarize",
                    summarizePendingIntent
                )
                .setGroup("summarize_actions")
                .build()

            // Post the notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    notificationManager.notify(conversationKey.hashCode(), notification)
                    Log.d(TAG, "Summarize action notification posted: $conversationKey")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Missing POST_NOTIFICATIONS permission", e)
                }
            } else {
                notificationManager.notify(conversationKey.hashCode(), notification)
                Log.d(TAG, "Summarize action notification posted: $conversationKey")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting summarize action notification", e)
        }
    }

    /**
     * Post grouped notifications by app
     * Groups multiple summaries by packageName and shows them in one notification with InboxStyle
     */
    fun postGroupedSummaryNotifications(summaries: List<dev.rcht.jist.data.db.entity.SummaryEntity>) {
        try {
            // Group summaries by packageName
            val summariesByApp = summaries.groupBy { it.packageName }
            
            for ((packageName, appSummaries) in summariesByApp) {
                postAppGroupedNotification(packageName, appSummaries)
            }
            
            Log.d(TAG, "Posted grouped notifications for ${summariesByApp.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting grouped notifications", e)
        }
    }
    
    private fun postAppGroupedNotification(
        packageName: String,
        summaries: List<dev.rcht.jist.data.db.entity.SummaryEntity>
    ) {
        try {
            if (summaries.isEmpty()) return
            
            // Use first summary's app info
            val firstSummary = summaries[0]
            val appName = firstSummary.appName
            
            // Create title
            val title = "$appName (${summaries.size} chats)"
            
            // Get app icon
            val appIcon = if (packageName != "Unknown") {
                dev.rcht.jist.util.AppIconExtractor.getAppIcon(context, packageName)
            } else {
                null
            }
            
            // Create InboxStyle notification with all summaries
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
            
            // Convert app icon to bitmap once
            val appBitmap = appIcon?.let { dev.rcht.jist.util.DrawableUtil.drawableToBitmap(it) }
            
            // Create a child notification per conversation so each line can be clicked to open the exact chat
            summaries.forEach { summary ->
                // First line of summary as preview
                val previewText = summary.summaryText.split("\n").firstOrNull()?.take(40) ?: ""
                inboxStyle.addLine("${summary.contactOrGroup}: $previewText")
                
                try {
                    // Prefer the original PendingIntent captured by the NotificationListener
                    val childPendingIntent = dev.rcht.jist.notification.PendingIntentStore.get(summary.conversationKey)
                        ?: run {
                            val childTapIntent = dev.rcht.jist.util.ChatIntentBuilder.buildChatIntent(
                                context,
                                summary.packageName,
                                summary.conversationKey,
                                summary.contactOrGroup
                            ) ?: Intent(context, MainActivity::class.java).apply {
                                putExtra("summary_id", summary.id)
                            }
                            PendingIntent.getActivity(
                                context,
                                summary.conversationKey.hashCode(),
                                childTapIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                        }
                    
                    val childBuilder = NotificationCompat.Builder(context, JistApplication.CHANNEL_SUMMARIES)
                        .setContentTitle(summary.contactOrGroup)
                        .setContentText(previewText)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(summary.summaryText))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setGroup("summaries_$packageName")
                        .setGroupSummary(false)
                        .setShowWhen(true)
                        .setContentIntent(childPendingIntent)
                    
                    if (appBitmap != null) childBuilder.setLargeIcon(appBitmap)
                    childBuilder.setSmallIcon(R.mipmap.ic_launcher)
                    
                    val childId = summary.conversationKey.hashCode()
                    notificationManager.notify(childId, childBuilder.build())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to post child notification for ${summary.conversationKey}", e)
                }
            }
            
            // Set summary at the end
            inboxStyle.setSummaryText("${summaries.size} conversations summarized")
            
            // Create main group summary notification
            val notificationBuilder = NotificationCompat.Builder(
                context,
                JistApplication.CHANNEL_SUMMARIES
            )
                .setContentTitle(title)
                .setContentText("${summaries.size} summaries ready")
                .setStyle(inboxStyle)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup("summaries_$packageName")
                .setGroupSummary(true)
                .setShowWhen(true)
            
            // Set app icon if available
            if (appBitmap != null) {
                notificationBuilder.setLargeIcon(appBitmap)
            }
            
            // Set small icon
            notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
            
            // Create content intent - open main app launch intent for the app
            val tapIntent = dev.rcht.jist.util.ChatIntentBuilder.buildChatIntent(
                context,
                packageName,
                "",
                ""
            ) ?: Intent(context, MainActivity::class.java)
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                packageName.hashCode(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            notificationBuilder.setContentIntent(pendingIntent)
            
            val notification = notificationBuilder.build()
            
            // Post the notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    notificationManager.notify(packageName.hashCode(), notification)
                    Log.d(TAG, "✓ Grouped notification posted for $appName with ${summaries.size} summaries")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Missing POST_NOTIFICATIONS permission", e)
                }
            } else {
                notificationManager.notify(packageName.hashCode(), notification)
                Log.d(TAG, "✓ Grouped notification posted for $appName with ${summaries.size} summaries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting app grouped notification for $packageName", e)
        }
    }

    /**
     * Cancel a notification by package name
     */
    fun cancelNotificationForApp(packageName: String) {
        notificationManager.cancel(packageName.hashCode())
        Log.d(TAG, "Notification cancelled for: $packageName")
    }

    companion object {
        private const val TAG = "SummaryNotificationMgr"
        const val ACTION_SUMMARIZE = "dev.rcht.jist.SUMMARIZE"
        const val EXTRA_CONVERSATION_KEY = "conversation_key"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_CONTACT_OR_GROUP = "contact_or_group"
    }
}

/**
 * Broadcast receiver for "Summarize" action button
 */
class SummarizeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SummaryNotificationManager.ACTION_SUMMARIZE) {
            return
        }

        val conversationKey = intent.getStringExtra(
            SummaryNotificationManager.EXTRA_CONVERSATION_KEY
        ) ?: return
        val appName = intent.getStringExtra(SummaryNotificationManager.EXTRA_APP_NAME)
        val contactOrGroup = intent.getStringExtra(
            SummaryNotificationManager.EXTRA_CONTACT_OR_GROUP
        )

        Log.d(TAG, "Summarize action received for: $conversationKey")

        // Execute summarization in background using coroutines
        val app = context.applicationContext as JistApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = app.summaryEngine.summarizeConversation(conversationKey)

                val notificationManager = SummaryNotificationManager(context)

                when (result) {
                    is dev.rcht.jist.engine.SummaryResult.Success -> {
                        // Cancel the action notification
                        val systemNotificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        systemNotificationManager.cancel(conversationKey.hashCode())

                        // Get the summary to extract packageName and other info
                        val summary = app.summaryRepository.getById(result.summaryId)
                        if (summary != null) {
                            // Post grouped notification using the same mechanism
                            notificationManager.postGroupedSummaryNotifications(listOf(summary))
                        }
                        Log.d(TAG, "Summary generated successfully")
                    }

                    is dev.rcht.jist.engine.SummaryResult.Error -> {
                        Log.e(TAG, "Error generating summary: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in summarize action", e)
            }
        }
    }

    companion object {
        private const val TAG = "SummarizeActionReceiver"
    }
}
