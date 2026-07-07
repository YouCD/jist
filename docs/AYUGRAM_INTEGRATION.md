# AyuGram ↔ Jist 集成方案

## 概述

AyuGram 在消息回调处将 Telegram 消息直接写入 Jist 的 ContentProvider，Jist 负责展示和 AI 摘要。无需 Xposed，无崩溃风险。

## 架构

```
AyuGram (org.telegram.messenger)
  │
  ├── 消息回调 (TDLib UpdateNewMessage / UpdateMessageContent)
  │     ↓
  ├── 提取: chatId, senderName, content, timestamp, msgType
  │     ↓
  └── ContentResolver.insert("content://dev.rcht.jist.provider/chat_messages", values)
                          │
                          ↓
                JistContentProvider.insertChatMessage()
                          │
                          ↓
                    Room DB → chat_messages + watched_chats + chat_sources
                          │
                          ↓
                  Jist App UI → 💬 消息 tab → 详情 → AI 摘要
```

## 通信方式

Jist 的 ContentProvider 已声明 `android:exported="true"`，任何应用均可写入。

| URI | 用途 | 自动处理 |
|-----|------|----------|
| `content://dev.rcht.jist.provider/chat_messages` | 写入消息 | 自动创建/关联 chat_source + watched_chat |
| `content://dev.rcht.jist.provider/chat_sources` | 注册应用源 | 已存在则不重复创建 |

## AyuGram 改动

### 1. 添加依赖

在 `build.gradle` 中添加：
```groovy
dependencies {
    // 无额外依赖，使用 Android SDK 自带的 ContentResolver
}
```

### 2. 消息回调处插入代码

找到 AyuGram 的消息处理入口（通常是 TDLib `UpdateNewMessage` 回调），加入以下代码：

```kotlin
package org.telegram.messenger

import android.content.ContentValues
import android.net.Uri

object JistBridge {

    private const val AUTHORITY = "dev.rcht.jist.provider"
    private const val CHAT_SOURCE_PATH = "chat_sources"
    private const val WATCHED_CHAT_PATH = "watched_chats"
    private const val CHAT_MESSAGE_PATH = "chat_messages"

    /**
     * 在收到新消息时调用。
     *
     * @param context   Application context
     * @param chatId    Telegram chat ID (long)
     * @param senderName 发送者显示名
     * @param content   消息文本
     * @param timestamp 消息时间戳（秒）
     * @param msgType   消息类型（参考 TDLib MessageContent 子类）
     * @param msgId     消息 ID
     */
    @JvmStatic
    fun onNewMessage(
        context: android.content.Context,
        chatId: Long,
        senderName: String,
        content: String,
        timestamp: Long,
        msgType: Int,
        msgId: Long
    ) {
        try {
            val cr = context.contentResolver
            val baseUri = Uri.parse("content://$AUTHORITY")

            // 1. 注册/获取聊天来源
            val sourceValues = ContentValues().apply {
                put("packageName", "org.telegram.messenger")
                put("displayName", "Telegram")
            }
            val sourceUri = cr.insert(
                Uri.withAppendedPath(baseUri, CHAT_SOURCE_PATH), sourceValues
            ) ?: return

            // 2. 注册/获取聊天会话（自动去重）
            val chatValues = ContentValues().apply {
                put("sourceId", sourceUri.lastPathSegment?.toLongOrNull() ?: return)
                put("chatId", chatId.toString())
                put("chatName", "") // 留空，Jist 自动从 rcontact 或 chatId 解析
            }
            val chatUri = cr.insert(
                Uri.withAppendedPath(baseUri, WATCHED_CHAT_PATH), chatValues
            ) ?: return

            // 3. 写入消息
            val msgValues = ContentValues().apply {
                put("watchedChatId", chatUri.lastPathSegment?.toLongOrNull() ?: return)
                put("senderName", senderName)
                put("content", content)
                put("timestamp", timestamp * 1000L) // Jist 使用毫秒
                put("msgType", msgType)
                put("msgSeq", msgId)
                put("chatAppKey", "org.telegram.messenger")
                put("chatId", chatId.toString())
            }
            cr.insert(Uri.withAppendedPath(baseUri, CHAT_MESSAGE_PATH), msgValues)

        } catch (e: Exception) {
            android.util.Log.w("JistBridge", "Failed to write message", e)
        }
    }
}
```

### 3. 调用时机

在以下 TDLib update 回调中调用 `JistBridge.onNewMessage()`：

| TDLib Update 类型 | 触发时机 |
|---|---|
| `UpdateNewMessage` | 收到新消息 |
| `UpdateMessageContent` | 消息内容被编辑 |
| `UpdateDeleteMessages` | 可选：同步删除状态 |

**示例调用位置**（在 TdlibUpdateManager 或等效类中）：

```kotlin
// 假设在 onUpdateNewMessage(update) 中
val message = update.message
JistBridge.onNewMessage(
    context = ApplicationLoader.applicationContext,
    chatId = message.chatId,
    senderName = resolveSenderName(message.senderId),
    content = extractText(message.content),
    timestamp = message.date.toLong(),
    msgType = message.content.constructor.typeConstructor,
    msgId = message.id
)
```

### 4. 辅助方法

```kotlin
/** 提取消息文本（支持 Text、Photo 带 caption 等） */
fun extractText(content: TdApi.MessageContent): String {
    return when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> content.caption?.text ?: "[图片]"
        is TdApi.MessageVideo -> content.caption?.text ?: "[视频]"
        is TdApi.MessageVoiceNote -> content.caption?.text ?: "[语音]"
        is TdApi.MessageDocument -> content.caption?.text ?: "[文件]"
        is TdApi.MessageSticker -> "[贴纸]"
        is TdApi.MessageAnimatedEmoji -> "[动态表情]"
        else -> "[消息]"
    }
}

/** 解析发送者显示名 */
fun resolveSenderName(senderId: TdApi.MessageSender): String {
    // 通过 Tdlib 的 getUser/getChat 等方法获取显示名
    // 实现取决于 AyuGram 的联系人缓存
    return when (senderId) {
        is TdApi.MessageSenderUser -> getUserName(senderId.userId)
        is TdApi.MessageSenderChat -> getChatTitle(senderId.chatId)
        else -> ""
    }
}
```

## 数据流对照

| 数据 | Xposed 方案 | AyuGram 源码方案 |
|------|-------------|------------------|
| 消息捕获 | hook insertWithOnConflict | 直接在 TDLib 回调处捕获 |
| 发送者解析 | resolveContact(rcontact) | AyuGram 自己的联系人缓存 |
| 群名解析 | rcontact.nickname | AyuGram 的 chat.title |
| 稳定性 | 受微信版本影响 | 随 AyuGram 版本同步编译 |
| 崩溃风险 | 可能因 hook 冲突闪退 | 无额外风险 |

## 多应用支持

此方案也适用于其他开源 Telegram 修改版（NekoX、exteraGram 等），只需在同位置插入 `JistBridge` 调用即可。
