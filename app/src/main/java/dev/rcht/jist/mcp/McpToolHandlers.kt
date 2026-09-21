package dev.rcht.jist.mcp

import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.data.db.entity.ConversationRow
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.repository.ChatMessageRepository
import dev.rcht.jist.data.repository.ChatSourceRepository
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.WatchedChatRepository
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Implementations of the three read-only MCP tools:
 * - list_notifications
 * - list_conversations
 * - get_chat_messages
 *
 * Output convention: ISO-8601 UTC time strings; list responses carry
 * `items` plus `count` / `limit` / `offset` where applicable.
 */
class McpToolHandlers(
    private val notificationRepo: NotificationRepository,
    private val chatSourceRepo: ChatSourceRepository,
    private val watchedChatRepo: WatchedChatRepository,
    private val chatMessageRepo: ChatMessageRepository,
) {

    // ------------------------------------------------------------------
    // list_notifications
    // ------------------------------------------------------------------

    suspend fun listNotifications(args: JsonObject?): CallToolResult {
        val appFilter = resolvePackage(args.str("app"))
        val limit = args.int("limit", 50).coerceIn(1, 200)
        val offset = args.int("offset", 0).coerceAtLeast(0)

        val items: List<NotificationEntity> = if (appFilter != null) {
            notificationRepo.getByApp(appFilter).drop(offset).take(limit)
        } else {
            notificationRepo.getRecent(limit + offset, offset).take(limit)
        }

        val json = buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    items.forEach { n ->
                        add(
                            buildJsonObject {
                                put("id", n.id)
                                put("time", iso(n.timestamp))
                                put("app", n.packageName)
                                put("group", n.title)
                                put("sender", n.senderName.orEmpty())
                                put("content", n.content)
                            }
                        )
                    }
                }
            )
            put("count", items.size)
            put("limit", limit)
            put("offset", offset)
            put("retentionNote", RETENTION_NOTE_NOTIFICATIONS)
        }
        return textResult(json.toString())
    }

    // ------------------------------------------------------------------
    // list_conversations
    // ------------------------------------------------------------------

    private data class Conversation(
        val name: String,
        val app: String,
        val channel: String,
        val key: String,
        val messageCount: Int,
        val lastSeenAt: Long?,
    )

    suspend fun listConversations(args: JsonObject?): CallToolResult {
        val appFilter = resolvePackage(args.str("app"))
        val channelFilter = args.str("channel")
        val limit = args.int("limit", 100).coerceIn(1, 500)
        val offset = args.int("offset", 0).coerceAtLeast(0)

        val convs = mutableListOf<Conversation>()

        if (channelFilter != "notification") {
            // Xposed channel: only chats with AI summarization enabled
            // (same criterion as SummaryWorker), not every watched chat.
            val pkgBySourceId = chatSourceRepo.getAll().associateBy { it.id }
            for (chat in watchedChatRepo.getAll().filter { it.isSummarized }) {
                val pkg = pkgBySourceId[chat.sourceId]?.packageName.orEmpty()
                convs += Conversation(
                    name = chat.chatName,
                    app = pkg,
                    channel = "xposed",
                    key = chat.chatId,
                    messageCount = chatMessageRepo.countByWatchedChat(chat.id),
                    lastSeenAt = chatMessageRepo.maxTimestampByWatchedChat(chat.id),
                )
            }
        }

        if (channelFilter != "xposed") {
            // Notification channel: conversations seen via notifications
            for (row in notificationRepo.listConversations()) {
                convs += Conversation(
                    name = row.title,
                    app = row.packageName,
                    channel = "notification",
                    key = row.conversationKey,
                    messageCount = row.count,
                    lastSeenAt = row.lastSeen,
                )
            }
        }

        val filtered = convs
            .filter { appFilter == null || it.app == appFilter }
            .filter { channelFilter == null || it.channel == channelFilter }
            .sortedByDescending { it.lastSeenAt ?: 0L }
            .drop(offset)
            .take(limit)

        val json = buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    filtered.forEach { c ->
                        add(
                            buildJsonObject {
                                put("name", c.name)
                                put("app", c.app)
                                put("channel", c.channel)
                                put("key", c.key)
                                put("messageCount", c.messageCount)
                                c.lastSeenAt?.let { put("lastSeenAt", iso(it)) }
                            }
                        )
                    }
                }
            )
            put("count", filtered.size)
            put("limit", limit)
            put("offset", offset)
        }
        return textResult(json.toString())
    }

    // ------------------------------------------------------------------
    // get_chat_messages
    // ------------------------------------------------------------------

    private data class MessageRow(
        val sender: String,
        val content: String,
        val time: Long,
        val source: String,
    )

    suspend fun getChatMessages(args: JsonObject?): CallToolResult {
        val chatName = args.str("chatName")
        val chatId = args.str("chatId")
        val appFilter = resolvePackage(args.str("app"))
        val limit = args.int("limit", 100).coerceIn(1, 500)

        if (chatName == null && chatId == null) {
            return errorResult("缺少参数：chatName 与 chatId 至少提供一个。")
        }
        val timeFrom = args.time("timeFrom") ?: 0L
        val timeTo = args.time("timeTo") ?: System.currentTimeMillis()
        if (timeTo <= timeFrom) {
            return errorResult("时间范围无效：timeTo 必须晚于 timeFrom。")
        }

        val pkgBySourceId = chatSourceRepo.getAll().associateBy { it.id }
        val messages = mutableListOf<MessageRow>()
        var channel = ""
        var matchedByName = false

        // 1) Xposed channel first (complete history)
        val xposedCandidates = watchedChatRepo.getAll().filter { chat ->
            val nameMatch = chatName == null || chat.chatName.equals(chatName, ignoreCase = true)
            val idMatch = chatId == null || chat.chatId == chatId
            val pkgMatch = appFilter == null || pkgBySourceId[chat.sourceId]?.packageName == appFilter
            nameMatch && idMatch && pkgMatch
        }
        if (xposedCandidates.isNotEmpty()) matchedByName = true
        for (chat in xposedCandidates) {
            val pkg = pkgBySourceId[chat.sourceId]?.packageName ?: continue
            val rows = chatMessageRepo.getByChatAndTimeRange(pkg, chat.chatId, timeFrom, timeTo)
            if (rows.isNotEmpty()) {
                rows.forEach { m -> messages += messageFromXposed(m) }
                channel = "xposed"
            }
        }

        // 2) Notification channel fallback (only messages that arrived as notifications)
        if (messages.isEmpty()) {
            val convs = notificationRepo.listConversations()
            val candidates = convs.filter { row ->
                val nameMatch = chatName == null || row.title.equals(chatName, ignoreCase = true)
                val idMatch = chatId == null || row.conversationKey == chatId
                val pkgMatch = appFilter == null || row.packageName == appFilter
                nameMatch && idMatch && pkgMatch
            }
            if (candidates.isNotEmpty()) matchedByName = true
            for (row in candidates) {
                val rows = notificationRepo.getByConversationKeyAndTimeRange(
                    row.conversationKey, timeFrom, timeTo
                )
                if (rows.isNotEmpty()) {
                    rows.forEach { n ->
                        messages += MessageRow(n.senderName.orEmpty(), n.content, n.timestamp, "notification")
                    }
                    channel = "notification"
                }
            }
        }

        if (messages.isEmpty()) {
            return notFoundResult(chatName, chatId, appFilter, matchedByName)
        }

        val sorted = messages.sortedBy { it.time }.takeLast(limit)
        val json = buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    sorted.forEach { m ->
                        add(
                            buildJsonObject {
                                put("sender", m.sender)
                                put("content", m.content)
                                put("time", iso(m.time))
                                put("source", m.source)
                            }
                        )
                    }
                }
            )
            put("count", sorted.size)
            put("channel", channel)
            put("timeFrom", iso(timeFrom))
            put("timeTo", iso(timeTo))
            if (channel == "notification") {
                val oldest = notificationRepo.oldestTimestamp()
                if (oldest != null && timeFrom < oldest) {
                    put("retentionNote", "timeFrom (${iso(timeFrom)}) 早于最早可用数据 (${iso(oldest)})，更早的消息可能已被清理（通知默认保留 7 天）。")
                }
            }
        }
        return textResult(json.toString())
    }

    private fun messageFromXposed(m: ChatMessageEntity) =
        MessageRow(m.senderName, m.content, m.timestamp, "xposed")

    private suspend fun notFoundResult(
        chatName: String?,
        chatId: String?,
        appFilter: String?,
        matchedByName: Boolean,
    ): CallToolResult {
        val convs = notificationRepo.listConversations()
        val available = convs
            .filter { appFilter == null || it.packageName == appFilter }
            .take(50)
        val target = chatName ?: chatId
        val errorText = if (matchedByName) {
            "会话（$target）存在，但指定时间范围内没有消息（可能已被保留期清理，或该时段确无消息）。"
        } else {
            "未找到会话（$target）。该会话可能未通过 Xposed 监听，或名称不完全匹配。"
        }
        val json = buildJsonObject {
            put("error", errorText + " 可用会话名称见 availableConversations；也可改用 list_conversations 工具查询。")
            put(
                "availableConversations",
                buildJsonArray {
                    available.forEach { row: ConversationRow ->
                        add(
                            buildJsonObject {
                                put("name", row.title)
                                put("app", row.packageName)
                                put("key", row.conversationKey)
                            }
                        )
                    }
                }
            )
        }
        return errorResult(json.toString())
    }

    // ------------------------------------------------------------------
    // Result helpers
    // ------------------------------------------------------------------

    private fun textResult(text: String) =
        CallToolResult(content = listOf(TextContent(text)))

    private fun errorResult(text: String) =
        CallToolResult(content = listOf(TextContent(text)), isError = true)

    // ------------------------------------------------------------------
    // Argument helpers
    // ------------------------------------------------------------------

    private fun JsonObject?.str(key: String): String? = this?.get(key)?.let { el ->
        if (el is JsonPrimitive && el.isString) el.content else null
    }

    private fun JsonObject?.int(key: String, default: Int): Int =
        this?.get(key)?.let { el ->
            if (el is JsonPrimitive) el.intOrNull ?: el.longOrNull?.toInt() ?: default else default
        } ?: default

    /** Accepts ISO-8601 strings (instant / date / datetime, treated as UTC when no offset) or epoch ms. */
    private fun JsonObject?.time(key: String): Long? {
        val value = this?.get(key) ?: return null
        if (value !is JsonPrimitive) return null
        value.longOrNull?.let { return it }
        val s = value.content ?: return null
        if (s.all { it.isDigit() } && s.length >= 9) {
            return s.toLongOrNull()
        }
        try {
            return Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
        }
        try {
            return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
        try {
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
        }
        return null
    }

    private fun iso(ts: Long): String = Instant.ofEpochMilli(ts).toString()

    /** Resolve app aliases (wx/wechat/tg/telegram/...) to package names. */
    private fun resolvePackage(app: String?): String? = app?.let { raw ->
        APP_ALIASES[raw.lowercase()] ?: raw
    }

    companion object {
        private const val RETENTION_NOTE_NOTIFICATIONS =
            "通知数据默认保留 7 天（autoDeleteNotificationsAfterDays），更早的数据可能已被清理。"

        private val APP_ALIASES = linkedMapOf(
            "wx" to "com.tencent.mm",
            "wechat" to "com.tencent.mm",
            "weixin" to "com.tencent.mm",
            "tg" to "org.telegram.messenger",
            "telegram" to "org.telegram.messenger",
            "whatsapp" to "com.whatsapp",
            "gmail" to "com.google.android.gm",
        )
    }
}
