@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.content.Context
import android.content.Intent
import android.util.Log
import java.lang.ClassLoader

internal fun setupTelegramX(loader: ClassLoader, pkg: String) {
    Log.i(TAG, "Attempting Telegram X hooks")
    try {
        val resultHandlerClass = XposedHelpers.findClass(
            "org.drinkless.td.libcore.telegram.Client\$ResultHandler", loader)
        XposedHelpers.findAndHookMethod(resultHandlerClass, "onResult",
            XposedHelpers.findClass("org.drinkless.td.libcore.telegram.TdApi\$Object", loader),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val obj = param.args[0] ?: return
                    val className = obj.javaClass.name
                    // Channel posts arrive as UpdateChannelMessage, not
                    // UpdateNewMessage — process both so channels are captured.
                    if (className.contains("UpdateNewMessage") ||
                        className.contains("UpdateChannelMessage")) {
                        handleTelegramXNewMessage(pkg, obj)
                    } else if (className.contains("UpdateMessageContent")) {
                        Log.d(TAG, "TGX UpdateMessageContent received (not processed)")
                    }
                }
            })
        Log.i(TAG, "TGX TDLib hook OK")
    } catch (e: Exception) {
        Log.w(TAG, "TGX TDLib hook failed: ${e.message}")
    }
}

private fun handleTelegramXNewMessage(pkg: String, updateObj: Any) {
    try {
        val msg = XposedHelpers.getObjectField(updateObj, "message")
        val chatId = XposedHelpers.getObjectField(msg, "chatId") as Long
        val msgId = XposedHelpers.getObjectField(msg, "id") as Long
        val date = XposedHelpers.getObjectField(msg, "date") as Int
        val isOutgoing = XposedHelpers.getObjectField(msg, "isOutgoing") as Boolean
        val content = XposedHelpers.getObjectField(msg, "content") ?: return
        val contentClass = content.javaClass.name
        val senderId = XposedHelpers.getObjectField(msg, "senderId")

        // TDLib chat ID convention: negative = group/channel
        val isGroup = chatId < 0
        val isChannel = chatId <= -1000000000000L
        val chatType = when {
            !isGroup -> "private"
            isChannel -> "channel/supergroup"
            else -> "group"
        }


        // Extract text content from various message types
        var textContent: String? = null
        try {
            textContent = when {
                contentClass.contains("MessageText") -> {
                    val text = XposedHelpers.getObjectField(content, "text")
                    XposedHelpers.callMethod(text, "toString") as? String
                }
                contentClass.contains("MessagePhoto") || contentClass.contains("MessageVideo") ||
                contentClass.contains("MessageAnimation") || contentClass.contains("MessageDocument") -> {
                    val caption = XposedHelpers.getObjectField(content, "caption")
                    if (caption != null) XposedHelpers.callMethod(caption, "toString") as? String else null
                }
                else -> null
            }
        } catch (_: Exception) { }

        // Determine media type label
        val mediaLabel = when {
            contentClass.contains("MessageText") -> null
            contentClass.contains("MessagePhoto") -> "[图片]"
            contentClass.contains("MessageVideo") -> "[视频]"
            contentClass.contains("MessageAnimation") -> "[GIF]"
            contentClass.contains("MessageDocument") -> "[文件]"
            contentClass.contains("MessageSticker") -> "[贴纸]"
            contentClass.contains("MessageAudio") -> "[音频]"
            contentClass.contains("MessageVoiceNote") -> "[语音]"
            contentClass.contains("MessageLocation") -> "[位置]"
            contentClass.contains("MessageContact") -> "[联系人]"
            contentClass.contains("MessagePoll") -> "[投票]"
            else -> "[${contentClass.substringAfterLast('$')}]"
        }

        // Resolve sender name
        val senderName = if (isOutgoing) "我" else resolveTelegramXSenderName(senderId)

        // Build content string
        val contentStr = when {
            !textContent.isNullOrBlank() && mediaLabel != null -> "$mediaLabel $textContent"
            !textContent.isNullOrBlank() -> textContent
            mediaLabel != null -> mediaLabel
            else -> "[空消息]"
        }

        val context = currentContext() ?: run {
            Log.w(TAG, "  TGX: failed to get application context")
            return
        }

        val timestamp = date * 1000L

        bgHandler.post {
            saveTelegramXMessage(context, pkg, chatId.toString(), senderName,
                chatId.toString(), timestamp, msgId, contentStr, chatType)
        }
    } catch (e: Exception) {
        Log.w(TAG, "TGX handleNewMessage error: ${e.message}")
    }
}

private fun resolveTelegramXSenderName(senderId: Any?): String {
    if (senderId == null) return "[未知发送者]"
    val senderClass = senderId.javaClass.name
    return when {
        senderClass.contains("MessageSenderUser") -> {
            try {
                val userId = XposedHelpers.getObjectField(senderId, "userId") as Long
                "用户$userId"
            } catch (_: Exception) { "[用户]" }
        }
        senderClass.contains("MessageSenderChat") -> {
            try {
                val chatId = XposedHelpers.getObjectField(senderId, "chatId") as Long
                "频道/群$chatId"
            } catch (_: Exception) { "[频道]" }
        }
        else -> "[发送者]"
    }
}

private fun saveTelegramXMessage(
    context: Context, pkg: String, chatId: String, senderName: String,
    chatName: String, timestamp: Long, msgId: Long, content: String, chatType: String
) {
    try {
        Log.d(TAG, "[Telegram] [$chatName] [$senderName] ${content.take(80)}")
        val intent = Intent("dev.rcht.jist.SAVE_MESSAGE").apply {
            setClassName("dev.rcht.jist", "dev.rcht.jist.receiver.MessageReceiver")
            putExtra("pkg", pkg)
            putExtra("chatId", chatId)
            putExtra("senderName", senderName)
            putExtra("chatName", chatName)
            putExtra("content", content)
            putExtra("timestamp", timestamp)
            putExtra("msgSeq", msgId)
            putExtra("msgType", 0)
            putExtra("appDisplayName", "Telegram X")
        }
        context.sendBroadcast(intent)
    } catch (e: Exception) {
        Log.w(TAG, "saveTelegramXMessage error: ${e.message}")
    }
}
