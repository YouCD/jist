@file:Suppress("unused")

package dev.rcht.jist.xposed

import android.database.Cursor
import android.util.Log

internal fun loadChatNameCache() {
    try {
        val db = WeChatHooks.weChatDb ?: return
        val candidates = mutableListOf<String>()
        try {
            val raw = XposedHelpers.callMethod(db, "rawQuery",
                "SELECT name FROM sqlite_master WHERE type='table' AND (name LIKE '%chat%' OR name LIKE '%room%' OR name LIKE '%group%' OR name LIKE '%convers%')",
                null)
            val c = raw as? Cursor
            c?.use { while (it.moveToNext()) candidates.add(it.getString(0)) }
        } catch (_: Exception) { }
        Log.i(TAG, "Chat table candidates: $candidates")

        val priorityOrder = listOf("rcontact", "rconversation", "conversation", "BizChatInfo")
        val sortedTables = candidates.sortedWith(compareBy { tbl ->
            val idx = priorityOrder.indexOf(tbl)
            if (idx >= 0) idx else Int.MAX_VALUE
        })
        val skipTables = setOf("chatroom")

        for (table in sortedTables) {
            if (table in skipTables) {
                Log.d(TAG, "loadChatNameCache: skipping $table (displayname is member list, not group name)")
                continue
            }
            val allCols = try {
                val schemaResult = XposedHelpers.callMethod(db, "rawQuery",
                    "PRAGMA table_info($table)", null)
                val schemaC = schemaResult as? Cursor ?: continue
                val cols = mutableListOf<String>()
                schemaC.use { s -> while (s.moveToNext()) { cols.add(s.getString(s.getColumnIndexOrThrow("name"))) } }
                cols
            } catch (_: Exception) { continue }

            val idCol = allCols.firstOrNull { cn ->
                ID_COLUMN_CANDIDATES.any { it.equals(cn, ignoreCase = true) }
            } ?: continue

            val nameCol = allCols.firstOrNull { cn ->
                !cn.equals(idCol, ignoreCase = true) && nameColsIgnoreCase(cn)
            } ?: continue

            try {
                val rawResult = XposedHelpers.callMethod(db, "rawQuery",
                    "SELECT $idCol, $nameCol FROM $table WHERE $nameCol IS NOT NULL AND $nameCol != '' LIMIT 200",
                    null) ?: continue
                val cursor = rawResult as? Cursor ?: continue
                var count = 0
                cursor.use { c ->
                    val idIdx = c.getColumnIndex(idCol)
                    val cnIdx = c.getColumnIndex(nameCol)
                    if (idIdx < 0) return@use
                    while (c.moveToNext()) {
                        val id = c.getString(idIdx) ?: continue
                        if (id in WeChatHooks.chatNameCache) continue
                        if (cnIdx < 0 || c.isNull(cnIdx)) continue
                        val name = c.getString(cnIdx)
                        if (name.isNullOrBlank()) continue
                        WeChatHooks.chatNameCache[id] = name
                        count++
                    }
                }
                if (count > 0) Log.i(TAG, "ChatNameCache($table:$idCol/$nameCol): cached $count")
            } catch (t: Throwable) {
                Log.w(TAG, "ChatNameCache($table): ${t.message}")
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "loadChatNameCache: ${t.message}")
    }
}

// ── Chat name resolution helpers (WeChat-specific) ────────────────

internal val NAME_COLUMN_CANDIDATES = listOf(
    "ChatRoomName", "chatroomname", "chatRoomName", "RoomName", "roomname", "roomName",
    "chatname", "chatName", "ChatName",
    "displayname", "displayName",
    "conversationName", "conversationname",
    "nickname", "NickName", "nickName",
    "name", "Name",
    "conRemark", "con_remark",
    "groupName", "groupname", "groupNick", "groupnick", "groupNickname", "groupnickname",
)

internal val ID_COLUMN_CANDIDATES = listOf(
    "username", "wxid", "roomid", "chatroomname", "roomname",
    "id", "_id", "bizChatLocalId", "brandUserName",
)

internal fun nameColsIgnoreCase(cn: String): Boolean {
    return NAME_COLUMN_CANDIDATES.any { it.equals(cn, ignoreCase = true) }
}

internal fun resolveChatNameFromDb(chatId: String): String {
    WeChatHooks.chatNameCache[chatId]?.let { if (it.isNotBlank()) return it }
    WeChatHooks.contactCache[chatId]?.let {
        if (it.isNotBlank()) { WeChatHooks.chatNameCache[chatId] = it; return it }
    }
    val db = WeChatHooks.weChatDb ?: run {
        Log.w(TAG, "resolveChatNameFromDb: WeChatHooks.weChatDb is null, returning raw chatId")
        return chatId
    }
    try {
        Log.d(TAG, "resolveChatNameFromDb: looking up $chatId")
        val directName = queryChatNameDirect(db, chatId)
        if (directName != null) {
            WeChatHooks.chatNameCache[chatId] = directName
            Log.d(TAG, "resolveChatNameFromDb: $chatId -> $directName (direct)")
            return directName
        }
        val convName = querySingleColumn(db, chatId, "rconversation", "username", "displayname")
        if (convName != null) {
            WeChatHooks.chatNameCache[chatId] = convName
            Log.d(TAG, "resolveChatNameFromDb: $chatId -> $convName (rconversation)")
            return convName
        }
        val roomName = querySingleColumn(db, chatId, "chatroom", "chatroomname", "displayname")
        // Older WeChat versions fill chatroom.displayname with a comma/newline
        // joined member list instead of the group name (the same guard the
        // live-insert hook uses) — reject those so we don't cache junk names.
        if (roomName != null && !roomName.contains(",") && !roomName.contains("\n")) {
            WeChatHooks.chatNameCache[chatId] = roomName
            Log.d(TAG, "resolveChatNameFromDb: $chatId -> $roomName (chatroom)")
            return roomName
        }
        Log.d(TAG, "resolveChatNameFromDb: $chatId not found in any table")
    } catch (e: Exception) {
        Log.w(TAG, "resolveChatNameFromDb: ${e.message}")
    }
    return chatId
}

internal fun queryChatNameDirect(db: Any, chatId: String): String? {
    val safeId = chatId.replace("'", "''")
    for (col in listOf("nickname", "conRemark")) {
        try {
            val raw = XposedHelpers.callMethod(db, "rawQuery",
                "SELECT $col FROM rcontact WHERE username='$safeId' LIMIT 1", null) ?: continue
            val c = raw as? Cursor ?: continue
            c.use { cur ->
                if (cur.moveToFirst()) {
                    val idx = cur.getColumnIndex(col)
                    if (idx >= 0 && !cur.isNull(idx)) {
                        val v = cur.getString(idx)
                        if (!v.isNullOrBlank()) return v
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryChatNameDirect($col): ${e.message}")
        }
    }
    return null
}

internal fun querySingleColumn(db: Any, chatId: String, table: String, idCol: String, nameCol: String): String? {
    val safeId = chatId.replace("'", "''")
    return try {
        val raw = XposedHelpers.callMethod(db, "rawQuery",
            "SELECT $nameCol FROM $table WHERE $idCol='$safeId' LIMIT 1", null) ?: return null
        val c = raw as? Cursor ?: return null
        c.use { cur ->
            if (cur.moveToFirst()) {
                val idx = cur.getColumnIndex(nameCol)
                if (idx >= 0 && !cur.isNull(idx)) {
                    val v = cur.getString(idx)
                    if (!v.isNullOrBlank()) v else null
                } else null
            } else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "querySingleColumn($table.$nameCol): ${e.message}")
        null
    }
}

// ── WeChat XML message parsing ──────────────────────────────────

internal fun extractXmlTitle(xml: String): String? {
    val title = extractTag(xml, "title")
    val des = extractTag(xml, "des")
    val url = extractTag(xml, "url")
    val prefix = when {
        title != null && des != null -> "[卡片] $title - $des"
        title != null -> "[卡片] $title"
        else -> "[分享]"
    }
    return if (url != null) "$prefix\n$url" else prefix
}

internal fun extractTag(xml: String, tag: String): String? {
    val open = "<$tag>"
    val close = "</$tag>"
    val start = xml.indexOf(open)
    if (start < 0) return null
    val contentStart = start + open.length
    val end = xml.indexOf(close, contentStart)
    if (end < 0) return null
    val text = xml.substring(contentStart, end).trim()
    return text.ifBlank { null }
}
