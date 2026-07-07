package dev.rcht.jist.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.data.db.entity.WatchedChatEntity
import kotlinx.coroutines.runBlocking

class MessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? JistApplication ?: return
        val pkg = intent.getStringExtra("pkg") ?: return
        val chatId = intent.getStringExtra("chatId") ?: return
        val senderName = intent.getStringExtra("senderName") ?: ""
        val chatName = intent.getStringExtra("chatName") ?: chatId
        val content = intent.getStringExtra("content") ?: return

        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        val msgSeq = intent.getLongExtra("msgSeq", 0)
        val msgType = intent.getIntExtra("msgType", 0)

        Log.d(TAG, "onReceive: pkg=$pkg chatId=$chatId sender=$senderName content=${content.take(40)}")

        runBlocking {
            try {
                val source = app.chatSourceRepository.getOrCreate(pkg, intent.getStringExtra("appDisplayName") ?: pkg)

                val existingChat = app.watchedChatRepository.getByChatKey(source.id, chatId)
                if (existingChat != null) {
                    if (!existingChat.isEnabled) {
                        app.watchedChatRepository.setEnabled(existingChat.id, true)
                    }
                } else {
                    app.watchedChatRepository.insert(
                        WatchedChatEntity(sourceId = source.id, chatId = chatId, chatName = chatName)
                    )
                }
                val watched = app.watchedChatRepository.getByChatKey(source.id, chatId) ?: return@runBlocking

                val message = ChatMessageEntity(
                    watchedChatId = watched.id,
                    senderName = senderName,
                    content = content,
                    timestamp = timestamp,
                    msgType = msgType,
                    msgSeq = msgSeq,
                    chatAppKey = pkg,
                    chatId = chatId,
                )
                app.chatMessageRepository.insert(message)
                Log.d(TAG, "  saved msgSeq=$msgSeq")
            } catch (e: Exception) {
                Log.w(TAG, "MessageReceiver error: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "MessageReceiver"
    }
}
