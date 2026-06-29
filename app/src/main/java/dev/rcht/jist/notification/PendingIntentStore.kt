package dev.rcht.jist.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

object PendingIntentStore {
    private val store = mutableMapOf<String, PendingIntent>()
    private val uriStore = mutableMapOf<String, String>()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("pending_intents", Context.MODE_PRIVATE)
        prefs?.all?.forEach { (k, v) ->
            if (v is String) uriStore[k] = v
        }
    }

    fun put(conversationKey: String, pendingIntent: PendingIntent) {
        store[conversationKey] = pendingIntent
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val innerIntent = pendingIntent::class.java.getMethod("getIntent").invoke(pendingIntent) as? android.content.Intent
                val dataUri = innerIntent?.dataString
                if (dataUri != null) {
                    uriStore[conversationKey] = dataUri
                    prefs?.edit()?.putString(conversationKey, dataUri)?.apply()
                    Log.d(TAG, "Stored + persisted URI: $conversationKey -> $dataUri")
                } else {
                    Log.d(TAG, "Stored PendingIntent for: $conversationKey (no URI)")
                }
            } else {
                try {
                    val field = PendingIntent::class.java.getDeclaredField("mIntent")
                    field.isAccessible = true
                    val inner = field.get(pendingIntent) as? Intent
                    val dataUri = inner?.dataString
                    if (dataUri != null) {
                        uriStore[conversationKey] = dataUri
                        prefs?.edit()?.putString(conversationKey, dataUri)?.apply()
                        Log.d(TAG, "Stored + persisted URI: $conversationKey -> $dataUri")
                    } else {
                        Log.d(TAG, "Stored PendingIntent for: $conversationKey (no URI via reflection)")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Stored PendingIntent for: $conversationKey (persist failed: ${e.message})")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Stored PendingIntent for: $conversationKey (error: ${e.message})")
        }
    }

    fun get(conversationKey: String): PendingIntent? = store[conversationKey]

    fun getIntentUri(conversationKey: String): String? {
        return uriStore[conversationKey]
    }

    fun remove(conversationKey: String) {
        store.remove(conversationKey)
        uriStore.remove(conversationKey)
        prefs?.edit()?.remove(conversationKey)?.apply()
        Log.d(TAG, "Removed PendingIntent for: $conversationKey")
    }

    private const val TAG = "PendingIntentStore"
}
