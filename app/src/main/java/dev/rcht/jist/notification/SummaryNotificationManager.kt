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
    fun postSummaryNotification(summaryId: Long, summaryText: String) {
        try {
            val title = "Notification Summary"
            val content = if (summaryText.length > 50) {
                summaryText.substring(0, 50) + "..."
            } else {
                summaryText
            }

            // Create intent to open app when tapped
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("summary_id", summaryId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                summaryId.toInt(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create the summary notification
            val notification = NotificationCompat.Builder(
                context,
                JistApplication.CHANNEL_SUMMARIES
            )
                .setSmallIcon(R.mipmap.ic_launcher) // Use app icon
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup("summaries") // Group by app
                .setGroupSummary(false)
                .build()

            // Post the notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+, check permission (should be granted by app)
                try {
                    notificationManager.notify(summaryId.toInt(), notification)
                    Log.d(TAG, "Summary notification posted: $summaryId")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Missing POST_NOTIFICATIONS permission", e)
                }
            } else {
                notificationManager.notify(summaryId.toInt(), notification)
                Log.d(TAG, "Summary notification posted: $summaryId")
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
     * Cancel a notification by ID
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Notification cancelled: $notificationId")
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
                        notificationManager.cancelNotification(conversationKey.hashCode())

                        // Post summary notification
                        notificationManager.postSummaryNotification(
                            result.summaryId,
                            result.summaryText
                        )
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
