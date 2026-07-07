package dev.rcht.jist.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.data.db.entity.ChatSourceEntity
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.db.entity.WatchedChatEntity
import dev.rcht.jist.util.ConversationKeyExtractor
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

class JistContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        Log.d(TAG, "JistContentProvider created")
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (values == null) return null
        val app = context?.applicationContext as? JistApplication ?: return null

        return when (uri.path) {
            "/$NOTIFICATION_PATH" -> insertNotification(app, values, uri)
            "/$CHAT_SOURCE_PATH" -> insertChatSource(app, values, uri)
            "/$WATCHED_CHAT_PATH" -> insertWatchedChat(app, values, uri)
            "/$CHAT_MESSAGE_PATH" -> insertChatMessage(app, values, uri)
            else -> {
                Log.w(TAG, "Unknown insert path: ${uri.path}")
                null
            }
        }
    }

    private fun insertNotification(app: JistApplication, values: ContentValues, uri: Uri): Uri? {
        val pkg = values.getAsString("packageName") ?: return null
        val title = values.getAsString("title") ?: ""
        val content = values.getAsString("content") ?: ""

        val notification = NotificationEntity(
            packageName = pkg,
            appName = values.getAsString("appName") ?: pkg,
            title = title,
            content = content,
            conversationKey = "",
            timestamp = values.getAsLong("timestamp") ?: System.currentTimeMillis(),
            isSummarized = false,
            senderName = values.getAsString("senderName"),
            notificationTag = values.getAsString("notificationTag") ?: "",
            notificationId = values.getAsInteger("notificationId") ?: 0,
            notificationKey = values.getAsString("notificationKey"),
            contentHash = sha256(content)
        )

        val conversationKey = ConversationKeyExtractor.extractConversationKey(notification)
        val id = runBlocking {
            try {
                app.notificationRepository.insertOrUpdate(notification.copy(conversationKey = conversationKey))
            } catch (e: Exception) {
                Log.e(TAG, "insertOrUpdate failed", e)
                -1L
            }
        }
        Log.d(TAG, "Inserted notification id=$id for $pkg:$title")
        return Uri.withAppendedPath(uri, id.toString())
    }

    private fun insertChatSource(app: JistApplication, values: ContentValues, uri: Uri): Uri? {
        val pkg = values.getAsString("packageName") ?: return null
        val id = runBlocking {
            app.chatSourceRepository.getOrCreate(pkg, values.getAsString("displayName") ?: pkg).id
        }
        return Uri.withAppendedPath(uri, id.toString())
    }

    private fun insertWatchedChat(app: JistApplication, values: ContentValues, uri: Uri): Uri? {
        val sourceId = values.getAsLong("sourceId") ?: return null
        val chatId = values.getAsString("chatId") ?: return null
        val chatName = values.getAsString("chatName") ?: chatId

        val existing = runBlocking { app.watchedChatRepository.getByChatKey(sourceId, chatId) }
        if (existing != null) {
            runBlocking { app.watchedChatRepository.setEnabled(existing.id, true) }
            return Uri.withAppendedPath(uri, existing.id.toString())
        }

        val id = runBlocking {
            app.watchedChatRepository.insert(
                WatchedChatEntity(sourceId = sourceId, chatId = chatId, chatName = chatName)
            )
        }
        return Uri.withAppendedPath(uri, id.toString())
    }

    private fun insertChatMessage(app: JistApplication, values: ContentValues, uri: Uri): Uri? {
        val watchedChatId = values.getAsLong("watchedChatId") ?: return null
        val content = values.getAsString("content") ?: return null

        val message = ChatMessageEntity(
            watchedChatId = watchedChatId,
            senderName = values.getAsString("senderName") ?: "",
            content = content,
            timestamp = values.getAsLong("timestamp") ?: System.currentTimeMillis(),
            msgType = values.getAsInteger("msgType") ?: 0,
            msgSeq = values.getAsLong("msgSeq") ?: 0,
            chatAppKey = values.getAsString("chatAppKey") ?: "",
            chatId = values.getAsString("chatId") ?: "",
            rawData = values.getAsString("rawData")
        )

        val id = runBlocking {
            try {
                app.chatMessageRepository.insert(message)
            } catch (e: Exception) {
                Log.w(TAG, "chat message insert failed (dup?): ${e.message}")
                -1L
            }
        }
        return if (id > 0) Uri.withAppendedPath(uri, id.toString()) else null
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val app = context?.applicationContext as? JistApplication ?: return null

        return when (uri.path) {
            "/$WATCHED_CHAT_PATH/query_by_chat" -> queryWatchedChatByKey(app, uri)
            "/$CHAT_SOURCE_PATH" -> queryChatSources(app)
            "/$WATCHED_CHAT_PATH" -> queryWatchedChats(app, uri)
            else -> null
        }
    }

    private fun queryChatSources(app: JistApplication): Cursor? {
        val sources = runBlocking { app.chatSourceRepository.getEnabled() }
        val cursor = MatrixCursor(arrayOf("id", "packageName", "displayName", "isEnabled"))
        for (s in sources) {
            cursor.addRow(arrayOf(s.id, s.packageName, s.displayName, if (s.isEnabled) 1 else 0))
        }
        return cursor
    }

    private fun queryWatchedChatByKey(app: JistApplication, uri: Uri): Cursor? {
        val sourceId = uri.getQueryParameter("sourceId")?.toLongOrNull() ?: return null
        val chatId = uri.getQueryParameter("chatId") ?: return null
        val chat = runBlocking { app.watchedChatRepository.getByChatKey(sourceId, chatId) }
            ?: return null
        val cursor = MatrixCursor(arrayOf("id", "sourceId", "chatId", "chatName", "isEnabled"))
        cursor.addRow(arrayOf(chat.id, chat.sourceId, chat.chatId, chat.chatName, if (chat.isEnabled) 1 else 0))
        return cursor
    }

    private fun queryWatchedChats(app: JistApplication, uri: Uri): Cursor? {
        val sourceId = uri.getQueryParameter("sourceId")?.toLongOrNull()
        val chats = runBlocking {
            if (sourceId != null) app.watchedChatRepository.getBySource(sourceId)
            else app.watchedChatRepository.getEnabled()
        }
        val cursor = MatrixCursor(arrayOf("id", "sourceId", "chatId", "chatName", "isEnabled"))
        for (c in chats) {
            cursor.addRow(arrayOf(c.id, c.sourceId, c.chatId, c.chatName, if (c.isEnabled) 1 else 0))
        }
        return cursor
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        val app = context?.applicationContext as? JistApplication ?: return 0
        return when (uri.path) {
            "/$WATCHED_CHAT_PATH/toggle" -> {
                val id = uri.getQueryParameter("id")?.toLongOrNull() ?: return 0
                val enabled = uri.getQueryParameter("enabled")?.toBooleanStrictOrNull() ?: return 0
                runBlocking { app.watchedChatRepository.setEnabled(id, enabled) }
                1
            }
            else -> 0
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun getType(uri: Uri): String? = null

    companion object {
        private const val TAG = "JistContentProvider"
        const val AUTHORITY = "dev.rcht.jist.provider"
        const val NOTIFICATION_PATH = "notifications"
        const val CHAT_SOURCE_PATH = "chat_sources"
        const val WATCHED_CHAT_PATH = "watched_chats"
        const val CHAT_MESSAGE_PATH = "chat_messages"

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
