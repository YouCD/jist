package dev.rcht.jist.notification

import android.app.PendingIntent
import android.os.Parcel
import android.util.Log

object PendingIntentStore {
    private val store = mutableMapOf<String, PendingIntent>()
    private val chatUriStore = mutableMapOf<String, String>()

    fun put(conversationKey: String, pendingIntent: PendingIntent) {
        store[conversationKey] = pendingIntent
        Log.d(TAG, "Stored in memory: $conversationKey")
    }

    fun get(conversationKey: String): PendingIntent? = store[conversationKey]

    fun remove(conversationKey: String) {
        store.remove(conversationKey)
        chatUriStore.remove(conversationKey)
        Log.d(TAG, "Removed from memory: $conversationKey")
    }

    fun putChatUri(conversationKey: String, uri: String) {
        chatUriStore[conversationKey] = uri
        Log.d(TAG, "Stored chat URI: $conversationKey -> $uri")
    }

    fun getChatUri(conversationKey: String): String? = chatUriStore[conversationKey]

    fun fromBase64(base64: String): PendingIntent? {
        val bytes = try {
            android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode base64", e)
            return null
        }
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            PendingIntent.CREATOR.createFromParcel(parcel).also {
                Log.d(TAG, "PendingIntent deserialized OK")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize PendingIntent from parcel", e)
            null
        } finally {
            parcel.recycle()
        }
    }

    fun toBase64(pi: PendingIntent): String? {
        val parcel = Parcel.obtain()
        return try {
            pi.writeToParcel(parcel, 0)
            android.util.Base64.encodeToString(parcel.marshall(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        } finally {
            parcel.recycle()
        }
    }

    private const val TAG = "PendingIntentStore"
}
