package dev.rcht.jist.notification

import android.app.PendingIntent
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for original PendingIntents captured from notifications.
 *
 * Note: PendingIntents are not trivially serializable across process restarts,
 * so this store is intentionally in-memory. If the app process is killed the
 * mapping will be lost and the system will fall back to opening the app.
 */
object PendingIntentStore {
    private val store = ConcurrentHashMap<String, PendingIntent>()

    fun put(conversationKey: String, pendingIntent: PendingIntent) {
        store[conversationKey] = pendingIntent
        Log.d(TAG, "Stored PendingIntent for: $conversationKey")
    }

    fun get(conversationKey: String): PendingIntent? = store[conversationKey]

    fun remove(conversationKey: String) {
        store.remove(conversationKey)
        Log.d(TAG, "Removed PendingIntent for: $conversationKey")
    }

    private const val TAG = "PendingIntentStore"
}
