@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.content.ContentValues
import android.util.Log
import java.lang.ClassLoader

internal fun extractSenderIdFromData(loader: ClassLoader, cv: ContentValues): Long? {
    try {
        val data = cv.getAsByteArray("data") ?: return null
        val serializedDataClass = XposedHelpers.findClass(
            "org.telegram.tgnet.SerializedData", loader)
        val serializedData = serializedDataClass.getConstructor(ByteArray::class.java).newInstance(data)
        val constructor = XposedHelpers.callMethod(serializedData, "readInt32", true) as Int
        val tlrpcMessageClass = XposedHelpers.findClass(
            "org.telegram.tgnet.TLRPC\$Message", loader)
        val msg = XposedHelpers.callStaticMethod(tlrpcMessageClass, "TLdeserialize",
            serializedData, constructor, true) ?: return null
        // Try long first (newer versions), then int (older versions), then Peer object
        val fromId = try {
            XposedHelpers.getObjectField(msg, "from_id") as Long
        } catch (_: Exception) {
            try {
                (XposedHelpers.getObjectField(msg, "from_id") as Int).toLong()
            } catch (_: Exception) {
                try {
                    val peer = XposedHelpers.getObjectField(msg, "from_id")
                    XposedHelpers.getObjectField(peer, "user_id") as Long
                } catch (_: Exception) { 0L }
            }
        }
        return if (fromId != 0L) fromId else null
    } catch (_: Throwable) {
        return null
    }
}

internal fun extractMessageTextFromData(loader: ClassLoader, cv: ContentValues): String? {
    try {
        val data = cv.getAsByteArray("data") ?: return null
        val serializedDataClass = XposedHelpers.findClass(
            "org.telegram.tgnet.SerializedData", loader)
        val serializedData = serializedDataClass.getConstructor(ByteArray::class.java).newInstance(data)
        val constructor = XposedHelpers.callMethod(serializedData, "readInt32", true) as Int
        val tlrpcMessageClass = XposedHelpers.findClass(
            "org.telegram.tgnet.TLRPC\$Message", loader)
        val msg = XposedHelpers.callStaticMethod(tlrpcMessageClass, "TLdeserialize",
            serializedData, constructor, true) ?: return null
        // Extract message text (message field always exists; caption may not)
        val message = try {
            XposedHelpers.getObjectField(msg, "message") as? String
        } catch (_: Throwable) { null }
        val caption = try {
            XposedHelpers.getObjectField(msg, "caption") as? String
        } catch (_: Throwable) { null }
        val text = when {
            !message.isNullOrBlank() && !caption.isNullOrBlank() -> "$message\n$caption"
            !message.isNullOrBlank() -> message
            !caption.isNullOrBlank() -> caption
            else -> null
        }
        return text
    } catch (_: Throwable) {
        return null
    }
}

internal fun deserializeChatTitle(classLoader: ClassLoader, blob: ByteArray): String? {
    try {
        val serializedDataClass = XposedHelpers.findClass(
            "org.telegram.tgnet.SerializedData", classLoader)
        // Try TLRPC.Chat first, then TLRPC.ChatFull. A FRESH SerializedData is
        // needed for each attempt: TLdeserialize consumes the byte stream, so
        // reusing one instance would leave the second attempt mid-stream.
        for (className in listOf("org.telegram.tgnet.TLRPC\$Chat", "org.telegram.tgnet.TLRPC\$ChatFull")) {
            try {
                val serializedData = serializedDataClass.getConstructor(ByteArray::class.java).newInstance(blob)
                val constructor = XposedHelpers.callMethod(serializedData, "readInt32", true) as Int
                val chatClass = XposedHelpers.findClass(className, classLoader)
                val chat = XposedHelpers.callStaticMethod(chatClass, "TLdeserialize",
                    serializedData, constructor, true) ?: continue
                val title = XposedHelpers.getObjectField(chat, "title") as? String
                if (!title.isNullOrBlank()) return title.split(";;;").first().trim()
            } catch (_: Throwable) { }
        }
        return null
    } catch (e: Throwable) {
        Log.d(TAG, "deserializeChatTitle: ${e.message}")
        return null
    }
}
